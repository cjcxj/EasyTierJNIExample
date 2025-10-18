package com.easytier.app

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.easytier.jni.EasyTierJNI
import com.easytier.jni.EasyTierManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var easyTierManager: EasyTierManager? = null

    // --- Data class for UI state ---
    data class ConfigData(
        val hostname: String = "Android-Device",
        val instanceName: String = "cjcxj-easytier",
        val ipv4: String = "10.0.0.4/24",
        val dhcp: Boolean = false,
        val listeners: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011",
        val rpcPortal: String = "0.0.0.0:0",
        val networkName: String = "cjcxj-easytier",
        val networkSecret: String = "dc230d4b-6702-4a65-87b4-26a06f3684b5",
        val peers: String = "tcp://175.178.155.56:11010",
        val enableKcpProxy: Boolean = true,
        val enableQuicProxy: Boolean = true,
        val latencyFirst: Boolean = true,
        val privateMode: Boolean = true
    )

    // --- UI State ---
    private val configDataState = mutableStateOf(ConfigData())
    private val statusState = mutableStateOf<EasyTierManager.EasyTierStatus?>(null)
    private val isRunningState = derivedStateOf { statusState.value?.isRunning == true }
    private val detailedInfoState = mutableStateOf<DetailedNetworkInfo?>(null)

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "VPN权限已授予。")
                startEasyTier()
            } else {
                Log.w(TAG, "VPN权限被拒绝。")
                Toast.makeText(this, "VPN权限被拒绝，无法启动服务", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    configData = configDataState.value,
                    onConfigChange = { newConfig -> configDataState.value = newConfig },
                    status = statusState.value,
                    isRunning = isRunningState.value,
                    onControlButtonClick = {
                        if (isRunningState.value) {
                            stopEasyTier()
                        } else {
                            requestVpnPermission()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEasyTier()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.i(TAG, "正在请求VPN权限...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.i(TAG, "VPN权限已授予，直接启动。")
            startEasyTier()
        }
    }

    private fun generateTomlConfig(data: ConfigData): String {
        val listenersFormatted = data.listeners.lines()
            .filter { it.isNotBlank() }
            .joinToString(separator = ",\n    ") { "\"$it\"" }

        val peersFormatted = data.peers.lines()
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n") { "[[peer]]\nuri = \"$it\"" }

        return """
            hostname = "${data.hostname}"
            instance_name = "${data.instanceName}"
            instance_id = "53dccdcf-9f9b-4062-a62c-8ec97f3bac0e"
            ipv4 = "${data.ipv4}"
            dhcp = ${data.dhcp}
            listeners = [
                $listenersFormatted
            ]
            rpc_portal = "${data.rpcPortal}"

            [network_identity]
            network_name = "${data.networkName}"
            network_secret = "${data.networkSecret}"

            $peersFormatted

            [flags]
            enable_kcp_proxy = ${data.enableKcpProxy}
            enable_quic_proxy = ${data.enableQuicProxy}
            latency_first = ${data.latencyFirst}
            private_mode = ${data.privateMode}
        """.trimIndent()
    }

    private fun startEasyTier() {
        if (isRunningState.value) {
            Log.w(TAG, "EasyTier 已在运行中。")
            return
        }
        val config = generateTomlConfig(configDataState.value)
        Log.d(TAG, "生成的配置:\n$config")

        easyTierManager = EasyTierManager(
            activity = this,
            instanceName = configDataState.value.instanceName,
            networkConfig = config
        )
        easyTierManager?.start()
    }

    private fun stopEasyTier() {
        easyTierManager?.stop()
        easyTierManager = null
        statusState.value = EasyTierManager.EasyTierStatus(
            false,
            configDataState.value.instanceName,
            null,
            emptyList()
        )
    }

    private suspend fun refreshDetailedInfo(showToast: Boolean = false) {
        // 如果服务未运行，则清除旧信息并返回
        if (easyTierManager == null || !isRunningState.value) {
            detailedInfoState.value = null
            if (showToast) {
                Toast.makeText(this, "服务未运行，无法刷新", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val jsonString = EasyTierJNI.collectNetworkInfos(10)
        if (jsonString != null) {
            try {
                val instanceName = configDataState.value.instanceName
                detailedInfoState.value = parseJsonToDetailedInfo(jsonString, instanceName)
                if (showToast) {
                    Toast.makeText(this@MainActivity, "详细信息已刷新", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析详细网络信息失败", e)
                if (showToast) {
                    Toast.makeText(
                        this@MainActivity,
                        "解析信息失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            if (showToast) {
                Toast.makeText(this@MainActivity, "获取网络信息失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Composable UI ---

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(
        configData: ConfigData,
        onConfigChange: (ConfigData) -> Unit,
        status: EasyTierManager.EasyTierStatus?,
        isRunning: Boolean,
        onControlButtonClick: () -> Unit
    ) {
        LaunchedEffect(Unit) {
            while (true) {
                statusState.value = easyTierManager?.getStatus()
                delay(1000L)
            }
        }

        // 【新增】用于自动刷新详细信息的 LaunchedEffect
        LaunchedEffect(isRunning) {
            // 当服务正在运行时 (isRunning is true)
            if (isRunning) {
                // 进入一个无限循环，直到这个Effect被取消（例如当 isRunning 变为 false）
                while (isActive) { // isActive 是协程的一个属性，当协程被取消时变为 false
                    refreshDetailedInfo(showToast = false) // 调用刷新，但不显示Toast
                    delay(5000L) // 等待5秒
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("EasyTier VPN 控制面板") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onControlButtonClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isRunning) "停止服务" else "启动服务", fontSize = 18.sp)
                }
                Spacer(Modifier.height(16.dp))

                StatusCard(status = status, isRunning = isRunning)
                Spacer(Modifier.height(16.dp))

                ConfigSection(title = "基本设置") {
                    ConfigTextField(
                        "主机名 (hostname)",
                        configData.hostname,
                        { onConfigChange(configData.copy(hostname = it)) },
                        isRunning
                    )
                    ConfigTextField(
                        "实例名 (instance_name)",
                        configData.instanceName,
                        { onConfigChange(configData.copy(instanceName = it)) },
                        isRunning
                    )
                }

                ConfigSection(title = "网络身份") {
                    ConfigTextField(
                        "网络名 (network_name)",
                        configData.networkName,
                        { onConfigChange(configData.copy(networkName = it)) },
                        isRunning
                    )
                    ConfigTextField(
                        "网络密钥 (network_secret)",
                        configData.networkSecret,
                        { onConfigChange(configData.copy(networkSecret = it)) },
                        isRunning
                    )
                }

                ConfigSection(title = "IP 设置") {
                    ConfigTextField(
                        "虚拟 IPv4 (ipv4)",
                        configData.ipv4,
                        { onConfigChange(configData.copy(ipv4 = it)) },
                        isRunning || configData.dhcp,
                        placeholder = "例如: 10.0.0.4/24"
                    )
                    ConfigSwitch(
                        "自动分配IP (dhcp)",
                        configData.dhcp,
                        { onConfigChange(configData.copy(dhcp = it)) },
                        isRunning
                    )
                }

                ConfigSection(title = "连接设置") {
                    ConfigTextField(
                        "对等节点 (每行一个)",
                        configData.peers,
                        { onConfigChange(configData.copy(peers = it)) },
                        isRunning,
                        singleLine = false,
                        modifier = Modifier.height(100.dp)
                    )
                    ConfigTextField(
                        "监听器 (每行一个)",
                        configData.listeners,
                        { onConfigChange(configData.copy(listeners = it)) },
                        isRunning,
                        singleLine = false,
                        modifier = Modifier.height(120.dp)
                    )
                }

                ConfigSection(title = "功能标志") {
                    ConfigSwitch(
                        "延迟优先",
                        configData.latencyFirst,
                        { onConfigChange(configData.copy(latencyFirst = it)) },
                        isRunning
                    )
                    ConfigSwitch(
                        "私有模式",
                        configData.privateMode,
                        { onConfigChange(configData.copy(privateMode = it)) },
                        isRunning
                    )
                    ConfigSwitch(
                        "启用 KCP 代理",
                        configData.enableKcpProxy,
                        { onConfigChange(configData.copy(enableKcpProxy = it)) },
                        isRunning
                    )
                    ConfigSwitch(
                        "启用 QUIC 代理",
                        configData.enableQuicProxy,
                        { onConfigChange(configData.copy(enableQuicProxy = it)) },
                        isRunning
                    )
                }

                DetailedInfoCard(
                    info = detailedInfoState.value,
                    // 【修改】手动刷新按钮现在会调用新的 suspend 函数
                    onRefresh = {
                        // 手动刷新时，我们需要在协程中调用，并显示Toast
                        lifecycleScope.launch {
                            refreshDetailedInfo(showToast = true)
                        }
                    }
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    @Composable
    fun StatusCard(status: EasyTierManager.EasyTierStatus?, isRunning: Boolean) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("状态信息", style = MaterialTheme.typography.titleLarge)
                Divider(Modifier.padding(vertical = 8.dp))
                StatusRow("服务状态:", if (isRunning) "运行中" else "已停止")
                StatusRow("实例名称:", status?.instanceName ?: "暂无")
                StatusRow("虚拟 IPv4:", status?.currentIpv4 ?: "暂无")
                StatusRow(
                    "代理路由:",
                    if (status?.currentProxyCidrs.isNullOrEmpty()) "无"
                    else status!!.currentProxyCidrs.joinToString("\n")
                )

                Spacer(Modifier.height(16.dp))

                val clipboardManager = LocalClipboardManager.current
                val context = LocalContext.current
                Button(
                    onClick = {
                        try {
                            val networkInfoJson = EasyTierJNI.collectNetworkInfos(10)
                            if (!networkInfoJson.isNullOrBlank()) {
                                clipboardManager.setText(AnnotatedString(networkInfoJson))
                                Toast.makeText(
                                    context,
                                    "网络信息 (JSON) 已复制",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "无网络信息可复制", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "collectNetworkInfos 调用失败", e)
                            Toast.makeText(context, "获取信息失败: ${e.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isRunning
                ) {
                    Text("复制网络信息 (JSON)")
                }
            }
        }
    }

    // --- Helper Composables ---

    @Composable
    fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Divider(Modifier.padding(vertical = 4.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }

    @Composable
    fun ConfigTextField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        readOnly: Boolean,
        modifier: Modifier = Modifier,
        singleLine: Boolean = true,
        placeholder: String = ""
    ) {
        OutlinedTextField(
            value,
            onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            readOnly = readOnly,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )
    }

    @Composable
    fun ConfigSwitch(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        readOnly: Boolean
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked, onCheckedChange, enabled = !readOnly)
        }
    }

    @Composable
    fun StatusRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.6f)
            )
        }
    }

    //详细信息卡片的 Composable
    @Composable
    fun DetailedInfoCard(info: DetailedNetworkInfo?, onRefresh: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("详细网络状态", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))

                if (info == null) {
                    Text(
                        "点击右上角刷新按钮获取详细信息。",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                } else {
                    // --- 本机信息部分 ---
                    InfoSection(title = "本机信息") {
                        InfoRow("主机名:", info.myNode.hostname)
                        InfoRow("版本:", info.myNode.version)
                        InfoRow("虚拟IPv4:", info.myNode.virtualIp)
                    }

                    InfoSection(title = "STUN探测信息") {
                        InfoRow("公网 IP:", info.myNode.publicIp)
                        InfoRow("NAT 类型:", info.myNode.natType)
                    }

                    InfoSection(title = "监听器") {
                        // 使用可组合的 Column 来显示多行文本
                        Text(
                            text = info.myNode.listeners.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp, // 增加行高以提高可读性
                        )
                    }

                    InfoSection(title = "接口IP地址") {
                        Text(
                            text = info.myNode.interfaceIps.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- 对等节点信息部分---
                    Text(
                        "对等节点 (${info.finalPeerList.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.heightIn(max = 400.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(info.finalPeerList) { peer ->
                                FinalPeerInfoItem(peer) // 调用新的 Item Composable
                            }
                        }
                    }

                    // --- 事件日志部分---
                    Spacer(Modifier.height(16.dp))
                    Text("事件日志", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        LazyColumn {
                            items(info.events) { event ->
                                Text(
                                    text = "[${event.time}] ${event.message}",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 辅助 Composable 来创建带标题的段落，使UI更整洁
     */
    @Composable
    fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            content()
        }
    }

    /**
     * 显示最终节点信息的可组合函数
     *
     * @param peer 包含节点信息的数据对象，用于展示节点的详细信息
     */
    @Composable
    fun FinalPeerInfoItem(peer: FinalPeerInfo) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = peer.hostname,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!peer.isDirectConnection) {
                        Text(
                            text = "中转",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Divider(Modifier.padding(vertical = 4.dp))
                InfoRow("虚拟 IP:", peer.virtualIp)
                InfoRow(
                    if (peer.isDirectConnection) "物理地址:" else "下一跳:",
                    peer.connectionDetails
                )
                InfoRow("延迟:", peer.latency)
                InfoRow("流量 (收/发):", peer.traffic)
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}


// 顶层数据容器
data class DetailedNetworkInfo(
    val myNode: MyNodeInfo,
    val events: List<EventInfo>,
    val finalPeerList: List<FinalPeerInfo> // 最终组合后的对等节点列表
)

// 本机信息
data class MyNodeInfo(
    val hostname: String,
    val version: String,
    val virtualIp: String,
    val publicIp: String,
    val natType: String,
    val listeners: List<String>,
    val interfaceIps: List<String>
)

// 事件信息
data class EventInfo(
    val time: String,
    val message: String
)

// 路由信息 (从 "routes" 解析)
data class RouteData(
    val peerId: Long,
    val hostname: String,
    val virtualIp: String,
    val nextHopPeerId: Long,
    val pathLatency: Int,
    val cost: Int
)

// 直连信息 (从 "peers" 解析)
data class PeerConnectionData(
    val peerId: Long,
    val physicalAddr: String,
    val latencyUs: Long,
    val rxBytes: Long,
    val txBytes: Long
)

// 最终展示给UI的、组合后的对等节点信息
data class FinalPeerInfo(
    val hostname: String,
    val virtualIp: String,
    val isDirectConnection: Boolean,
    val connectionDetails: String, // 用于显示物理地址或下一跳
    val latency: String,
    val traffic: String // 用于显示流量或 N/A
)

/**
 * 将整数形式的IP地址转换为点分十进制字符串。
 */
fun parseIntegerToIp(addr: Int): String {
    return String.format(
        "%d.%d.%d.%d",
        (addr ushr 24) and 0xFF,
        (addr ushr 16) and 0xFF,
        (addr ushr 8) and 0xFF,
        addr and 0xFF
    )
}

/**
 * 格式化字节大小为可读的 KB, MB, GB。
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
}

/**
 * 解析 UDP NAT 类型代码为可读的字符串。
 */
fun parseNatType(typeCode: Int): String {
    return when (typeCode) {
        // NatType.Unknown
        0 -> "Unknown (未知类型)"
        // NatType.OpenInternet
        1 -> "Open Internet (开放互联网)" // P2P 最佳
        // NatType.NoPAT
        2 -> "No PAT (无端口转换)"
        // NatType.FullCone
        3 -> "Full Cone (完全锥形)" // P2P 很好
        // NatType.Restricted
        4 -> "Restricted Cone (限制锥形)" // P2P 较好
        // NatType.PortRestricted
        5 -> "Port Restricted (端口限制锥形)" // P2P 一般
        // NatType.Symmetric
        6 -> "Symmetric (对称型)" // P2P 困难
        // NatType.SymUdpFirewall
        7 -> "Symmetric UDP Firewall (对称UDP防火墙)"
        // NatType.SymmetricEasyInc
        8 -> "Symmetric Easy Inc (对称型-端口递增)"
        // NatType.SymmetricEasyDec
        9 -> "Symmetric Easy Dec (对称型-端口递减)"
        // 其他未知代码
        else -> "Other Type ($typeCode)"
    }
}

/**
 * 将给定的 JSON 字符串解析为详细的网络信息结构。
 *
 * 首先解析 JSON 数据中的指定实例部分，然后分别提取 my_node_info、events、routes 和 peers 信息，
 * 最后根据路由表和直连信息组装出完整的对等节点列表，并返回包含所有信息的 [DetailedNetworkInfo] 对象。
 *
 * @param jsonString 包含网络信息的完整 JSON 字符串
 * @param instanceName 要解析的具体实例名称，用于从 JSON 的 "map" 中获取对应数据
 * @return 包含当前节点信息、事件列表和最终对等节点列表的 [DetailedNetworkInfo] 实例
 */
fun parseJsonToDetailedInfo(jsonString: String, instanceName: String): DetailedNetworkInfo {
    val root = JSONObject(jsonString)
    val instance = root.getJSONObject("map").getJSONObject(instanceName)

    // --- 独立解析各个部分 ---
    val myNode = parseMyNodeInfo(instance.getJSONObject("my_node_info"))
    val events = parseEvents(instance.getJSONArray("events"))
    val routesMap = parseRoutes(instance.getJSONArray("routes"))
    val peersMap = parsePeers(instance.getJSONArray("peers"))

    // --- 组装最终的对等节点列表 ---
    val finalPeerList = mutableListOf<FinalPeerInfo>()
    // 以路由表为准，遍历所有已知的节点
    routesMap.values.forEach { route ->
        val peerConn = peersMap[route.peerId] // 尝试查找该节点的直连信息

        if (peerConn != null) {
            // --- 情况A: 找到了直连信息 ---
            finalPeerList.add(
                FinalPeerInfo(
                    hostname = route.hostname,
                    virtualIp = route.virtualIp,
                    isDirectConnection = true,
                    connectionDetails = peerConn.physicalAddr,
                    latency = "${peerConn.latencyUs / 1000} ms",
                    traffic = "${formatBytes(peerConn.rxBytes)} / ${formatBytes(peerConn.txBytes)}"
                )
            )
        } else {
            // --- 情况B: 未找到直连信息，说明是中转 ---
            val nextHopHostname = routesMap[route.nextHopPeerId]?.hostname ?: "未知"
            finalPeerList.add(
                FinalPeerInfo(
                    hostname = route.hostname,
                    virtualIp = route.virtualIp,
                    isDirectConnection = false,
                    connectionDetails = "通过 $nextHopHostname",
                    latency = "${route.pathLatency} ms (路径)",
                    traffic = "N/A"
                )
            )
        }
    }

    return DetailedNetworkInfo(
        myNode = myNode,
        events = events,
        finalPeerList = finalPeerList.sortedBy { it.hostname }
    )
}

// -- 解析 "my_node_info" --
private fun parseMyNodeInfo(myNodeJson: JSONObject): MyNodeInfo {
    val myStunInfoJson = myNodeJson.getJSONObject("stun_info")
    val ipsJson = myNodeJson.getJSONObject("ips")

    val virtualIpv4Json = myNodeJson.getJSONObject("virtual_ipv4")
    val virtualIpAddr = parseIntegerToIp(virtualIpv4Json.getJSONObject("address").getInt("addr"))
    val virtualIpPrefix = virtualIpv4Json.getInt("network_length")
    val virtualIp = "$virtualIpAddr/$virtualIpPrefix"

    val listenersArray = myNodeJson.getJSONArray("listeners")
    val listenersList = (0 until listenersArray.length()).map {
        listenersArray.getJSONObject(it).getString("url")
    }

    val interfaceIpsArray = ipsJson.getJSONArray("interface_ipv4s")
    val interfaceIpsList = (0 until interfaceIpsArray.length()).map {
        parseIntegerToIp(interfaceIpsArray.getJSONObject(it).getInt("addr"))
    }

    val publicIpsArray = myStunInfoJson.getJSONArray("public_ip")
    val publicIpsStr = if (publicIpsArray.length() > 0) {
        (0 until publicIpsArray.length()).joinToString(", ") { publicIpsArray.getString(it) }
    } else {
        "N/A"
    }

    return MyNodeInfo(
        hostname = myNodeJson.getString("hostname"),
        version = myNodeJson.getString("version"),
        virtualIp = virtualIp,
        publicIp = publicIpsStr,
        natType = parseNatType(myStunInfoJson.getInt("udp_nat_type")),
        listeners = listenersList,
        interfaceIps = interfaceIpsList
    )
}

// -- 解析 "events" --
private fun parseEvents(eventsJson: org.json.JSONArray): List<EventInfo> {
    val eventList = mutableListOf<EventInfo>()
    for (i in 0 until eventsJson.length()) {
        val eventStr = eventsJson.getString(i)
        val eventJson = JSONObject(eventStr)
        val time = eventJson.getString("time").substring(11, 19) // 提取 H:M:S
        val message =
            eventJson.getJSONObject("event").toString().replace("\"", "").replace(":", ": ")
                .replace(",", ", ")
        eventList.add(EventInfo(time, message))
    }
    return eventList.take(20) // 最多显示最近20条
}

// -- 解析 "routes" --
private fun parseRoutes(routesJson: org.json.JSONArray): Map<Long, RouteData> {
    val routesMap = mutableMapOf<Long, RouteData>()
    for (i in 0 until routesJson.length()) {
        val route = routesJson.getJSONObject(i)
        val peerId = route.getLong("peer_id")

        val ipv4AddrJson = route.optJSONObject("ipv4_addr")
        val virtualIp = if (ipv4AddrJson != null) {
            parseIntegerToIp(ipv4AddrJson.getJSONObject("address").getInt("addr"))
        } else {
            "无虚拟IP"
        }

        routesMap[peerId] = RouteData(
            peerId = peerId,
            hostname = route.getString("hostname"),
            virtualIp = virtualIp,
            nextHopPeerId = route.getLong("next_hop_peer_id"),
            pathLatency = route.getInt("path_latency"),
            cost = route.getInt("cost")
        )
    }
    return routesMap
}

// -- 解析 "peers" --
private fun parsePeers(peersJson: org.json.JSONArray): Map<Long, PeerConnectionData> {
    val peersMap = mutableMapOf<Long, PeerConnectionData>()
    for (i in 0 until peersJson.length()) {
        val peer = peersJson.getJSONObject(i)
        val conns = peer.getJSONArray("conns")
        if (conns.length() > 0) {
            val conn = conns.getJSONObject(0) // 只取第一个连接
            val peerId = conn.getLong("peer_id")

            peersMap[peerId] = PeerConnectionData(
                peerId = peerId,
                physicalAddr = conn.getJSONObject("tunnel").getJSONObject("remote_addr")
                    .getString("url"),
                latencyUs = conn.getJSONObject("stats").getLong("latency_us"),
                rxBytes = conn.getJSONObject("stats").getLong("rx_bytes"),
                txBytes = conn.getJSONObject("stats").getLong("tx_bytes")
            )
        }
    }
    return peersMap
}
