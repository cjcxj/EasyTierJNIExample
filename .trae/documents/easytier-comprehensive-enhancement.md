# EasyTierJNIExample 综合增强计划

## Context

本项目是 EasyTier 的 Android JNI 示例应用，目标是与上游 EasyTier 保持功能对齐。当前已完成控制面 JNI 封装与基础 UI（控制/状态/日志三个 Tab），但存在以下缺口：

1. **VPN 服务实现薄弱**：文件名异常（`EasyTierVpnService.t.kt`）、用 `Thread.sleep` 循环保活、DNS 硬编码、不支持 IPv6、非前台服务易被杀。
2. **数据面 JNI 完全闲置**：[EasyTierDataPlaneJNI.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/jni/EasyTierDataPlaneJNI.kt) 已声明 23 个 TCP/UDP 异步操作函数，但全项目无任何调用代码，无法验证虚拟网络通信能力。
3. **配置服务器客户端未接入 UI**：`startConfigServerClient` 等 JNI 接口已暴露，但 UI 无入口，无法对接上游远程配置下发能力。
4. **通用 RPC 调用无界面**：`callJsonRpc` 已暴露，但缺少开发者向的调试面板。

用户希望全面铺开上述四个方向。考虑到工作量巨大且用户偏好逐步确认，本计划将工作拆为 5 个独立可交付的阶段，按"先基础后功能、先低风险后高价值"排序，每阶段独立交付与验证。

## 阶段划分与优先级

| 阶段 | 主题 | 风险 | 依赖 |
|------|------|------|------|
| P1 | VPN 服务健壮性优化 | 低 | 无 |
| P2 | EasyTierManager 协程化 | 低 | 无（可与 P1 并行） |
| P3 | 数据面 JNI 实战调试 Tab | 中 | 实例需运行 |
| P4 | 配置服务器客户端 UI | 中 | 无 |
| P5 | 通用 RPC 调试工具 | 低 | 无 |

建议按 P1 → P2 → P3 → P4 → P5 顺序推进，每阶段完成后用户审核确认再进入下一阶段。

---

## P1：VPN 服务健壮性优化

### 改动文件
- `app/src/main/java/com/easytier/jni/EasyTierVpnService.t.kt` → 重命名为 `EasyTierVpnService.kt`（注意更新 AndroidManifest 中的类名引用，应无需改动因 manifest 用全限定名）
- `app/src/main/AndroidManifest.xml` — 添加 `FOREGROUND_SERVICE` 权限与 `FOREGROUND_SERVICE_SPECIAL_USE`（API 34+）
- `app/src/main/java/com/easytier/jni/EasyTierManager.kt` — 传递 DNS、IPv6 参数给 VpnService
- `app/src/main/java/com/easytier/app/ui/MainViewModel.kt` — 透传 ConfigData 中的 DNS/IPv6 字段

### 具体改动

1. **文件重命名**：`EasyTierVpnService.t.kt` → `EasyTierVpnService.kt`。需确认 manifest 引用不变（`android:name=".EasyTierVpnService"` 这种相对名不受文件名影响）。

2. **Foreground Service 改造**：
   - `onStartCommand` 中先调用 `startForeground(id, notification)` 再做后续工作
   - 新增 `createNotificationChannel()`（在 `onCreate` 调用）
   - 通知文案："EasyTier VPN 运行中"，带"停止"action 调用 `stopSelf()`
   - 移除 `while (isRunning && vpnInterface != null) { Thread.sleep(1000) }` 循环，前台服务本身保活

3. **DNS 可配置**：
   - `EasyTierVpnService` 接收 `dns_servers`（ArrayList<String>）extra
   - 若为空，回退到 `223.5.5.5`、`114.114.114.114`（保持兼容）
   - `EasyTierManager.startVpnService` 从 ConfigData 读取（新增 `dnsServers` 字段，默认空，对齐上游 `dns` 配置项）

4. **IPv6 支持**：
   - `EasyTierVpnService` 接收 `ipv6_address`（String?）extra
   - 非空时 `builder.addAddress(ipv6, prefixLen)` 并为 IPv6 proxy cidrs 调用 `builder.addRoute()`
   - `EasyTierManager` 从 `DetailedNetworkInfo` 解析 IPv6 地址并传递（若 ConfigData.ipv6 已配置则用配置值，否则用运行时获取的虚拟 IPv6）

5. **Per-app VPN（可选增强）**：
   - ConfigData 新增 `vpnAllowedApps: List<String>` 与 `vpnDisallowedAppsMode: Boolean`
   - 默认保持现有 `addDisallowedApplication(packageName)` 行为
   - UI 暂不暴露（仅在 ConfigData 预留字段）

