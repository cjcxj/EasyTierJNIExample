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
        _activeConfig.value = _allConfigs.value.find { it.id == activeId } ?: _allConfigs.value.first()
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
        if (isRunning) return
        val configToml = generateTomlConfig(_activeConfig.value)
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
            NetworkInfoParser.extractRawEventStrings(fullJsonString, _activeConfig.value.instanceName)
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
                val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "日志已成功导出", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write logs to file", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- 工具函数 ---

    private fun generateTomlConfig(data: ConfigData): String {
        val listenersFormatted = data.listeners.lines().filter { it.isNotBlank() }.joinToString(separator = ",\n    ") { "\"$it\"" }
        val peersFormatted = data.peers.lines().filter { it.isNotBlank() }.joinToString(separator = "\n") { "[[peer]]\nuri = \"$it\"" }
        val ipv4ConfigLine = if (data.ipv4.isNotBlank() && !data.dhcp) "ipv4 = \"${data.ipv4}\"" else ""

        return """
            hostname = "${data.hostname}"
            instance_name = "${data.instanceName}"
            instance_id = "53dccdcf-9f9b-4062-a62c-8ec97f3bac0e"
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
}