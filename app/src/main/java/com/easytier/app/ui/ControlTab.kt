package com.easytier.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.app.ConfigData
import com.easytier.app.PortForwardItem
import com.easytier.app.ui.common.ConfigSwitchWithInlineHelp

private object HelpTexts {
    const val RELAY_WHITELIST =
        "仅转发白名单网络的流量，支持通配符。多个网络名用空格隔开。为空则禁用转发。例如：'*', 'def*', 'net1 net2'"
    const val LATENCY_FIRST = "忽略中转跳数，选择总延迟最低的路径进行通信。"
    const val PRIVATE_MODE = "启用后，不允许与本机网络名称和密码不符的节点通过本机进行握手或中转。"
    const val ENABLE_KCP = "将 TCP 流量转为 KCP 流量，可降低延迟、提升速度。"
    const val DISABLE_KCP_INPUT = "禁用 KCP 入站流量。其他节点将使用 TCP 连接到本节点。"
    const val DISABLE_P2P = "禁用 P2P 模式，所有流量将通过手动指定的服务器中转。"
    const val NO_TUN =
        "不创建 TUN 网卡，适合无管理员权限时使用。本节点仅允许被访问，访问其他节点需使用 SOCKS5 代理。"
    const val MULTI_THREAD = "使用多线程模式运行核心服务，可能提升性能。"
    const val ACCEPT_DNS = "启用魔法 DNS，可通过 'hostname.et.net' 访问网络内其他节点。"
    const val BIND_DEVICE = "仅在物理网络接口上监听和建立连接，避免通过其他 VPN 或虚拟网卡通信。"
    const val ENABLE_EXIT_NODE = "允许此节点作为其他节点的网络出口，转发其所有流量到公共互联网。"
    const val DISABLE_ENCRYPTION =
        "禁用对等节点间的通信加密。警告：不安全，仅在特殊网络环境下使用，且需所有节点配置一致。"
    const val DISABLE_IPV6 = "禁用此节点的 IPv6 功能，仅使用 IPv4 进行网络通信。"
    const val ENABLE_QUIC = "将 TCP 流量转为 QUIC 流量，可降低延迟、提升速度。"
    const val DISABLE_QUIC_INPUT = "禁用 QUIC 入站流量。其他节点将使用 TCP 连接到本节点。"
    const val DISABLE_UDP_HOLE_PUNCHING = "禁用常规的 UDP 打洞尝试，可能影响 P2P 连接建立。"
    const val DISABLE_SYM_HOLE_PUNCHING =
        "禁用针对对称 NAT 的打洞技术（生日攻击），会将其视为更简单的锥形 NAT 处理。"
    const val RELAY_ALL_RPC =
        "允许转发所有对等节点的 RPC 数据包，即使它们不在转发网络白名单中。有助于白名单外的节点建立 P2P 连接。"
    const val PROXY_FORWARD_BY_SYSTEM = "通过操作系统内核（而非内置 NAT）来转发子网代理的数据包。"
    const val USE_SMOLTCP =
        "使用用户态 TCP/IP 协议栈，可绕过某些操作系统防火墙限制，改善子网代理或 KCP 代理的兼容性。"
}


