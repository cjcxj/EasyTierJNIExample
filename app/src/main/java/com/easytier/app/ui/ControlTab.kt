package com.easytier.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.app.ConfigData
import com.easytier.app.PortForwardItem
import com.easytier.app.ui.ConfigSwitchWithInlineHelp
import com.easytier.jni.EasyTierManager

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
    const val P2P_ONLY = "仅与已建立 P2P 连接的对端通信。"
    const val LAZY_P2P =
        "仅当确实需要与某对端通信时才尝试建立 P2P；标记为 need_p2p 的对端仍会主动连接。"
    const val NEED_P2P =
        "声明其他节点应主动与本节点建立 P2P 连接（即使它们启用了 lazy_p2p）。"
    const val DISABLE_TCP_HOLE_PUNCHING = "禁用 TCP 打洞。"
    const val DISABLE_RELAY_QUIC = "禁用中转 QUIC 数据包，避免消耗过多带宽。"
    const val ENABLE_RELAY_FOREIGN_NETWORK_QUIC = "允许中转来自外部网络的 QUIC 数据包。"
    const val DISABLE_UPNP = "禁用运行时 UPnP/NAT-PMP 端口映射。"
    const val DISABLE_RELAY_DATA = "禁止本节点为其他节点中转数据流量。"
    const val ENABLE_UDP_BROADCAST_RELAY =
        "捕获物理接口的本地 UDP 广播包并转发给 EasyTier 对端（便于局域网游戏发现房间，Windows 专用，需管理员权限）。"
    const val ENCRYPTION_ALGORITHM =
        "加密算法，支持 ''、'xor'、'chacha20'、'aes-gcm'、'aes-gcm-256'、'openssl-aes128-gcm'、'openssl-aes256-gcm'、'openssl-chacha20'。留空表示默认（aes-gcm）。"
    const val IPV6_ADDR =
        "此VPN节点的IPv6地址，可与IPv4一起使用以进行双栈操作。例如：fd00::1/64"
    const val STUN_SERVERS =
        "覆盖内置的默认 STUN server 列表；如果设置了但是为空，则不使用 STUN servers；如果没设置，则使用默认 STUN server 列表"
    const val STUN_SERVERS_V6 =
        "覆盖内置的默认 IPv6 STUN server 列表；如果设置了但是为空，则不使用 IPv6 STUN servers；如果没设置，则使用默认 IPv6 STUN server 列表"
    const val SECURE_MODE =
        "如果为true，则启用安全模式。默认值为false"
    const val LOCAL_PRIVATE_KEY =
        "安全模式下的本地私钥。如果未提供，则会随机生成一个密钥"
    const val LOCAL_PUBLIC_KEY =
        "安全模式下的本地公钥。如果未提供，则会随机生成一个密钥，或者使用本地私钥派生公钥"
    const val RELAY_ALL_RPC =
        "允许转发所有对等节点的 RPC 数据包，即使它们不在转发网络白名单中。有助于白名单外的节点建立 P2P 连接。"
    const val PROXY_FORWARD_BY_SYSTEM = "通过操作系统内核（而非内置 NAT）来转发子网代理的数据包。"
    const val USE_SMOLTCP =
        "使用用户态 TCP/IP 协议栈，可绕过某些操作系统防火墙限制，改善子网代理或 KCP 代理的兼容性。"
    const val MANUAL_ROUTES_HELP =
        "手动分配路由CIDR，将禁用子网代理和从对等节点传播的wireguard路由。例如：192.168.0.0/16"
    const val SOCKS5_SERVER_HELP =
        "启用 socks5 服务器，允许 socks5 客户端访问虚拟网络. 格式: <端口>，例如：1080"
    const val PORT_FORWARD_HELP =
        "将本地端口转发到虚拟网络中的远程端口。例如：udp://0.0.0.0:12345/10.126.126.1:23456，表示将本地UDP端口12345转发到虚拟网络中的10.126.126.1:23456。可以指定多个。"
    const val DISABLE_RELAY_KCP = "禁用中转 KCP 数据包。"
    const val ENABLE_RELAY_FOREIGN_NETWORK_KCP = "允许中转来自外部网络的 KCP 数据包。"
    const val DATA_COMPRESS_ALGO = "数据压缩算法，留空=默认(aes-gcm)，可选: none, zstd"
    const val MULTI_THREAD_COUNT = "多线程模式下的线程数，默认为2。"
    const val TLD_DNS_ZONE = "Magic DNS 的 TLD 域名区域，例如 'et' 表示通过 'hostname.et' 访问。"
    const val CREDENTIAL_FILE = "凭证文件路径，用于无密钥认证的对等节点凭证。"
    const val IPV6_PUBLIC_ADDR_PROVIDER = "启用 IPv6 公网地址提供者，为其他节点分配 IPv6 公网地址。"
    const val IPV6_PUBLIC_ADDR_AUTO = "自动获取 IPv6 公网地址前缀。"
    const val IPV6_PUBLIC_ADDR_PREFIX = "IPv6 公网地址前缀，例如 'fd00:1234::/48'。"

    // --- 文本字段帮助文本 (对齐上游 app.yml) ---
    const val INSTANCE_NAME = "用于在同一机器上标识此 VPN 节点的实例名称。"
    const val NETWORK_NAME = "用于标识此 VPN 网络的网络名称。"
    const val NETWORK_SECRET = "网络密钥，用于验证此节点属于该 VPN 网络。留空在安全模式下可启用凭据模式。"
    const val PEERS = "初始连接的对等节点，每行一个。例如：tcp://public.easytier.top:11010"
    const val DHCP = "自动确定并设置 IP 地址。EasyTier 会从网络中分配一个可用的虚拟 IP。"
    const val VIRTUAL_IPV4 = "此 VPN 节点的 IPv4 地址。例如：10.0.0.1/24"
    const val HOSTNAME = "用于标识此设备的主机名，可用于 Magic DNS 解析。留空则自动获取系统主机名。"
    const val LISTENERS = "监听器列表，每行一个。格式：协议://地址:端口。例如：tcp://0.0.0.0:11010、udp://0.0.0.0:11010、wg://0.0.0.0:11011"
    const val DEV_NAME = "可选的 TUN 接口名称。留空则自动生成。"
    const val MTU = "TUN 接口的 MTU 值。留空使用默认值 1380。"
    const val PROXY_NETWORKS = "子网代理的 CIDR 网络，每行一个。可选映射到另一 CIDR。例如：10.147.223.0/24"
    const val DEFAULT_PROTOCOL = "连接对等节点时使用的默认协议。可选：tcp、udp、ws、wss、quic。默认 tcp。"
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
 * @param status 当前 EasyTier 运行状态（用于 Hero 卡片显示虚拟 IP），可为 null。
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
    status: EasyTierManager.EasyTierStatus?,
    onControlButtonClick: () -> Unit,
    onExportConfig: (Uri) -> Unit,
    onImportConfig: (Uri) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // --- 文件导出器 (创建文件) ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/toml"),
        onResult = { uri: Uri? ->
            uri?.let {
                onExportConfig(it)
                // 可以在 ViewModel 中处理成功提示
            }
        }
    )


    // --- 文件导入器 (打开文件) ---
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                onImportConfig(it)
            }
        }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Hero 状态卡（顶部） ---
        HeroStatusCard(
            isRunning = isRunning,
            instanceName = activeConfig.instanceName,
            virtualIp = status?.currentIpv4 ?: activeConfig.virtualIpv4,
            controlEnabled = activeConfig.instanceName.isNotBlank(),
            onControlButtonClick = onControlButtonClick
        ) {
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
                    Divider() // --- 分隔线，让导入导出更清晰 ---

                    DropdownMenuItem(
                        text = { Text("导入配置") },
                        onClick = {
                            // 启动文件选择器来打开文件
                            importLauncher.launch(arrayOf("application/toml", "text/plain", "*/*"))
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.FileOpen, "导入") }
                    )
                    DropdownMenuItem(
                        text = { Text("导出当前配置") },
                        onClick = {
                            // 推荐的文件名，用户可以修改
                            val fileName = "${activeConfig.instanceName}.toml"
                            // 启动文件选择器来创建文件
                            exportLauncher.launch(fileName)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.IosShare, "导出") }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // --- 核心配置 ---
        CollapsibleConfigSection(title = "核心配置", icon = Icons.Default.Settings, initiallyExpanded = true) {
            ConfigTextField(
                "实例名",
                activeConfig.instanceName,
                { onConfigChange(activeConfig.copy(instanceName = it)) },
                enabled = !isRunning,
                helpText = HelpTexts.INSTANCE_NAME
            )
            ConfigTextField(
                "网络名",
                activeConfig.networkName,
                { onConfigChange(activeConfig.copy(networkName = it)) },
                enabled = !isRunning,
                helpText = HelpTexts.NETWORK_NAME
            )
            ConfigTextField(
                "网络密钥",
                activeConfig.networkSecret,
                { onConfigChange(activeConfig.copy(networkSecret = it)) },
                enabled = !isRunning,
                helpText = HelpTexts.NETWORK_SECRET
            )
            ConfigTextField(
                label = "对等节点 (Peers, 每行一个)",
                value = activeConfig.peers,
                onValueChange = { onConfigChange(activeConfig.copy(peers = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(120.dp),
                helpText = HelpTexts.PEERS
            )
        }

        // --- IP & 接口配置 ---
        CollapsibleConfigSection(title = "IP 与接口", icon = Icons.Default.Language) {
            // DHCP 开关
            ConfigSwitchWithInlineHelp(
                label = "自动分配IP (DHCP)",
                checked = activeConfig.dhcp,
                onCheckedChange = { dhcpEnabled ->
                    // 启用DHCP时清空静态IP，禁用时不做改变让用户手动输入
                    onConfigChange(
                        if (dhcpEnabled) activeConfig.copy(dhcp = true)
                        else activeConfig.copy(dhcp = false)
                    )
                },
                helpText = HelpTexts.DHCP,
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
            Text(
                text = HelpTexts.VIRTUAL_IPV4,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            ConfigTextField(
                "主机名",
                activeConfig.hostname,
                { onConfigChange(activeConfig.copy(hostname = it)) },
                enabled = !isRunning,
                placeholder = "留空则自动获取",
                helpText = HelpTexts.HOSTNAME
            )
            ConfigTextField(
                label = "监听器(每行一个)",
                value = activeConfig.listenerUrls,
                onValueChange = { onConfigChange(activeConfig.copy(listenerUrls = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(100.dp),
                helpText = HelpTexts.LISTENERS
            )
            ConfigTextField(
                label = "映射监听器(每行一个)",
                value = activeConfig.mappedListeners,
                onValueChange = { onConfigChange(activeConfig.copy(mappedListeners = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(100.dp),
                placeholder = "手动指定监听器的公网地址，其他节点可以使用该地址连接到本节点。",
                helpText = "手动指定监听器的公网地址（含端口），用于 NAT 后的环境。每行一个，格式与监听器相同。"
            )
            ConfigTextField(
                "TUN设备名 (dev_name)",
                activeConfig.devName,
                { onConfigChange(activeConfig.copy(devName = it)) },
                enabled = !isRunning,
                placeholder = "留空则自动",
                helpText = HelpTexts.DEV_NAME
            )
            ConfigTextField(
                "MTU",
                activeConfig.mtu,
                { onConfigChange(activeConfig.copy(mtu = it)) },
                enabled = !isRunning,
                placeholder = "TUN设备的MTU，默认为非加密时为1380，加密时为1360。范围：400-1380",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                helpText = HelpTexts.MTU
            )

        }

        // --- 功能标志 (Flags) ---
        CollapsibleConfigSection(title = "功能标志 (Flags)", icon = Icons.Default.Flag) {
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
                        "启用KCP",
                        activeConfig.enableKcpProxy,
                        { onConfigChange(activeConfig.copy(enableKcpProxy = it)) },
                        HelpTexts.ENABLE_KCP,
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
                        "允许作为出口",
                        activeConfig.enableExitNode,
                        { onConfigChange(activeConfig.copy(enableExitNode = it)) },
                        HelpTexts.ENABLE_EXIT_NODE,
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
                        "使用多线程",
                        activeConfig.multiThread,
                        { onConfigChange(activeConfig.copy(multiThread = it)) },
                        HelpTexts.MULTI_THREAD,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "仅P2P",
                        activeConfig.p2pOnly,
                        { onConfigChange(activeConfig.copy(p2pOnly = it)) },
                        HelpTexts.P2P_ONLY,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "懒加载P2P",
                        activeConfig.lazyP2p,
                        { onConfigChange(activeConfig.copy(lazyP2p = it)) },
                        HelpTexts.LAZY_P2P,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "需要P2P",
                        activeConfig.needP2p,
                        { onConfigChange(activeConfig.copy(needP2p = it)) },
                        HelpTexts.NEED_P2P,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "禁用TCP打洞",
                        activeConfig.disableTcpHolePunching,
                        { onConfigChange(activeConfig.copy(disableTcpHolePunching = it)) },
                        HelpTexts.DISABLE_TCP_HOLE_PUNCHING,
                        !isRunning
                    )
                }
                Column(Modifier.weight(1f)) {
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
                        "绑定设备",
                        activeConfig.bindDevice,
                        { onConfigChange(activeConfig.copy(bindDevice = it)) },
                        HelpTexts.BIND_DEVICE,
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
                        "禁用对称NAT打洞",
                        activeConfig.disableSymHolePunching,
                        { onConfigChange(activeConfig.copy(disableSymHolePunching = it)) },
                        HelpTexts.DISABLE_SYM_HOLE_PUNCHING,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "使用SmolTCP",
                        activeConfig.useSmoltcp,
                        { onConfigChange(activeConfig.copy(useSmoltcp = it)) },
                        HelpTexts.USE_SMOLTCP,
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
                        "禁用UPnP",
                        activeConfig.disableUpnp,
                        { onConfigChange(activeConfig.copy(disableUpnp = it)) },
                        HelpTexts.DISABLE_UPNP,
                        !isRunning
                    )
                    ConfigSwitchWithInlineHelp(
                        "UDP广播转发",
                        activeConfig.enableUdpBroadcastRelay,
                        { onConfigChange(activeConfig.copy(enableUdpBroadcastRelay = it)) },
                        HelpTexts.ENABLE_UDP_BROADCAST_RELAY,
                        !isRunning
                    )
                }
            }
        }
        // --- 高级路由配置 ---
        CollapsibleConfigSection(title = "高级路由", icon = Icons.Default.AltRoute) {
            ConfigTextField(
                label = "代理子网",
                value = activeConfig.proxyNetworks,
                onValueChange = { onConfigChange(activeConfig.copy(proxyNetworks = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(100.dp),
                placeholder = "子网代理CIDR,每行一个",
                helpText = HelpTexts.PROXY_NETWORKS
            )
            ConfigSwitchWithInlineHelp(
                "启用自定义路由",
                activeConfig.enableManualRoutes,
                { onConfigChange(activeConfig.copy(enableManualRoutes = it)) },
                HelpTexts.MANUAL_ROUTES_HELP,
                enabled = !isRunning
            )
            ConfigTextField(
                "自定义路由",
                activeConfig.routes,
                { onConfigChange(activeConfig.copy(routes = it)) },
                enabled = !isRunning && activeConfig.enableManualRoutes,
                singleLine = false,
                modifier = Modifier.height(120.dp),
                placeholder = "例如: 192.168.0.0/16",
                helpText = HelpTexts.MANUAL_ROUTES_HELP
            )
            ConfigTextField(
                label = "出口节点 (Exit Nodes)",
                value = activeConfig.exitNodes,
                onValueChange = { onConfigChange(activeConfig.copy(exitNodes = it)) },
                enabled = !isRunning,
                singleLine = false,
                modifier = Modifier.height(100.dp),
                placeholder = "每行一个虚拟 IPv4 地址",
                helpText = "转发所有流量的出口节点列表（虚拟 IPv4 地址），优先级由列表顺序决定。"
            )
        }
        // --- IPv6 公网地址 ---
        CollapsibleConfigSection(title = "IPv6 公网地址", icon = Icons.Default.Public) {
            ConfigSwitchWithInlineHelp(
                "自动获取IPv6前缀",
                activeConfig.ipv6PublicAddrAuto,
                { onConfigChange(activeConfig.copy(ipv6PublicAddrAuto = it)) },
                HelpTexts.IPV6_PUBLIC_ADDR_AUTO,
                enabled = !isRunning
            )
        }
        // --- 服务与门户 ---
        CollapsibleConfigSection(title = "服务与门户", icon = Icons.Default.Dns) {
            ConfigSwitchWithInlineHelp(
                "启用SOCKS5代理",
                activeConfig.enableSocks5,
                { onConfigChange(activeConfig.copy(enableSocks5 = it)) },
                HelpTexts.SOCKS5_SERVER_HELP,
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
            ConfigSwitchWithInlineHelp(
                "启用VPN门户",
                activeConfig.enableVpnPortal,
                { onConfigChange(activeConfig.copy(enableVpnPortal = it)) },
                "启用基于 WireGuard 协议的 VPN 门户，允许其他 WireGuard 客户端接入此虚拟网络。",
                enabled = !isRunning
            )
            ConfigTextField(
                "VPN门户客户端网段",
                activeConfig.vpnPortalClientNetworkAddr,
                { onConfigChange(activeConfig.copy(vpnPortalClientNetworkAddr = it)) },
                enabled = !isRunning && activeConfig.enableVpnPortal,
                placeholder = "例如: 10.14.14.0",
                helpText = "分配给 WireGuard 客户端的网段地址（不含掩码），默认 10.14.14.0。"
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
        }

        // --- 端口转发配置 ---
        CollapsibleConfigSection(title = "端口转发", icon = Icons.Default.MultipleStop) {
            // 添加帮助文本说明
            Text(
                text = HelpTexts.PORT_FORWARD_HELP,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

/**
 * 顶部 Hero 状态卡：渐变背景 + 脉冲状态点 + 实例/IP 信息 + 全宽启停按钮（带 Crossfade）。
 * [configMenu] 为右侧配置菜单槽（IconButton + DropdownMenu）。
 */
@Composable
fun HeroStatusCard(
    isRunning: Boolean,
    instanceName: String,
    virtualIp: String,
    controlEnabled: Boolean,
    onControlButtonClick: () -> Unit,
    configMenu: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroPulseAlpha"
    )
    val controlInteractionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (isRunning)
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        else
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                    ),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                            .then(
                                if (isRunning) Modifier.background(
                                    Color(0xFF4CAF50).copy(alpha = pulseAlpha),
                                    CircleShape
                                )
                                else Modifier
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = instanceName.ifBlank { "未命名实例" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (virtualIp.isNotBlank()) virtualIp else "未分配 IP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    configMenu()
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onControlButtonClick,
                    enabled = controlEnabled,
                    interactionSource = controlInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressableScale(interactionSource = controlInteractionSource),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Crossfade(
                        targetState = isRunning,
                        animationSpec = tween(300),
                        label = "controlBtnCrossfade"
                    ) { running ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(
                                if (running) "停止服务" else "启动服务",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
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
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = if (expanded)
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            else
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    helpText: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        if (!helpText.isNullOrBlank()) {
            Text(
                text = helpText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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