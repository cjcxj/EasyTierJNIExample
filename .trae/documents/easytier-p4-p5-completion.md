# EasyTier 增强计划：P4 配置中心集成 + P5 RPC 调试工具

> 本计划承接此前会话：P1（VPN Service 鲁棒性）、P2（协程迁移）、P3（数据面调试 Tab）已完成；
> P4 的 `ConfigServerClientManager.kt` 与 `ConfigServerTab.kt` 已创建但未集成；
> P5（通用 RPC 调试工具）尚未开始。本计划覆盖剩余全部工作。

## 一、当前状态分析

### 已完成（无需改动）
- `EasyTierVpnService.kt`：前台服务 + DNS + IPv6 ✅
- `EasyTierManager.kt`：协程迁移 ✅
- `DataPlaneClient.kt` + `DataPlaneDebugTab.kt`：数据面调试（已作为第 4 个 Tab 集成）✅
- `ConfigServerClientManager.kt`：封装 `startConfigServerClient/stopConfigServerClient/isConfigServerClientConnected`，暴露 `connectionState: StateFlow` 与 `events: SharedFlow` ✅（**已创建，未集成**）
- `ConfigServerTab.kt`：配置中心 UI（URL/hostname/secureMode/machineId/事件流）✅（**已创建，未集成**）

### 待完成
- **P4 Task #13**：将 `ConfigServerTab` 接入应用
  - `SettingsRepository.kt` 缺少 `machineId` 和 `configServerUrl` 的持久化
  - `MainViewModel.kt` 缺少 `ConfigServerClientManager` 实例与 `machineId` 状态
  - `MainScreen.kt` 缺少第 5 个 Tab
  - `MainActivity.kt` 未传递 `configServerManager` 与 `machineId`
- **P5**：通用 RPC 调试工具
  - 新建 `RpcServiceRegistry.kt`：RPC 预设定义
  - 新建 `RpcDebugTab.kt`：RPC 调用 UI
  - `MainScreen.kt` 添加第 6 个 Tab
  - `MainActivity.kt` / `MainViewModel.kt` 传递 `instanceName`

## 二、P4 Task #13：集成配置中心 Tab

### 2.1 修改 `SettingsRepository.kt`
**文件**：[SettingsRepository.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/SettingsRepository.kt)

**改动**：新增 4 个方法，持久化 `machineId`（首次自动生成 UUID）和 `configServerUrl`。

```kotlin
// 新增 PreferencesKey
private val machineIdKey = stringPreferencesKey("machine_id")
private val configServerUrlKey = stringPreferencesKey("config_server_url")

/** 获取机器 ID，若不存在则生成 UUID 并持久化后返回 */
suspend fun getMachineId(): String {
    val existing = context.dataStore.data.map { it[machineIdKey] }.first()
    if (!existing.isNullOrBlank()) return existing
    val newId = UUID.randomUUID().toString()
    context.dataStore.edit { it[machineIdKey] = newId }
    return newId
}

suspend fun getConfigServerUrl(): String? {
    return context.dataStore.data.map { it[configServerUrlKey] }.first()
}

suspend fun setConfigServerUrl(url: String) {
    context.dataStore.edit { it[configServerUrlKey] = url }
}
```

**需新增 import**：`java.util.UUID`（如未存在）

### 2.2 修改 `MainViewModel.kt`
**文件**：[MainViewModel.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainViewModel.kt)

**改动**：
1. 新增 `ConfigServerClientManager` 实例（与 `easyTierManager` 同级，长期持有，独立于 EasyTier 实例生命周期）
2. 新增 `machineId` 状态，在 `init` 中加载
3. `onCleared()` 中停止 `ConfigServerClientManager`

```kotlin
// 新增字段（与 _dataPlaneClient 同区）
private val _configServerManager = mutableStateOf<ConfigServerClientManager?>(null)
val configServerManager: State<ConfigServerClientManager?> = _configServerManager

private val _machineId = mutableStateOf("")
val machineId: State<String> = _machineId

// init 块新增（在现有 loadAllConfigs launch 之后）
viewModelScope.launch {
    _machineId.value = settingsRepository.getMachineId()
    _configServerManager.value = ConfigServerClientManager()
}

// onCleared() 新增
override fun onCleared() {
    super.onCleared()
    _configServerManager.value?.stop()
    stopEasyTier()
}
```

**需新增 import**：`com.easytier.jni.ConfigServerClientManager`

### 2.3 修改 `MainScreen.kt`
**文件**：[MainScreen.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/MainScreen.kt)

**改动**：
1. 函数签名新增参数 `configServerManager: ConfigServerClientManager?` 和 `machineId: String`
2. `tabs` 列表新增第 5 项：`TabItem("配置中心", Icons.Default.CloudSync)`
3. `HorizontalPager` 的 `when(page)` 新增 `4 -> ConfigServerTab(manager = configServerManager, machineId = machineId)`
4. 新增 import：`com.easytier.jni.ConfigServerClientManager`、`androidx.compose.material.icons.filled.CloudSync`