### 验证
- 启动 VPN 后通知栏出现"EasyTier VPN 运行中"
- 按下 Home 长时间后台不被杀（前台服务保活）
- 配置 DNS 后 `adb shell settings get global dns_server` 或日志确认生效
- 配置 IPv6 后 `adb shell ifconfig tun0` 看到 inet6 地址

---

## P2：EasyTierManager 协程化

### 改动文件
- `app/src/main/java/com/easytier/jni/EasyTierManager.kt`

### 具体改动

1. **移除 Handler + postDelayed**：
   - 删除 `private val handler = Handler(Looper.getMainLooper())` 与 `monitorRunnable`
   - 新增 `suspend fun startMonitoring()` 用 `while (isActive) { monitorNetworkStatus(); delay(MONITOR_INTERVAL) }`
   - 由 MainViewModel 在 `viewModelScope` 中调用（已有 `while(true){...delay(2000)}` 模式可统一）

2. **状态收集改为 Flow**：
   - 将 `currentIpv4`、`currentProxyCidrs` 改为 `MutableStateFlow`
   - MainViewModel 直接 collect，无需 `getStatus()` 轮询

3. **错误处理增强**：
   - `start()` 失败时通过 Flow 发送错误事件，UI 可订阅展示（替代当前仅 Log.e）

### 验证
- 启动/停止功能与改造前一致
- 网络状态变化时 UI 自动刷新（无需 manualRefresh）
- 无内存泄漏（LeakCanary 或手动检查）

---

## P3：数据面 JNI 实战调试 Tab

### 新增文件
- `app/src/main/java/com/easytier/app/ui/DataPlaneDebugTab.kt` — 数据面调试 UI
- `app/src/main/java/com/easytier/jni/DataPlaneClient.kt` — 数据面操作封装（协程友好的封装层）

### 改动文件
- `app/src/main/java/com/easytier/app/ui/MainScreen.kt` — 添加第 4 个 tab "调试"
- `app/src/main/java/com/easytier/app/ui/MainViewModel.kt` — 暴露数据面操作方法

### 具体改动

1. **DataPlaneClient.kt**（封装层）：
   - 将 `*Start`/`*Finish` 配对封装为 suspend 函数，内部用 `dataPlaneAsyncOpWait` 等待完成
   - 例如 `suspend fun tcpConnect(instanceName, ip, port, timeoutMs): DataPlaneTcpConnectResult?`
   - 用 `withContext(Dispatchers.IO)` 包裹
   - 提供 `tcpEchoTest()`、`udpEchoTest()` 等便利方法

