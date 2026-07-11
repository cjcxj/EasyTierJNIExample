# 通知行为修复 + 配置中心增强 + 控制页蒙版 + 日志翻译

## Context

用户报告了一系列问题：
1. 从通知栏进入应用会让服务关闭但 VPN 图标不消失；通知停止按钮按下后 UI 仍显示运行中
2. 配置中心页的服务器 URL/主机名/安全模式未持久化；缺少"启动应用时连接服务器"功能；事件日志显示不够友好
3. 连接到配置服务器时控制页缺少蒙版提示；配置服务器启动的实例无法通过控制页停止按钮停止
4. 日志页部分英文事件未翻译

根因分析：
- `MainActivity.onDestroy()` 无条件调用 `stopEasyTier()`，Activity 被系统回收重建时服务被错误停止
- 通知停止按钮直接走 `EasyTierVpnService.stopVpn()`，不经过 ViewModel，导致 UI 状态不同步
- ConfigServerTab 用 `rememberSaveable` 存储配置，进程重启后丢失
- 上游 `GlobalCtxEvent` 枚举有 16 个事件未在 Android 端翻译

## 实现步骤

### 第1步：修复通知进入导致服务关闭

**文件**：[MainActivity.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/MainActivity.kt)、[AndroidManifest.xml](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/AndroidManifest.xml)

1. AndroidManifest.xml 中 MainActivity 添加 `android:launchMode="singleTask"`
2. MainActivity.onDestroy() 中检查 `isFinishing`，仅当为 true（用户主动关闭）时才调用 `viewModel.stopEasyTier()`
3. MainActivity 添加 `onNewIntent` 处理 STOP action（通知停止按钮触发）

### 第2步：通知停止按钮走 Activity

**文件**：[EasyTierVpnService.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/EasyTierVpnService.kt)、[MainActivity.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/MainActivity.kt)

1. EasyTierVpnService.buildNotification() 中停止按钮的 PendingIntent 从 `getService` 改为 `getActivity`，target 为 MainActivity，携带 action `com.easytier.app.action.STOP_VPN`
2. MainActivity.companion 中定义 `ACTION_STOP_VPN` 常量
3. MainActivity.onNewIntent 中检测 ACTION_STOP_VPN，调用 `viewModel.stopEasyTier()`
4. 保留 EasyTierVpnService 中 ACTION_STOP 的处理逻辑作为兜底（但不被通知按钮直接使用）
5. MainViewModel.stopEasyTier() 增加健壮性：即使 easyTierManager 为 null（配置服务器启动的实例），也调用 `EasyTierJNI.stopAllInstances()` 停止所有实例

### 第3步：配置中心持久化 URL/主机名/安全模式

**文件**：[SettingsRepository.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/SettingsRepository.kt)、[MainViewModel.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt)、[ConfigServerTab.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/ConfigServerTab.kt)

1. SettingsRepository 新增 3 个 PreferencesKey：`configServerHostname`、`configServerSecureMode`（Boolean）、`autoConnectConfigServer`（Boolean），以及对应的 get/set 方法
2. MainViewModel 新增 `_configServerSettings` StateFlow（包含 url、hostname、secureMode、autoConnect），init 中从 SettingsRepository 加载
3. MainViewModel 新增 `saveConfigServerSettings(...)` 方法
4. MainViewModel 新增 `stopConfigServerInstance()` 方法：停止配置服务器启动的实例（调用 `EasyTierJNI.stopAllInstances()`，清零 `_configServerInstanceName` 和相关状态）
5. MainViewModel 暴露 `isConfigServerControlled` 状态：`_configServerInstanceName.value != null && easyTierManager == null`
6. ConfigServerTab 接收 `initialUrl/initialHostname/initialSecureMode/autoConnect` 参数和 `onSettingsSaved` 回调
7. ConfigServerTab 用 `remember(initialUrl) { mutableStateOf(initialUrl) }` 初始化（避免 recomposition 重置）
8. 点击"连接"时调用 `onSettingsSaved(url, hostname, secureMode, autoConnect)` 持久化
9. MainScreen 将 ViewModel 的配置服务器设置传递给 ConfigServerTab

### 第4步：实现"启动应用时自动连接"

**文件**：[ConfigServerTab.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/ConfigServerTab.kt)、[MainViewModel.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt)

1. ConfigServerTab 在连接按钮旁添加一个复选框"启动应用时自动连接"，绑定 `autoConnect` 状态
2. 复选框状态变化时立即持久化（通过 onSettingsSaved 回调）
3. MainViewModel.init 中：加载配置服务器设置后，若 `autoConnect == true` 且 `url` 非空，自动调用 `configServerManager.start(url, hostname, machineId, secureMode)`