### 2.4 修改 `MainActivity.kt`
**文件**：[MainActivity.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/MainActivity.kt)

**改动**：
1. 在 `setContent` 中新增：`val configServerManager by viewModel.configServerManager`、`val machineId by viewModel.machineId`
2. `MainScreen(...)` 调用新增：`configServerManager = configServerManager, machineId = machineId`

## 三、P5：通用 RPC 调试工具

### 3.1 新建 `RpcServiceRegistry.kt`
**文件**：`app/src/main/java/com/easytier/jni/RpcServiceRegistry.kt`

**目的**：定义常用 RPC 预设，减少用户手输 JSON 的负担。基于 proto 探索结果，下列服务可用（`api.manage.WebClientService` 不支持，已排除）。

**设计**：
```kotlin
package com.easytier.jni

/**
 * RPC 预设：基于 EasyTier proto 定义。
 * serviceName 格式为 "包名.服务名"，例如 "api.instance.PeerManageRpc"。
 * payloadJson 中 InstanceIdentifier.instance_selector.name 填实例名。
 */
data class RpcPreset(
    val displayName: String,
    val serviceName: String,
    val methodName: String,
    val domainName: String = "",        // 仅 TcpProxyRpc 使用
    val payloadTemplate: String,         // 含 {instance} 占位符的 JSON 模板
    val description: String
)

object RpcServiceRegistry {
    /** 常用 RPC 预设列表 */
    val PRESETS: List<RpcPreset> = listOf(
        RpcPreset(
            displayName = "列出节点 (ListPeer)",
            serviceName = "api.instance.PeerManageRpc",
            methodName = "list_peer",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出当前网络中所有对等节点信息"
        ),
        RpcPreset(
            displayName = "列出路由 (ListRoute)",
            serviceName = "api.instance.PeerManageRpc",
            methodName = "list_route",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出当前节点的路由表"
        ),
        RpcPreset(
            displayName = "节点信息 (ShowNodeInfo)",
            serviceName = "api.instance.PeerManageRpc",
            methodName = "show_node_info",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "展示当前节点详细信息"
        ),
        RpcPreset(
            displayName = "列出连接器 (ListConnector)",
            serviceName = "api.instance.ConnectorManageRpc",
            methodName = "list_connector",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出所有连接器及其状态"
        ),
        RpcPreset(
            displayName = "VPN Portal 信息",
            serviceName = "api.instance.VpnPortalRpc",
            methodName = "get_vpn_portal_info",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取 VPN Portal 配置与已连接客户端"
        ),
        RpcPreset(
            displayName = "TCP 代理表项 (ListTcpProxyEntry)",
            serviceName = "api.instance.TcpProxyRpc",
            methodName = "list_tcp_proxy_entry",
            domainName = "tcp",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出 TCP 代理连接表项（需 domainName=tcp）"
        ),
        RpcPreset(
            displayName = "统计指标 (GetStats)",
            serviceName = "api.instance.StatsRpc",
            methodName = "get_stats",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取节点运行指标快照"
        ),
        RpcPreset(
            displayName = "Prometheus 指标",
            serviceName = "api.instance.StatsRpc",
            methodName = "get_prometheus_stats",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取 Prometheus 文本格式指标"
        ),
        RpcPreset(
            displayName = "ACL 统计 (GetAclStats)",
            serviceName = "api.instance.AclManageRpc",
            methodName = "get_acl_stats",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取 ACL 命中统计"
        ),
        RpcPreset(
            displayName = "端口转发列表 (ListPortForward)",
            serviceName = "api.instance.PortForwardManageRpc",
            methodName = "list_port_forward",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出已配置的端口转发规则"
        )
    )

    /** 将模板中的 {instance} 占位符替换为实际实例名 */
    fun buildPayload(template: String, instanceName: String): String {
        return template.replace("{instance}", instanceName)
    }
}
```

### 3.2 新建 `RpcDebugTab.kt`
**文件**：`app/src/main/java/com/easytier/app/ui/RpcDebugTab.kt`

**设计要点**：
- 顶部：预设下拉（`ExposedDropdownMenuBox`），选中后自动填充 serviceName/methodName/domainName/payloadJson
- 中部：4 个可编辑输入框（serviceName、methodName、domainName、payloadJson 多行）
- payloadJson 框支持「美化/压缩」按钮（用 `JSONObject` 格式化）
- 「调用」按钮：调用 `EasyTierJNI.callJsonRpc(...)`，IO 线程
- 底部：响应展示区（可折叠、可复制、带耗时显示）
- 调用历史列表（最近 10 次，点击可回填）

```kotlin
@Composable
fun RpcDebugTab(
    rpcClient: RpcClient?,              // 包装类，可为 null
    instanceName: String                // 默认实例名，用于填充预设模板
)
```