2. **DataPlaneDebugTab.kt**（UI）：
   - **TCP 客户端区**：目标 IP/Port 输入、发送数据框、连接/发送/接收按钮、结果显示
   - **TCP 服务器区**：监听端口输入、启动监听/停止按钮、accept 日志列表
   - **UDP 调试区**：目标 IP/Port、发送数据、接收数据显示
   - 复用 [Common.kt](file:///d:/SRC/android/EasyTierJNIExample/app/src/main/java/com/easytier/app/ui/Common.kt) 的 StatusRow、pressableScale
   - 仅当 `isRunning` 为 true 时启用控件

3. **MainScreen.kt**：
   - `tabs` 列表添加 `TabItem("调试", Icons.Default.NetworkCheck)`
   - `when (page)` 添加 `3 -> DataPlaneDebugTab(...)`

4. **MainViewModel.kt**：
   - 新增 `dataPlaneClient: DataPlaneClient?`（实例运行时创建）
   - 暴露 `tcpConnect()`、`tcpSend()`、`udpSend()` 等方法供 UI 调用
   - 用 SharedFlow 发送数据面操作结果

### 验证
- 启动 EasyTier 实例后切换到"调试"tab
- TCP 客户端连接到虚拟网络中已知端口，能发送/接收数据
- TCP 服务器在虚拟网络中监听，另一节点能连接
- UDP 收发正常
- 不阻塞主线程（所有 JNI 调用在 IO 线程）

---

## P4：配置服务器客户端 UI

### 新增文件
- `app/src/main/java/com/easytier/app/ui/ConfigServerTab.kt` — 配置服务器客户端 UI
- `app/src/main/java/com/easytier/jni/ConfigServerClientManager.kt` — 客户端管理器

### 改动文件
- `app/src/main/java/com/easytier/app/ui/MainScreen.kt` — 添加第 5 个 tab "配置中心"
- `app/src/main/java/com/easytier/app/ui/MainViewModel.kt` — 暴露客户端管理方法
- `app/src/main/java/com/easytier/app/SettingsRepository.kt` — 持久化 machineId、configServerUrl
- `app/src/main/java/com/easytier/app/ConfigData.kt` — 新增 `configServerUrl` 字段（可选）

### 具体改动

1. **ConfigServerClientManager.kt**：
   - 封装 `startConfigServerClient`/`stopConfigServerClient`/`isConfigServerClientConnected`
   - 接收 `ConfigServerEventCallback`，将事件 JSON 转为结构化事件并通过 Flow 发送
   - machineId 自动生成（UUID）并持久化，首次启动时生成

2. **ConfigServerTab.kt**（UI）：
   - **连接配置区**：URL 输入、hostname（可选）、secureMode 开关、machineId（只读展示 + 复制）
   - **连接/断开按钮**
   - **连接状态指示**（连接中/已连接/已断开）
   - **事件流区**：实时显示远程下发配置事件（原始 JSON + 简要描述）
   - 复用 ConfigSwitchWithInlineHelp 展示 secureMode 帮助

3. **SettingsRepository.kt**：
   - 新增 `getMachineId()`/`setMachineId()`（首次返回随机 UUID）
   - 新增 `getConfigServerUrl()`/`setConfigServerUrl()`

4. **事件 JSON schema 处理**：
   - 首版以原始 JSON 字符串展示（黑盒）
   - 后续根据上游 Rust 源码确认事件结构后，再做结构化解析（标注为 TODO）

### 验证
- 输入配置服务器 URL，点击连接后状态变为"已连接"
- 远程下发配置时事件流实时刷新
- 断开重连正常
- machineId 重启 App 后保持不变

---

## P5：通用 RPC 调试工具

### 新增文件
- `app/src/main/java/com/easytier/app/ui/RpcDebugTab.kt` — RPC 调试 UI
- `app/src/main/java/com/easytier/jni/RpcServiceRegistry.kt` — 预定义服务/方法清单

### 改动文件
- `app/src/main/java/com/easytier/app/ui/MainScreen.kt` — 添加第 6 个 tab "RPC"
- `app/src/main/java/com/easytier/app/ui/MainViewModel.kt` — 暴露 callJsonRpc 封装

### 具体改动

1. **RpcServiceRegistry.kt**（预定义清单）：
   - 基于已读取的 proto 文件，硬编码以下服务/方法清单：
     - `api.instance.PeerManageRpc`: ListPeer、ListRoute、DumpRoute、ListForeignNetwork、ListGlobalForeignNetwork、ShowNodeInfo、GetForeignNetworkSummary、ListPublicIpv6Info
     - `api.instance.ConnectorManageRpc`: ListConnector
     - `api.instance.MappedListenerManageRpc`: ListMappedListener
     - `api.logger.LoggerRpc`: GetLoggerConfig、SetLoggerConfig
   - 每个方法附带默认 payload 模板（含 instance selector）
   - 注释标注 WebClientService 不支持（JNI 已明确排除）

2. **RpcDebugTab.kt**（UI）：
   - **服务选择**：下拉框（从 RpcServiceRegistry 读取）
   - **方法选择**：下拉框（依赖服务选择，动态更新）
   - **payload 编辑**：多行文本框，预填默认模板，可编辑
   - **调用按钮**：仅 isRunning 时启用
   - **响应展示**：格式化 JSON 显示，支持复制
   - **历史记录**：最近 10 次调用，可点击重放

3. **MainViewModel.kt**：
   - 新增 `callRpc(service, method, payload)` suspend 方法
   - 用 `withContext(Dispatchers.IO)` 包裹 `EasyTierJNI.callJsonRpc`
   - 结果通过 SharedFlow 发送

### 验证
- isRunning 时选择 `PeerManageRpc.ShowNodeInfo`，调用后返回当前节点信息 JSON
- 调用 `ListPeer` 返回对等节点列表
- 调用 `LoggerRpc.GetLoggerConfig` 返回日志配置
- 历史记录可重放
- 非 isRunning 时调用按钮禁用

---

## 横切关注点

### 测试覆盖（与各阶段并行）
- `app/src/test/java/com/easytier/app/ConfigDataMapperTest.kt` — TOML 往返一致性
- `app/src/test/java/com/easytier/jni/NetworkInfoParserTest.kt` — 快照解析（用真实 JSON 样本）
- `app/src/test/java/com/easytier/jni/DataPlaneModelsTest.kt` — ByteArray equals 重写后的行为验证

### 上游对齐检查
- 每阶段完成后核对 JNI external 函数清单与上游 `easytier-contrib/easytier-android-jni/src/lib.rs` 一致
- TOML 字段命名与上游 `config.rs` 一致
- proto 文件保持与上游同步（已有 proto/ 目录）

### 风险与回滚
- 每阶段独立提交，便于回滚
- P1 改 VPN 服务可能影响现网运行，需在真机验证
- P3/P4/P5 新增 tab 不影响现有功能，风险低

## 实施建议

1. 严格按 P1→P5 顺序，每阶段完成后用户审核确认
2. 每阶段一个 commit，commit message 描述改动范围
3. P1/P2 可考虑合并为一个 PR（基础优化）
4. P3/P4/P5 各自独立 PR（新功能）
5. 测试覆盖随各阶段同步补充
