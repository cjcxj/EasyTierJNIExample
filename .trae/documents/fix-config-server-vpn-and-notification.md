# 修复远程配置 VPN 组网 & 服务关闭通知残留

## Context

用户反馈两个问题：

1. **服务关闭后通知不会消失**：当配置服务器推送 `delete_network_instance` 事件删除远程实例时，[MainViewModel.kt#L171-L181](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt#L171-L181) 只清理了状态变量，**没有停止 VpnService 也没有取消通知**。而 `stopConfigServerInstance()` (L480) 已有完整停止逻辑，但事件处理未复用。

2. **远程配置只连接服务器未真正组网**：[MainViewModel.kt#L161-L169](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt#L161-L169) 收到 `run_network_instance` 成功事件后，只设置实例名和创建 DataPlaneClient，**完全没有启动 VpnService**。EasyTier 内部虽然在跑，但没有 TUN fd，无法组网。对比本地启动路径 [EasyTierManager.kt#L63-L91](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/EasyTierManager.kt#L63-L91) 会通过监控协程检测 IPv4 变化并调用 `restartVpnService` 建立 TUN 接口。

目标：让远程配置下发的实例能正确建立 VPN 接口完成组网；并修复 delete 事件不清理 VPN/通知的问题。

## 修复方案

### 修复 1：delete_network_instance 事件复用停止逻辑

**文件**：[MainViewModel.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt)

将 L171-L181 的 `delete_network_instance` 分支改为直接调用 `stopConfigServerInstance()`。该方法已有幂等保护（L482 `if (_configServerInstanceName.value == null) return`），可安全复用，会完成：停止实例、停止 VpnService、取消通知、清理状态。保留 toast 提示。

### 修复 2：新建 ConfigServerVpnController 启动 VPN

**新文件**：`app/src/main/java/com/easytier/jni/ConfigServerVpnController.kt`

参考 [EasyTierManager](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/EasyTierManager.kt) 的监控+重启 VPN 模式，但做以下简化：
- 用 `Context` 而非 `Activity`（远程配置事件来自 FFI 回调，不一定有 Activity）
- 不绑定本地配置参数（DNS/IPv6 留空，走 [EasyTierVpnService.getSystemDnsServers()](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/EasyTierVpnService.kt#L157-L167) 系统回退，IPv6 不配置）
- 复用 `EasyTierJNI.collectNetworkInfos` + `NetworkInstanceRunningInfoMap` 解析，按 instanceName 过滤
- 复用 [parseIpv4InetToString](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/EasyTierManager.kt#L19-L34)（已是顶层函数）

**核心 API**：
```kotlin
class ConfigServerVpnController(
    private val context: Context,
    private val instanceName: String,
    private val onVpnAuthRequired: () -> Unit  // 权限不足时回调，由 VM 缓存待启动状态
) {
    fun start()  // 启动监控协程，MONITOR_INTERVAL=3000ms
    fun stop()   // 取消监控 + stopService(VpnService)
}
```

**监控逻辑**（参考 EasyTierManager.kt L122-187）：
1. `collectNetworkInfos(10)` → 解析 → 按 instanceName 过滤
2. 取 `my_node_info.virtual_ipv4`，收集所有 `routes[].proxy_cidrs`
3. IPv4 或 proxy_cidrs 变化时：
   - 预检权限：`VpnService.prepare(context)`
     - 非 null（需授权）→ 调用 `onVpnAuthRequired()`，不启动 VPN
     - null（已授权）→ 启动 VpnService
4. 启动 VpnService：用 `ContextCompat.startForegroundService(context, intent)`（应对后台启动限制），Intent 仅带 `EXTRA_IPV4_ADDRESS`、`EXTRA_PROXY_CIDRS`、`EXTRA_INSTANCE_NAME`（不带 DNS/IPv6）
5. 重启时先 `stopService` 再启动（同 EasyTierManager.restartVpnService 模式）
6. `stop()` 中 `stopService` + 不再调用 `EasyTierJNI.stopAllInstances()`（实例停止由 MainViewModel 负责，控制器只管 VPN 接口）

### 修复 3：MainViewModel 集成 controller + 待授权状态

**文件**：[MainViewModel.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt)

新增字段：
- `private var configServerVpnController: ConfigServerVpnController? = null`
- `private val _pendingVpnForConfigServer = mutableStateOf(false)`
- `val pendingVpnForConfigServer: State<Boolean>` 暴露给 UI

修改点：
1. **`run_network_instance` 事件**（L161-L169）：成功且 `easyTierManager == null` 时，创建 controller 并 `start()`。若本地实例正在运行则不创建（由本地 manager 管 VPN）
2. **`delete_network_instance` 事件**：改为调用 `stopConfigServerInstance()`（含修复 1）
3. **`stopConfigServerInstance()`**（L480）：增加 `configServerVpnController?.stop(); configServerVpnController = null`；清除 `_pendingVpnForConfigServer`
4. **`stopEasyTier()`**（L454）：同样停止 controller，避免本地+远程并存时残留
5. **新增 `startVpnForConfigServerInstance(activity)`**：权限授予后调用，重新触发 controller 的 VPN 启动（或直接调用 controller 的 `retryStartVpn()` 方法）
6. **新增 `checkPendingVpnForConfigServer(activity)`**：供 MainActivity.onResume 调用，若 `_pendingVpnForConfigServer` 为 true，触发权限请求流程

### 修复 4：MainActivity 区分本地/远程权限流程

**文件**：[MainActivity.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/MainActivity.kt)

1. 新增 `private var pendingVpnAction: VpnAction = VpnAction.NONE`（枚举：NONE / LOCAL_START / CONFIG_SERVER_VPN）
2. 修改 `requestVpnPermission()`（L204）：设置 `pendingVpnAction = VpnAction.LOCAL_START` 后请求
3. 新增 `requestVpnPermissionForConfigServer()`：设置 `pendingVpnAction = VpnAction.CONFIG_SERVER_VPN` 后请求
4. 修改 `vpnPermissionLauncher` 回调（L62-70）：根据 `pendingVpnAction` 分发
   - `LOCAL_START` → `viewModel.startEasyTier(this)`
   - `CONFIG_SERVER_VPN` → `viewModel.startVpnForConfigServerInstance(this)`
   - 完成后重置为 NONE
5. 新增 `onResume()`：调用 `viewModel.checkPendingVpnForConfigServer(this)`，若 VM 标记待授权则触发 `requestVpnPermissionForConfigServer()`

### ConfigServerVpnController 与 MainViewModel 的回调衔接

controller 的 `onVpnAuthRequired` 回调实现：
```kotlin
onVpnAuthRequired = {
    _pendingVpnForConfigServer.value = true
}
```
权限授予后 `startVpnForConfigServerInstance` 清除该标志并调用 controller 的重试方法。

## 不需要改动的部分

- **EasyTierVpnService.kt**：现有 `onStartCommand` 已兼容 DNS/IPv6 缺省场景（L56/L102 检查 isNullOrBlank），无需改动
- **ConfigServerTab.kt**：UI 无需改动（DNS/IPv6 留空走系统回退，不暴露配置项）
- **EasyTierManager.kt**：本地启动路径不变，可作为 controller 的参考模板

## 验证步骤

> 项目缺少 gradle-wrapper.jar，需在 Android Studio 中 Build > Make Project 验证编译，然后在真机测试。

1. **编译验证**：Build > Make Project，确认无编译错误
2. **远程配置组网验证**（问题 2）：
   - 配置服务器 Tab 连接到 `https://config.easytier.cn`
   - 服务器下发实例后，观察日志出现 "VPN 接口创建成功" 和 "TUN 文件描述符设置成功"
   - 在状态 Tab 确认虚拟 IPv4 已获取，peer 列表能正确显示组网对端
   - 若首次未授权 VPN：App 切回前台时自动弹出权限请求，授权后 VPN 自动建立
3. **delete 事件通知清理验证**（问题 1）：
   - 远程实例运行中（VPN 已建立，通知显示）
   - 服务器推送 delete 事件，确认通知立即消失，状态清空
   - 也可手动点击 UI 停止按钮，确认通知消失
4. **本地+远程并存场景**：本地启动实例后，远程配置再下发实例，确认本地 manager 继续管 VPN，不冲突
