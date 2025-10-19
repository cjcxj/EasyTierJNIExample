package com.easytier.app.ui

import android.app.Application
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
import com.easytier.jni.NetworkInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val settingsRepository = SettingsRepository(application)
    private var easyTierManager: EasyTierManager? = null

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    // --- 状态变量 ---
    private val _allConfigs = mutableStateOf<List<ConfigData>>(emptyList())
    val allConfigs: State<List<ConfigData>> = _allConfigs

    private val _activeConfig = mutableStateOf(ConfigData())
    val activeConfig: State<ConfigData> = _activeConfig

    private val _statusState = mutableStateOf<EasyTierManager.EasyTierStatus?>(null)
    val statusState: State<EasyTierManager.EasyTierStatus?> = _statusState

    private val _detailedInfoState = mutableStateOf<DetailedNetworkInfo?>(null)
    val detailedInfoState: State<DetailedNetworkInfo?> = _detailedInfoState

    // isRunning 状态由 statusState 派生
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
                    refreshDetailedInfo(false)
                }
                delay(2000)
            }
        }
    }

    // --- 公共方法 ---

    private suspend fun loadAllConfigs() {
        val configs = settingsRepository.getAllConfigs()
        if (configs.isEmpty()) {
            _allConfigs.value = listOf(ConfigData()) // 确保至少有一个默认配置
        } else {
            _allConfigs.value = configs
        }
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
            Toast.makeText(getApplication(), "无法删除最后一个配置", Toast.LENGTH_SHORT).show()
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
    }

    fun manualRefreshDetailedInfo() {
        viewModelScope.launch {
            refreshDetailedInfo(showToast = true)
        }
    }

    private suspend fun refreshDetailedInfo(showToast: Boolean) {
        if (easyTierManager == null || !isRunning) {
            _detailedInfoState.value = null
            if (showToast) Toast.makeText(getApplication(), "服务未运行", Toast.LENGTH_SHORT).show()
            return
        }

        val info = withContext(Dispatchers.IO) {
            try {
                val jsonString = EasyTierJNI.collectNetworkInfos(10)
                if (jsonString != null) {
                    NetworkInfoParser.parse(jsonString, _activeConfig.value.instanceName)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse detailed network info", e)
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "解析信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                null
            }
        }
        _detailedInfoState.value = info
        if (showToast && info != null) {
            Toast.makeText(getApplication(), "详细信息已刷新", Toast.LENGTH_SHORT).show()
        }
    }

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

    fun copyJsonToClipboard() {
        viewModelScope.launch {
            val jsonString = EasyTierJNI.collectNetworkInfos(10)
            if (!jsonString.isNullOrBlank()) {
                _toastEvents.emit("JSON 已复制")
            } else {
                _toastEvents.emit("获取信息失败")
            }
        }
    }
}