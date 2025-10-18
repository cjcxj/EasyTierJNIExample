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
import com.easytier.jni.NetworkInfoParser
import com.easytier.jni.DetailedNetworkInfo
import com.easytier.jni.FinalPeerInfo

// --- Data class for UI state ---
data class ConfigData(
    val hostname: String = "Android-Device",
    val instanceName: String = "easytier",
    val ipv4: String = "",
    val dhcp: Boolean = true,
    val listeners: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011",
    val rpcPortal: String = "0.0.0.0:0",
    val networkName: String = "easytier",
    val networkSecret: String = "",
    val peers: String = "tcp://public.easytier.top:11010",
    val enableKcpProxy: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val latencyFirst: Boolean = false,
    val privateMode: Boolean = false
)

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var easyTierManager: EasyTierManager? = null
    private lateinit var settingsRepository: SettingsRepository

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

        settingsRepository = SettingsRepository(applicationContext)

        // 在 Coroutine 中加载配置
        lifecycleScope.launch {
            configDataState.value = settingsRepository.loadConfig()
        }

        setContent {
            MaterialTheme {
                MainScreen(
                    configData = configDataState.value,
                    onConfigChange = { newConfig ->
                        configDataState.value = newConfig
                        // 每当配置更改时，自动保存
                        lifecycleScope.launch {
                            settingsRepository.saveConfig(newConfig)
                        }
                    },
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

        val ipv4ConfigLine = if (data.ipv4.isNotBlank() && !data.dhcp) {
            "ipv4 = \"${data.ipv4}\""
        } else {
            "" // 如果 ipv4 为空或 dhcp 启用，则不生成此行
        }

        return """
            hostname = "${data.hostname}"
            instance_name = "${data.instanceName}"
            $ipv4ConfigLine
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
        if (easyTierManager == null || !isRunningState.value) {
            detailedInfoState.value = null
            if (showToast) Toast.makeText(this, "服务未运行，无法刷新", Toast.LENGTH_SHORT).show()
            return
        }
        
        val jsonString = EasyTierJNI.collectNetworkInfos(10)
        if (jsonString != null) {
            try {
                val instanceName = configDataState.value.instanceName
                detailedInfoState.value = NetworkInfoParser.parse(jsonString, instanceName)
                if (showToast) Toast.makeText(this@MainActivity, "详细信息已刷新", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "解析详细网络信息失败", e)
                if (showToast) Toast.makeText(this@MainActivity, "解析信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (showToast) Toast.makeText(this@MainActivity, "获取网络信息失败", Toast.LENGTH_SHORT).show()
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
                        placeholder = if (configData.dhcp) "由DHCP自动分配" else "例如: 10.0.0.1/24"
                    )
                    ConfigSwitch(
                        "自动分配IP (dhcp)",
                        configData.dhcp,
                        // 当用户点击开关时，执行更复杂的逻辑
                        { dhcpEnabled ->
                            if (dhcpEnabled) {
                                // 如果启用DHCP，清空ipv4字段
                                onConfigChange(configData.copy(dhcp = true, ipv4 = ""))
                            } else {
                                // 如果禁用DHCP，可以恢复一个默认值，或者保持为空让用户输入
                                onConfigChange(configData.copy(dhcp = false, ipv4 = "10.0.0.1/24"))
                            }
                        },
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