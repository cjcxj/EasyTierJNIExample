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
    val fullEventHistory: State<List<EventInfo>> = _fullEventHistory

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
        if (_allConfigs.value.size <= 1) {
            viewModelScope.launch { _toastEvents.emit("无法删除最后一个配置") }
            return
        }
        _allConfigs.value = _allConfigs.value.filterNot { it.id == config.id }
        if (_activeConfig.value.id == config.id) {
            setActiveConfig(_allConfigs.value.first())
        }
        viewModelScope.launch {
            settingsRepository.saveAllConfigs(_allConfigs.value)
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
        _fullEventHistory.value = emptyList() // 清空历史日志
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
        val fullJsonString = getRawJsonForClipboard() ?: return
        val snapshotEvents = withContext(Dispatchers.Default) {
            NetworkInfoParser.extractAndParseEvents(fullJsonString, _activeConfig.value.instanceName)
        }
        if (snapshotEvents.isEmpty()) return
        val currentHistory = _fullEventHistory.value
        val lastTimestamp = currentHistory.lastOrNull()?.rawTime ?: ""
        val newEventsToAdd = snapshotEvents.filter { it.rawTime > lastTimestamp }
        if (newEventsToAdd.isNotEmpty()) {
            _fullEventHistory.value = currentHistory + newEventsToAdd
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

    suspend fun getRawEventsJsonForExport(): String? {
        val fullJsonString = getRawJsonForClipboard() ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val root = JSONObject(fullJsonString)
                val instance = root.getJSONObject("map").getJSONObject(_activeConfig.value.instanceName)
                val eventsArray = instance.getJSONArray("events")
                eventsArray.toString(4)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract raw events JSON", e)
                null
            }
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