// --- 标签页1: 控制 (Control) ---
/**
 * “控制”标签页的UI
 * 集成了多配置管理（切换、添加、删除）和当前配置的完整编辑功能。
 *
 * @param allConfigs 所有已保存的配置列表。
 * @param activeConfig 当前激活的配置。
 * @param onActiveConfigChange 当用户切换配置时调用。
 * @param onAddNewConfig 当用户点击“添加新配置”时调用。
 * @param onDeleteConfig 当用户确认删除当前配置时调用。
 * @param onConfigChange 当用户编辑当前配置的任何字段时调用。
 * @param isRunning 服务当前是否在运行。
 * @param onControlButtonClick 当主启动/停止按钮被点击时调用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlTab(
    allConfigs: List<ConfigData>,
    activeConfig: ConfigData,
    onActiveConfigChange: (ConfigData) -> Unit,
    onAddNewConfig: () -> Unit,
    onDeleteConfig: (ConfigData) -> Unit,
    onConfigChange: (ConfigData) -> Unit,
    isRunning: Boolean,
    onControlButtonClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 顶部控制行 ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onControlButtonClick,
                modifier = Modifier.weight(1f),
                enabled = activeConfig.instanceName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) { Text(if (isRunning) "停止服务" else "启动服务", fontSize = 18.sp) }

            Box {
                IconButton(onClick = { showMenu = true }, enabled = !isRunning) {
                    Icon(
                        Icons.Default.MoreVert,
                        "配置选项"
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    allConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = { Text(config.instanceName) },
                            onClick = { onActiveConfigChange(config); showMenu = false },
                            leadingIcon = {
                                if (config.id == activeConfig.id) Icon(
                                    Icons.Default.Check,
                                    "当前选中"
                                )
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        { Text("添加新配置") },
                        { onAddNewConfig(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Add, "添加") })
                    DropdownMenuItem(
                        { Text("删除当前配置") },
                        { showDeleteDialog = true; showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, "删除") },
                        enabled = allConfigs.size > 1
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // --- 核心配置 ---
        CollapsibleConfigSection(title = "核心配置", initiallyExpanded = true) {
            ConfigTextField(
                "实例名",
                activeConfig.instanceName,
                { onConfigChange(activeConfig.copy(instanceName = it)) },
                enabled = !isRunning
            )
            ConfigTextField(
                "网络名",
                activeConfig.networkName,
                { onConfigChange(activeConfig.copy(networkName = it)) },
                enabled = !isRunning
            )
            ConfigTextField(
                "网络密钥",
                activeConfig.networkSecret,
                { onConfigChange(activeConfig.copy(networkSecret = it)) },
                enabled = !isRunning
            )
            ConfigTextField(
                label = "对等节点 (Peers, 每行一个)",
                value = activeConfig.peers,
                onValueChange = { onConfigChange(activeConfig.copy(peers = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(120.dp)
            )
        }

        // --- IP & 接口配置 ---
        CollapsibleConfigSection(title = "IP 与接口") {
            // DHCP 开关
            ConfigSwitch(
                label = "自动分配IP (DHCP)",
                checked = activeConfig.dhcp,
                onCheckedChange = { dhcpEnabled ->
                    // 启用DHCP时清空静态IP，禁用时不做改变让用户手动输入
                    onConfigChange(
                        if (dhcpEnabled) activeConfig.copy(dhcp = true, virtualIpv4 = "")
                        else activeConfig.copy(dhcp = false)
                    )
                },
                enabled = !isRunning
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = activeConfig.virtualIpv4,
                    onValueChange = { onConfigChange(activeConfig.copy(virtualIpv4 = it)) },
                    label = { Text("静态IPv4地址") },
                    enabled = !isRunning && !activeConfig.dhcp,
                    modifier = Modifier.weight(3f),
                    placeholder = { Text("10.0.0.1") }
                )
                Text("/", modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = activeConfig.networkLength.toString(),
                    onValueChange = {
                        onConfigChange(
                            activeConfig.copy(
                                networkLength = it.toIntOrNull()?.coerceIn(1, 32)
                                    ?: activeConfig.networkLength
                            )
                        )
                    },
                    label = { Text("掩码") },
                    enabled = !isRunning && !activeConfig.dhcp,
                    modifier = Modifier.weight(1.5f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(8.dp))

            ConfigTextField(
                "主机名",
                activeConfig.hostname,
                { onConfigChange(activeConfig.copy(hostname = it)) },
                enabled = !isRunning,
                placeholder = "留空则自动获取"
            )
            ConfigTextField(
                "监听器",
                activeConfig.listenerUrls,
                { onConfigChange(activeConfig.copy(listenerUrls = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(100.dp)
            )
            ConfigTextField(
                "映射监听器",
                activeConfig.mappedListeners,
                { onConfigChange(activeConfig.copy(mappedListeners = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(80.dp)
            )
            ConfigTextField(
                "TUN设备名 (dev_name)",
                activeConfig.devName,
                { onConfigChange(activeConfig.copy(devName = it)) },
                enabled = !isRunning,
                placeholder = "留空则自动"
            )
            ConfigTextField(
                "MTU",
                activeConfig.mtu,
                { onConfigChange(activeConfig.copy(mtu = it)) },
                enabled = !isRunning,
                placeholder = "留空使用默认值",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

        }

        // --- 功能标志 (Flags) ---
        CollapsibleConfigSection(title = "功能标志 (Flags)") {
            ConfigSwitchWithInlineHelp(
                "启用转发白名单",
                activeConfig.enableRelayNetworkWhitelist,
                { onConfigChange(activeConfig.copy(enableRelayNetworkWhitelist = it)) },
                HelpTexts.RELAY_WHITELIST,
                enabled = !isRunning
            )
            ConfigTextField(
                "转发白名单",
                activeConfig.relayNetworkWhitelist,
                { onConfigChange(activeConfig.copy(relayNetworkWhitelist = it)) },
                enabled = !isRunning && activeConfig.enableRelayNetworkWhitelist
            )
            Spacer(Modifier.height(8.dp))

            // 其他所有开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    ConfigSwitchWithInlineHelp(
                        "延迟优先",
                        activeConfig.latencyFirst,
                        { onConfigChange(activeConfig.copy(latencyFirst = it)) },
                        HelpTexts.LATENCY_FIRST,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "私有模式",
                        activeConfig.privateMode,
                        { onConfigChange(activeConfig.copy(privateMode = it)) },
                        HelpTexts.PRIVATE_MODE,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "启用KCP",
                        activeConfig.enableKcpProxy,
                        { onConfigChange(activeConfig.copy(enableKcpProxy = it)) },
                        HelpTexts.ENABLE_KCP,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用KCP输入",
                        activeConfig.disableKcpInput,
                        { onConfigChange(activeConfig.copy(disableKcpInput = it)) },
                        HelpTexts.DISABLE_KCP_INPUT,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用P2P",
                        activeConfig.disableP2p,
                        { onConfigChange(activeConfig.copy(disableP2p = it)) },
                        HelpTexts.DISABLE_P2P,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "不创建TUN",
                        activeConfig.noTun,
                        { onConfigChange(activeConfig.copy(noTun = it)) },
                        HelpTexts.NO_TUN,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "使用多线程",
                        activeConfig.multiThread,
                        { onConfigChange(activeConfig.copy(multiThread = it)) },
                        HelpTexts.MULTI_THREAD,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "魔法DNS",
                        activeConfig.acceptDns,
                        { onConfigChange(activeConfig.copy(acceptDns = it)) },
                        HelpTexts.ACCEPT_DNS,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "绑定设备",
                        activeConfig.bindDevice,
                        { onConfigChange(activeConfig.copy(bindDevice = it)) },
                        HelpTexts.BIND_DEVICE,
                        !isRunning
                    )
                }
                Column(Modifier.weight(1f)) {
                    ConfigSwitchWithInlineHelp(
                        "允许作为出口",
                        activeConfig.enableExitNode,
                        { onConfigChange(activeConfig.copy(enableExitNode = it)) },
                        HelpTexts.ENABLE_EXIT_NODE,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用加密",
                        activeConfig.disableEncryption,
                        { onConfigChange(activeConfig.copy(disableEncryption = it)) },
                        HelpTexts.DISABLE_ENCRYPTION,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用IPv6",
                        activeConfig.disableIpv6,
                        { onConfigChange(activeConfig.copy(disableIpv6 = it)) },
                        HelpTexts.DISABLE_IPV6,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "启用QUIC",
                        activeConfig.enableQuicProxy,
                        { onConfigChange(activeConfig.copy(enableQuicProxy = it)) },
                        HelpTexts.ENABLE_QUIC,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用QUIC输入",
                        activeConfig.disableQuicInput,
                        { onConfigChange(activeConfig.copy(disableQuicInput = it)) },
                        HelpTexts.DISABLE_QUIC_INPUT,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用UDP打洞",
                        activeConfig.disableUdpHolePunching,
                        { onConfigChange(activeConfig.copy(disableUdpHolePunching = it)) },
                        HelpTexts.DISABLE_UDP_HOLE_PUNCHING,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用对称NAT打洞",
                        activeConfig.disableSymHolePunching,
                        { onConfigChange(activeConfig.copy(disableSymHolePunching = it)) },
                        HelpTexts.DISABLE_SYM_HOLE_PUNCHING,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "转发所有RPC",
                        activeConfig.relayAllPeerRpc,
                        { onConfigChange(activeConfig.copy(relayAllPeerRpc = it)) },
                        HelpTexts.RELAY_ALL_RPC,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "系统内核转发",
                        activeConfig.proxyForwardBySystem,
                        { onConfigChange(activeConfig.copy(proxyForwardBySystem = it)) },
                        HelpTexts.PROXY_FORWARD_BY_SYSTEM,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "使用SmolTCP",
                        activeConfig.useSmoltcp,
                        { onConfigChange(activeConfig.copy(useSmoltcp = it)) },
                        HelpTexts.USE_SMOLTCP,
                        !isRunning
                    )
                }
            }
        }
        // --- 高级路由配置 ---
        CollapsibleConfigSection(title = "高级路由") {
            ConfigTextField(
                "代理子网",
                activeConfig.proxyNetworks,
                { onConfigChange(activeConfig.copy(proxyNetworks = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(100.dp),
                placeholder = "每行一个CIDR"
            )
            ConfigSwitch(
                "启用自定义路由",
                activeConfig.enableManualRoutes,
                { onConfigChange(activeConfig.copy(enableManualRoutes = it)) },
                enabled = !isRunning
            )
            ConfigTextField(
                "自定义路由",
                activeConfig.routes,
                { onConfigChange(activeConfig.copy(routes = it)) },
                enabled = !isRunning && activeConfig.enableManualRoutes,
                singleLine = false,
                modifier = Modifier.height(100.dp)
            )
            ConfigTextField(
                "出口节点 (Exit Nodes)",
                activeConfig.exitNodes,
                { onConfigChange(activeConfig.copy(exitNodes = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(80.dp)
            )
        }
        // --- 服务与门户 ---
        CollapsibleConfigSection(title = "服务与门户") {
            ConfigSwitch(
                "启用SOCKS5代理",
                activeConfig.enableSocks5,
                { onConfigChange(activeConfig.copy(enableSocks5 = it)) },
                enabled = !isRunning
            )
            OutlinedTextField(
                value = activeConfig.socks5Port.toString(),
                onValueChange = {
                    onConfigChange(
                        activeConfig.copy(
                            socks5Port = it.toIntOrNull() ?: 1080
                        )
                    )
                },
                label = { Text("SOCKS5 端口") },
                enabled = !isRunning && activeConfig.enableSocks5,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(16.dp))
            ConfigSwitch(
                "启用VPN门户",
                activeConfig.enableVpnPortal,
                { onConfigChange(activeConfig.copy(enableVpnPortal = it)) },
                enabled = !isRunning
            )
            ConfigTextField(
                "VPN门户客户端网段",
                activeConfig.vpnPortalClientNetworkAddr,
                { onConfigChange(activeConfig.copy(vpnPortalClientNetworkAddr = it)) },
                enabled = !isRunning && activeConfig.enableVpnPortal
            )
            OutlinedTextField(
                value = activeConfig.vpnPortalListenPort.toString(),
                onValueChange = {
                    onConfigChange(
                        activeConfig.copy(
                            vpnPortalListenPort = it.toIntOrNull() ?: 11011
                        )
                    )
                },
                label = { Text("VPN门户监听端口") },
                enabled = !isRunning && activeConfig.enableVpnPortal,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            CollapsibleConfigSection(title = "远程管理 (RPC)") {
                ConfigTextField(
                    label = "RPC 门户地址",
                    value = activeConfig.rpcPortal,
                    onValueChange = { onConfigChange(activeConfig.copy(rpcPortal = it)) },
                    enabled = !isRunning,
                    placeholder = "例如: 0.0.0.0:15888, 留空则禁用"
                )

                ConfigTextField(
                    label = "RPC 白名单 (每行一个)",
                    value = activeConfig.rpcPortalWhitelist,
                    onValueChange = { onConfigChange(activeConfig.copy(rpcPortalWhitelist = it)) },
                    enabled = !isRunning,
                    singleLine = false,
                    modifier = Modifier.height(100.dp),
                    placeholder = "例如: 127.0.0.1/32"
                )
            }
        }

        // --- 端口转发配置 ---
        CollapsibleConfigSection(title = "端口转发") {
            // Display existing port forward rules
            activeConfig.portForwards.forEachIndexed { index, item ->
                PortForwardRow(
                    item = item,
                    onItemChange = { updatedItem ->
                        val newList = activeConfig.portForwards.toMutableList()
                        newList[index] = updatedItem
                        onConfigChange(activeConfig.copy(portForwards = newList))
                    },
                    onDeleteItem = {
                        val newList = activeConfig.portForwards.toMutableList()
                        newList.removeAt(index)
                        onConfigChange(activeConfig.copy(portForwards = newList))
                    },
                    isDeleteEnabled = !isRunning
                )
                if (index < activeConfig.portForwards.size - 1) {
                    Divider(Modifier.padding(vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            // Add new rule button
            Button(
                onClick = {
                    val newList = activeConfig.portForwards + PortForwardItem()
                    onConfigChange(activeConfig.copy(portForwards = newList))
                },
                enabled = !isRunning
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加")
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("添加转发规则")
            }
        }

        // --- 删除配置按钮 ---
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除") },
                text = { Text("您确定要删除配置 '${activeConfig.instanceName}' 吗？此操作无法撤销。") },
                confirmButton = {
                    Button(
                        { onDeleteConfig(activeConfig); showDeleteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                },
                dismissButton = { OutlinedButton({ showDeleteDialog = false }) { Text("取消") } }
            )
        }
    }
}


@Composable
fun PortForwardRow(
    item: PortForwardItem,
    onItemChange: (PortForwardItem) -> Unit,
    onDeleteItem: () -> Unit,
    isDeleteEnabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(item.proto.uppercase())
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择协议")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("TCP") },
                        onClick = { onItemChange(item.copy(proto = "tcp")); expanded = false })
                    DropdownMenuItem(
                        text = { Text("UDP") },
                        onClick = { onItemChange(item.copy(proto = "udp")); expanded = false })
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDeleteItem, enabled = isDeleteEnabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除规则",
                    tint = if (isDeleteEnabled) MaterialTheme.colorScheme.error else Color.Gray
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = item.bindIp,
                onValueChange = { onItemChange(item.copy(bindIp = it)) },
                label = { Text("本地IP") },
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = item.bindPort?.toString() ?: "",
                onValueChange = { onItemChange(item.copy(bindPort = it.toIntOrNull())) },
                label = { Text("端口") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = item.dstIp,
                onValueChange = { onItemChange(item.copy(dstIp = it)) },
                label = { Text("目标IP") },
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = item.dstPort?.toString() ?: "",
                onValueChange = { onItemChange(item.copy(dstPort = it.toIntOrNull())) },
                label = { Text("端口") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

@Composable
fun CollapsibleConfigSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = true,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions
    )
}

@Composable
fun ConfigSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}