**新建辅助类 `RpcClient.kt`**（`app/src/main/java/com/easytier/jni/RpcClient.kt`）：
```kotlin
package com.easytier.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 通用 RPC 客户端，封装 EasyTierJNI.callJsonRpc 的 IO 切换与异常处理 */
class RpcClient {
    companion object { private const val TAG = "RpcClient" }

    data class RpcResult(
        val success: Boolean,
        val response: String?,
        val error: String?,
        val durationMs: Long
    )

    suspend fun call(
        serviceName: String,
        methodName: String,
        domainName: String?,
        payloadJson: String
    ): RpcResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val resp = EasyTierJNI.callJsonRpc(serviceName, methodName, domainName, payloadJson)
            RpcResult(
                success = resp != null,
                response = resp,
                error = if (resp == null) EasyTierJNI.getLastError() else null,
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Log.e(TAG, "RPC 调用异常: $serviceName.$methodName", e)
            RpcResult(false, null, e.message, System.currentTimeMillis() - start)
        }
    }
}
```

### 3.3 集成到 ViewModel / MainScreen / MainActivity

**`MainViewModel.kt`**：
```kotlin
private val _rpcClient = mutableStateOf<RpcClient?>(null)
val rpcClient: State<RpcClient?> = _rpcClient

// init 中创建（与 ConfigServerClientManager 一起）
viewModelScope.launch {
    _rpcClient.value = RpcClient()
}
```

**`MainScreen.kt`**：
- 新增参数 `rpcClient: RpcClient?`
- 新增第 6 个 Tab：`TabItem("RPC", Icons.Default.Terminal)`
- `when(page)` 新增：`5 -> RpcDebugTab(rpcClient = rpcClient, instanceName = activeConfig.instanceName)`

**`MainActivity.kt`**：
- 新增 `val rpcClient by viewModel.rpcClient`
- 传入 MainScreen

## 四、最终 Tab 结构

| 索引 | Tab 名 | 图标 | 内容 |
|------|--------|------|------|
| 0 | 控制 | Settings | 配置编辑/启停 |
| 1 | 状态 | ShowChart | 网络状态/节点列表 |
| 2 | 日志 | List | 事件日志/导出 |
| 3 | 调试 | NetworkCheck | 数据面 TCP/UDP 调试 |
| 4 | 配置中心 | CloudSync | 远程配置服务器客户端 |
| 5 | RPC | Terminal | 通用 RPC 调试工具 |

## 五、执行步骤顺序

1. **P4-1**：修改 `SettingsRepository.kt`（新增 4 方法 + 2 Key）
2. **P4-2**：修改 `MainViewModel.kt`（新增 ConfigServerClientManager + machineId 状态）
3. **P4-3**：修改 `MainScreen.kt`（新增第 5 Tab + 参数）
4. **P4-4**：修改 `MainActivity.kt`（传递 configServerManager + machineId）
5. **P5-1**：新建 `RpcClient.kt`
6. **P5-2**：新建 `RpcServiceRegistry.kt`
7. **P5-3**：新建 `RpcDebugTab.kt`
8. **P5-4**：修改 `MainViewModel.kt`（新增 rpcClient 状态）
9. **P5-5**：修改 `MainScreen.kt`（新增第 6 Tab + 参数）
10. **P5-6**：修改 `MainActivity.kt`（传递 rpcClient）

## 六、验证步骤

由于环境缺少 `gradle-wrapper.jar`，无法执行编译验证。代码完成后需用户在 Android Studio 中：
1. 执行 `Build > Make Project` 确认编译通过
2. 运行应用，验证 6 个 Tab 均可切换
3. 启动 EasyTier 实例后：
   - 切到「配置中心」Tab，确认 machineId 自动生成并显示
   - 切到「RPC」Tab，选择「列出节点」预设，点击调用，确认返回 JSON
4. 旋转屏幕，确认配置中心 URL/hostname/secureMode 不丢失（rememberSaveable）

## 七、假设与决策

1. **`ConfigServerClientManager` 生命周期**：独立于 EasyTier 实例，长期持有；在 `onCleared()` 停止。理由：配置服务器客户端可在 EasyTier 未运行时单独使用（用于拉取配置后再启动实例）。
2. **`machineId` 持久化策略**：首次调用 `getMachineId()` 时生成 UUID 并立即持久化，确保后续调用稳定返回同一值。
3. **`configServerUrl` 持久化**：虽然 `ConfigServerTab.kt` 当前用 `rememberSaveable` 保存 URL，但额外持久化到 DataStore 可在重装/清数据后恢复。**决策**：暂不持久化 URL，保持现有 `rememberSaveable` 行为，避免过度设计（用户每次重连输入成本很低）。
4. **RPC 预设方法名**：使用 snake_case（如 `list_peer`），JNI 注释明确支持 snake_case 或 proto 方法名。
5. **`RpcClient` 与 `DataPlaneClient` 一致**：均为轻量包装类，suspend + IO 切换，异常捕获到 Result。
6. **不支持 WebClientService**：按 JNI 注释约束，预设中不包含 `api.manage.WebClientService`。