### 第5步：事件日志格式化增强

**文件**：[ConfigServerTab.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/ConfigServerTab.kt)

1. 增强 `formatEventJson`：解析 instance_id、network_name、error 等更多字段，在摘要中展示
2. EventItem 根据事件成功/失败状态使用不同颜色（成功=绿色，失败=红色，连接状态变更=蓝色）
3. 连接状态变更事件（CONNECTING/CONNECTED/DISCONNECTED）也添加到事件列表时区分颜色

### 第6步：控制页蒙版

**文件**：[ControlTab.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/ControlTab.kt)、[MainScreen.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainScreen.kt)、[MainViewModel.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt)、[MainActivity.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/MainActivity.kt)

1. MainViewModel 暴露 `isConfigServerControlled` State
2. MainScreen 接收 `isConfigServerControlled` 和 `onStopConfigServerInstance` 回调，传递给 ControlTab
3. ControlTab 新增 `isConfigServerControlled` 和 `onStopConfigServerInstance` 参数
4. ControlTab 的最外层 Column 用 Box 包裹，在 `isConfigServerControlled == true` 时叠加半透明蒙版：
   - 蒙版 Modifier：`Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))`，**不添加 clickable**，事件穿透到下层
   - 蒙版上居中显示提示卡片："当前由配置服务器控制，配置不可编辑。点击停止按钮可停止远程实例。"
   - Hero 卡片的停止按钮在蒙版之上（z-index 更高），保持可点击
5. 当 `isConfigServerControlled == true` 时，停止按钮的 onClick 改为调用 `onStopConfigServerInstance`
6. MainActivity 中将 `viewModel.isConfigServerControlled` 和 `viewModel::stopConfigServerInstance` 传入 MainScreen

### 第7步：日志页翻译增强

**文件**：[NetworkInfoParser.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/NetworkInfoParser.kt)

在 `parseSingleRawEvent` 的 when 表达式中添加以下事件的翻译（基于上游 `GlobalCtxEvent` 枚举）：

| 事件类型 | 翻译 | 级别 |
|---------|------|------|
| `TunDeviceError` | TUN 设备错误: {msg} | ERROR |
| `ListenerAddFailed` | 监听器添加失败: {url} ({error}) | ERROR |
| `ListenerAcceptFailed` | 监听器接受连接失败: {url} ({error}) | ERROR |
| `ListenerPortMappingEstablished` | 端口映射已建立: {local} -> {mapped} ({backend}) | SUCCESS |
| `ConnectError` | 连接错误: {dst} ({ip version}): {error} | ERROR |
| `VpnPortalStarted` | VPN 门户已启动: {portal} | SUCCESS |
| `VpnPortalClientConnected` | VPN 门户客户端已连接: {client_ip} | INFO |
| `VpnPortalClientDisconnected` | VPN 门户客户端已断开: {client_ip} | INFO |
| `DhcpIpv4Conflicted` | DHCP IP 冲突: {ip} | WARNING |
| `PublicIpv6Changed` | 公网 IPv6 变更: {old} -> {new} | INFO |
| `PublicIpv6RoutesUpdated` | 公网 IPv6 路由更新: +{added} -{removed} | INFO |
| `PortForwardAdded` | 端口转发已添加 | INFO |
| `ConfigPatched` | 配置已更新 | INFO |
| `ProxyCidrsUpdated` | 代理 CIDR 更新: +{added} -{removed} | INFO |
| `UdpBroadcastRelayStartResult` | UDP 广播转发启动: {backend}（{error}） | WARNING/ERROR |
| `CredentialChanged` | 凭证已变更 | INFO |

## 验证方法

由于项目缺少 gradle-wrapper.jar，需要在 Android Studio 中执行 `Build > Make Project` 验证编译。

### 功能测试清单
1. **通知进入**：启动 VPN → 按 HOME 键回桌面 → 从通知栏点击 EasyTier 通知 → 应用应恢复到前台，服务不应停止，VPN 图标仍在
2. **通知停止**：启动 VPN → 点击通知的"停止"按钮 → 服务停止，控制/状态/日志页均显示已停止，VPN 图标消失
3. **配置持久化**：在配置中心页输入 URL/主机名/安全模式 → 连接 → 重启应用 → 配置应保留
4. **自动连接**：勾选"启动应用时自动连接" → 重启应用 → 应自动连接配置服务器
5. **控制页蒙版**：配置服务器启动实例后 → 切换到控制页 → 应看到半透明蒙版和提示，配置不可编辑，停止按钮可点击且能停止远程实例
6. **事件日志**：配置服务器下发事件 → 事件日志应显示彩色、格式化的摘要
7. **日志翻译**：触发各种网络事件 → 日志页应显示中文翻译而非原始事件类型名
