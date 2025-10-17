package com.easytier.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.jni.EasyTierJNI
import com.easytier.jni.EasyTierManager
import kotlinx.coroutines.delay

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
        statusState.value = EasyTierManager.EasyTierStatus(false, configDataState.value.instanceName, null, emptyList())
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
                    ConfigTextField("主机名 (hostname)", configData.hostname, { onConfigChange(configData.copy(hostname = it)) }, isRunning)
                    ConfigTextField("实例名 (instance_name)", configData.instanceName, { onConfigChange(configData.copy(instanceName = it)) }, isRunning)
                }

                ConfigSection(title = "网络身份") {
                    ConfigTextField("网络名 (network_name)", configData.networkName, { onConfigChange(configData.copy(networkName = it)) }, isRunning)
                    ConfigTextField("网络密钥 (network_secret)", configData.networkSecret, { onConfigChange(configData.copy(networkSecret = it)) }, isRunning)
                }

                ConfigSection(title = "IP 设置") {
                    ConfigTextField("虚拟 IPv4 (ipv4)", configData.ipv4, { onConfigChange(configData.copy(ipv4 = it)) }, isRunning || configData.dhcp, placeholder = "例如: 10.0.0.4/24")
                    ConfigSwitch("自动分配IP (dhcp)", configData.dhcp, { onConfigChange(configData.copy(dhcp = it)) }, isRunning)
                }

                ConfigSection(title = "连接设置") {
                    ConfigTextField("对等节点 (每行一个)", configData.peers, { onConfigChange(configData.copy(peers = it)) }, isRunning, singleLine = false, modifier = Modifier.height(100.dp))
                    ConfigTextField("监听器 (每行一个)", configData.listeners, { onConfigChange(configData.copy(listeners = it)) }, isRunning, singleLine = false, modifier = Modifier.height(120.dp))
                }

                ConfigSection(title = "功能标志") {
                    ConfigSwitch("延迟优先", configData.latencyFirst, { onConfigChange(configData.copy(latencyFirst = it)) }, isRunning)
                    ConfigSwitch("私有模式", configData.privateMode, { onConfigChange(configData.copy(privateMode = it)) }, isRunning)
                    ConfigSwitch("启用 KCP 代理", configData.enableKcpProxy, { onConfigChange(configData.copy(enableKcpProxy = it)) }, isRunning)
                    ConfigSwitch("启用 QUIC 代理", configData.enableQuicProxy, { onConfigChange(configData.copy(enableQuicProxy = it)) }, isRunning)
                }

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
                                Toast.makeText(context, "网络信息 (JSON) 已复制", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "无网络信息可复制", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "collectNetworkInfos 调用失败", e)
                            Toast.makeText(context, "获取信息失败: ${e.message}", Toast.LENGTH_LONG).show()
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
    fun ConfigTextField(label: String, value: String, onValueChange: (String) -> Unit, readOnly: Boolean, modifier: Modifier = Modifier, singleLine: Boolean = true, placeholder: String = "") {
        OutlinedTextField(value, onValueChange, label = { Text(label) }, placeholder = { Text(placeholder) }, readOnly = readOnly, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = singleLine, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next))
    }

    @Composable
    fun ConfigSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, readOnly: Boolean) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked, onCheckedChange, enabled = !readOnly)
        }
    }

    @Composable
    fun StatusRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
        }
    }
}