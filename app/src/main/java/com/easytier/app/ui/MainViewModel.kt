package com.easytier.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easytier.app.ConfigData
import com.easytier.app.MainActivity
import com.easytier.app.SettingsRepository
import com.easytier.jni.DetailedNetworkInfo
import com.easytier.jni.EasyTierJNI
import com.easytier.jni.EasyTierManager
import com.easytier.jni.EventInfo
import com.easytier.jni.NetworkInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val settingsRepository = SettingsRepository(application)
    private var easyTierManager: EasyTierManager? = null

    // --- 状态变量 ---
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _allConfigs = mutableStateOf<List<ConfigData>>(emptyList())
    val allConfigs: State<List<ConfigData>> = _allConfigs

    private val _activeConfig = mutableStateOf(ConfigData())
    val activeConfig: State<ConfigData> = _activeConfig

    private val _statusState = mutableStateOf<EasyTierManager.EasyTierStatus?>(null)
    val statusState: State<EasyTierManager.EasyTierStatus?> = _statusState

    private val _detailedInfoState = mutableStateOf<DetailedNetworkInfo?>(null)
    val detailedInfoState: State<DetailedNetworkInfo?> = _detailedInfoState

    private val _fullEventHistory = mutableStateOf<List<EventInfo>>(emptyList())

    // 只存储完整的、原始的事件JSON字符串
    private val _fullRawEventHistory = mutableStateOf<List<String>>(emptyList())
    val fullRawEventHistory: State<List<String>> = _fullRawEventHistory

    val isRunning: Boolean
        get() = _statusState.value?.isRunning == true

    init {
        viewModelScope.launch {
            loadAllConfigs()
        }
        viewModelScope.launch {
            while (true) {
                _statusState.value = easyTierManager?.getStatus()
                if (isRunning) {
                    // 在循环中，同时刷新快照和收集新日志
                    refreshDetailedInfoSnapshot(false)
                    collectNewEvents()
                }
                delay(2000)
            }
        }
    }

    // --- 配置管理 ---

    private suspend fun loadAllConfigs() {
        val configs = settingsRepository.getAllConfigs()
        _allConfigs.value = if (configs.isEmpty()) listOf(ConfigData()) else configs
        val activeId = settingsRepository.getActiveConfigId()
        _activeConfig.value =
            _allConfigs.value.find { it.id == activeId } ?: _allConfigs.value.first()
    }

    fun setActiveConfig(config: ConfigData) {
        _activeConfig.value = config
        viewModelScope.launch {
            settingsRepository.setActiveConfigId(config.id)
        }
    }

    fun updateConfig(newConfig: ConfigData) {
        _activeConfig.value = newConfig
        val currentList = _allConfigs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == newConfig.id }
        if (index != -1) {
            currentList[index] = newConfig
            _allConfigs.value = currentList
            viewModelScope.launch {
                settingsRepository.saveAllConfigs(currentList)
            }
        }
    }

    fun addNewConfig() {
        val existingNames = _allConfigs.value.map { it.instanceName }
        var newInstanceName = "easytier-${_allConfigs.value.size + 1}"
        var i = 2
        while (existingNames.contains(newInstanceName)) {
            newInstanceName = "easytier-$i"
            i++
        }
        val newConfig = ConfigData(instanceName = newInstanceName)
        _allConfigs.value = _allConfigs.value + newConfig
        setActiveConfig(newConfig)
        viewModelScope.launch {
            settingsRepository.saveAllConfigs(_allConfigs.value)
        }
    }

    fun deleteConfig(config: ConfigData) {
        val currentConfigs = _allConfigs.value

        // 保护逻辑：不允许删除最后一个配置
        if (currentConfigs.size <= 1) {
            viewModelScope.launch { _toastEvents.emit("无法删除最后一个配置") }
            return
        }

        // 1在删除之前，记录下被删除项的索引
        val deletedIndex = currentConfigs.indexOfFirst { it.id == config.id }

        // 如果由于某种原因没找到，则不执行任何操作
        if (deletedIndex == -1) {
            Log.w(TAG, "Attempted to delete a config that does not exist in the list.")
            return
        }

        // 创建一个新的、不包含被删除项的列表
        val newList = currentConfigs.filterNot { it.id == config.id }
        _allConfigs.value = newList

        // 只有当被删除的配置是当前激活的配置时，才需要重新设置激活配置
        if (_activeConfig.value.id == config.id) {
            // 决定下一个激活项的索引
            // 如果被删除的是第一个，新的激活项就是新的第一个（索引仍为0）。
            // 否则，新的激活项就是被删除项的前一个（索引为 deletedIndex - 1）。
            val nextActiveIndex = (deletedIndex - 1).coerceAtLeast(0)

            // 从新列表中获取下一个要激活的配置并设置
            setActiveConfig(newList[nextActiveIndex])
        }

        // 将更新后的完整列表保存到 DataStore
        viewModelScope.launch {
            settingsRepository.saveAllConfigs(newList)
        }
    }

    // --- 服务生命周期 ---

    fun handleControlButtonClick(activity: MainActivity) {
        if (isRunning) {
            stopEasyTier()
        } else {
            activity.requestVpnPermission()
        }
    }

    fun startEasyTier(activity: ComponentActivity) {
        if (isRunning) {
            Log.w(TAG, "EasyTier is already running.")
            return
        }

        val configToml = generateTomlConfig(_activeConfig.value)
        Log.d(TAG, "Generated Config:\n$configToml")

        val timeStamp = java.time.OffsetDateTime.now().toString()


        val tomlLogEntry = JSONObject()
            .put("time", timeStamp)
            .put(
                "event",
                JSONObject().put(
                    "GeneratedTomlConfig",
                    "\n--- 使用的TOML配置 ---\n$configToml\n-----------------------------"
                )
            )
            .toString()

        _fullRawEventHistory.value = listOf(tomlLogEntry)
        _fullEventHistory.value = emptyList()

        easyTierManager = EasyTierManager(
            activity = activity,
            instanceName = _activeConfig.value.instanceName,
            networkConfig = configToml
        )
        easyTierManager?.start()
    }

    fun stopEasyTier() {
        easyTierManager?.stop()
        easyTierManager = null
        _statusState.value = null
        _detailedInfoState.value = null
        _fullEventHistory.value = emptyList() // 清空UI用的日志
        _fullRawEventHistory.value = emptyList() //清空原始日志历史
    }

    override fun onCleared() {
        super.onCleared()
        stopEasyTier()
    }

    // --- 数据获取与导出 ---

    fun manualRefreshDetailedInfo() {
        viewModelScope.launch {
            refreshDetailedInfoSnapshot(true)
        }
    }

    private suspend fun refreshDetailedInfoSnapshot(showToast: Boolean) {
        if (easyTierManager == null || !isRunning) {
            _detailedInfoState.value = null
            if (showToast) viewModelScope.launch { _toastEvents.emit("服务未运行") }
            return
        }
        val info = withContext(Dispatchers.IO) {
            try {
                val jsonString = EasyTierJNI.collectNetworkInfos(10)
                if (jsonString != null) {
                    NetworkInfoParser.parse(jsonString, _activeConfig.value.instanceName)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse detailed info snapshot", e)
                if (showToast) viewModelScope.launch { _toastEvents.emit("解析信息失败: ${e.message}") }
                null
            }
        }
        _detailedInfoState.value = info
        if (showToast && info != null) {
            viewModelScope.launch { _toastEvents.emit("详细信息已刷新") }
        }
    }

    private suspend fun collectNewEvents() {
        if (!isRunning) return

        val fullJsonString = getRawJsonForClipboard() ?: return

        // 1. 直接从快照中提取原始事件字符串列表
        val snapshotRawEvents = withContext(Dispatchers.Default) {
            NetworkInfoParser.extractRawEventStrings(
                fullJsonString,
                _activeConfig.value.instanceName
            )
        }

        if (snapshotRawEvents.isEmpty()) return

        // 2. 使用原始字符串进行高效去重
        val currentHistory = _fullRawEventHistory.value
        val lastKnownEvent = currentHistory.lastOrNull()

        val newRawEventsToAdd = if (lastKnownEvent != null) {
            val lastIndex = snapshotRawEvents.lastIndexOf(lastKnownEvent)
            if (lastIndex != -1) {
                snapshotRawEvents.subList(lastIndex + 1, snapshotRawEvents.size)
            } else {
                Log.w(TAG, "Log buffer wrap-around detected. History may have gaps.")
                snapshotRawEvents // 缓冲区轮转，接受所有新日志
            }
        } else {
            snapshotRawEvents // 首次收集
        }

        // 3. 如果有新事件，直接添加到历史记录
        if (newRawEventsToAdd.isNotEmpty()) {
            _fullRawEventHistory.value = currentHistory + newRawEventsToAdd
        }
    }

    suspend fun getRawJsonForClipboard(): String? {
        if (easyTierManager == null || !isRunning) return null
        return withContext(Dispatchers.IO) {
            try {
                EasyTierJNI.collectNetworkInfos(10)
            } catch (e: Exception) {
                Log.e(TAG, "getRawJsonForClipboard failed", e)
                null
            }
        }
    }

    fun copyJsonToClipboard() {
        viewModelScope.launch {
            val jsonString = getRawJsonForClipboard()
            if (!jsonString.isNullOrBlank()) {
                val clipboard =
                    getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Network Info JSON", jsonString)
                clipboard.setPrimaryClip(clip)
                _toastEvents.emit("JSON 已复制到剪贴板")
            } else {
                _toastEvents.emit("获取信息失败")
            }
        }
    }

    /**
     * 获取用于“导出”功能的、格式化后的人类可读日志字符串。
     * 这个方法的数据源是 _fullEventHistory，确保了日志的完整性。
     */
    fun getFormattedLogsForExport(): String? {
        // 从 _fullEventHistory 获取完整的、不断累积的事件列表
        val events = _fullEventHistory.value

        if (events.isEmpty()) {
            return null // 如果没有历史记录，返回 null
        }

        // 将 EventInfo 对象列表格式化为一个单一的、多行的字符串
        return events.joinToString(separator = "\n") { event ->
            "[${event.time}] [${event.level}] ${event.message}"
        }
    }

    /**
     * 导出原始事件的方法
     */
    suspend fun getRawEventsJsonForExport(): String? {
        val rawEvents = _fullRawEventHistory.value
        if (rawEvents.isEmpty()) return null

        // 直接将原始字符串列表拼接成一个格式化的JSON数组字符串
        return withContext(Dispatchers.Default) {
            rawEvents.joinToString(
                separator = ",\n    ",
                prefix = "[\n    ",
                postfix = "\n]"
            )
        }
    }

    fun writeContentToUri(uri: Uri, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?.use { it.write(content.toByteArray()) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "日志已成功导出", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write logs to file", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "导出失败: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    // --- 工具函数 ---

    private fun generateTomlConfig(data: ConfigData): String {
        val sb = StringBuilder()

        // --- 辅助函数 ---
        fun appendIf(condition: Boolean, text: String) {
            if (condition) sb.appendLine(text)
        }

        fun appendString(key: String, value: String) {
            if (value.isNotBlank()) sb.appendLine("$key = \"$value\"")
        }

        fun appendStringList(key: String, value: String) {
            if (value.isNotBlank()) {
                val formatted =
                    value.lines().filter { it.isNotBlank() }.joinToString(", ") { "\"$it\"" }
                sb.appendLine("$key = [$formatted]")
            }
        }

        // --- 1. 顶级键值对 ---
        appendString("hostname", data.hostname)
        appendString("instance_name", data.instanceName)
        sb.appendLine("instance_id = \"${data.id}\"") // 总是输出
        if (data.dhcp) {
            sb.appendLine("dhcp = true")
        } else {
            sb.appendLine("dhcp = false")
            appendString("ipv4", data.ipv4)
        }
        appendStringList("listeners", data.listeners)
        appendStringList("mapped_listeners", data.mappedListeners)
        appendStringList("exit_nodes", data.exitNodes)
        appendString("rpc_portal", data.rpcPortal)
        appendStringList("rpc_portal_whitelist", data.rpcPortalWhitelist)
        appendStringList("routes", data.routes)
        appendString("socks5_proxy", data.socks5Proxy)

        // --- 2. [network_identity] ---
        sb.appendLine("\n[network_identity]")
        appendString("network_name", data.networkName)
        appendString("network_secret", data.networkSecret)

        // --- 3. [[peer]] ---
        data.peers.lines().filter { it.isNotBlank() }.forEach {
            sb.appendLine("\n[[peer]]")
            sb.appendLine("uri = \"$it\"")
        }

        // --- 4. [[proxy_network]] ---
        data.proxyNetworks.lines().filter { it.isNotBlank() }.forEach {
            sb.appendLine("\n[[proxy_network]]")
            sb.appendLine("cidr = \"$it\"")
        }

        // --- 5. [vpn_portal_config] ---
        if (data.vpnPortalClientCidr.isNotBlank() || data.vpnPortalWgListen.isNotBlank()) {
            sb.appendLine("\n[vpn_portal_config]")
            appendString("client_cidr", data.vpnPortalClientCidr)
            appendString("wireguard_listen", data.vpnPortalWgListen)
        }

        // --- 6. [[port_forward]] ---
        data.portForwards.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(',').map { it.trim() }
            if (parts.size == 3) {
                sb.appendLine("\n[[port_forward]]")
                sb.appendLine("bind_addr = \"${parts[0]}\"")
                sb.appendLine("dst_addr = \"${parts[1]}\"")
                sb.appendLine("proto = \"${parts[2]}\"")
            }
        }

        val flagLines = mutableListOf<String>()
        val defaultFlags = ConfigData() // 获取一个包含所有默认值的实例

        // --- 逐一比较并添加与默认值不同的 flag ---

        // 字符串类型
        if (data.devName.isNotBlank()) {
            flagLines.add("dev_name = \"${data.devName}\"")
        }
        if (data.relayNetworkWhitelist != defaultFlags.relayNetworkWhitelist) {
            flagLines.add("relay_network_whitelist = \"${data.relayNetworkWhitelist}\"")
        }

        // 布尔类型 (与默认值不同才输出)
        if (data.acceptDns != defaultFlags.acceptDns) {
            flagLines.add("accept_dns = ${data.acceptDns}")
        }
        if (data.disableKcpInput != defaultFlags.disableKcpInput) {
            flagLines.add("disable_kcp_input = ${data.disableKcpInput}")
        }
        if (data.disableP2p != defaultFlags.disableP2p) {
            flagLines.add("disable_p2p = ${data.disableP2p}")
        }
        if (data.disableQuicInput != defaultFlags.disableQuicInput) {
            flagLines.add("disable_quic_input = ${data.disableQuicInput}")
        }
        if (data.disableSymHolePunching != defaultFlags.disableSymHolePunching) {
            flagLines.add("disable_sym_hole_punching = ${data.disableSymHolePunching}")
        }
        if (data.disableUdpHolePunching != defaultFlags.disableUdpHolePunching) {
            flagLines.add("disable_udp_hole_punching = ${data.disableUdpHolePunching}")
        }
        if (data.enableEncryption != defaultFlags.enableEncryption) {
            flagLines.add("enable_encryption = ${data.enableEncryption}")
        }
        if (data.enableExitNode != defaultFlags.enableExitNode) {
            flagLines.add("enable_exit_node = ${data.enableExitNode}")
        }
        if (data.enableIpv6 != defaultFlags.enableIpv6) {
            flagLines.add("enable_ipv6 = ${data.enableIpv6}")
        }
        if (data.enableKcpProxy != defaultFlags.enableKcpProxy) {
            flagLines.add("enable_kcp_proxy = ${data.enableKcpProxy}")
        }
        if (data.enableQuicProxy != defaultFlags.enableQuicProxy) {
            flagLines.add("enable_quic_proxy = ${data.enableQuicProxy}")
        }
        if (data.latencyFirst != defaultFlags.latencyFirst) {
            flagLines.add("latency_first = ${data.latencyFirst}")
        }
        if (data.noTun != defaultFlags.noTun) {
            flagLines.add("no_tun = ${data.noTun}")
        }
        if (data.privateMode != defaultFlags.privateMode) {
            flagLines.add("private_mode = ${data.privateMode}")
        }
        if (data.proxyForwardBySystem != defaultFlags.proxyForwardBySystem) {
            flagLines.add("proxy_forward_by_system = ${data.proxyForwardBySystem}")
        }
        if (data.relayAllPeerRpc != defaultFlags.relayAllPeerRpc) {
            flagLines.add("relay_all_peer_rpc = ${data.relayAllPeerRpc}")
        }
        if (data.useSmoltcp != defaultFlags.useSmoltcp) {
            flagLines.add("use_smoltcp = ${data.useSmoltcp}")
        }
        // --- 只有当有需要输出的flag时，才生成 [flags] 段落 ---
        if (flagLines.isNotEmpty()) {
            sb.appendLine("\n[flags]")
            flagLines.forEach { sb.appendLine(it) }
        }

        return sb.toString()
    }

}