package com.easytier.app.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.State

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val settingsRepository = SettingsRepository(application)

    private var easyTierManager: EasyTierManager? = null

    // 状态变量
    private val _allConfigs = mutableStateOf<List<ConfigData>>(emptyList())
    val allConfigs: State<List<ConfigData>> = _allConfigs

    private val _activeConfig = mutableStateOf(ConfigData())
    val activeConfig: State<ConfigData> = _activeConfig
    val status = mutableStateOf<EasyTierManager.EasyTierStatus?>(null)
    val isRunning = derivedStateOf { status.value?.isRunning == true }
    val detailedInfo = mutableStateOf<DetailedNetworkInfo?>(null)

    init {
        // 启动时加载配置
        viewModelScope.launch {
            loadAllConfigs()
        }

        // 启动轮询，获取状态和详细信息
        viewModelScope.launch {
            while (true) {
                // 直接从 manager 获取状态
                status.value = easyTierManager?.getStatus()
                if (isRunning.value) {
                    // 如果在运行，则刷新详细信息（静默刷新）
                    refreshDetailedInfo(false)
                }
                delay(2000) // 轮询间隔2秒
            }
        }
    }

    /**
     * 当UI层的配置发生变化时调用
     */

    private suspend fun loadAllConfigs() {
        val configs = settingsRepository.getAllConfigs()
        _allConfigs.value = configs
        val activeId = settingsRepository.getActiveConfigId()
        _activeConfig.value = configs.find { it.id == activeId } ?: configs.first()
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
        // Ensure the new name is unique
        while (existingNames.contains(newInstanceName)) {
            newInstanceName = "easytier-$i"
            i
        }

        val newConfig = ConfigData(instanceName = newInstanceName)
        val currentList = _allConfigs.value.toMutableList()
        currentList.add(newConfig)
        _allConfigs.value = currentList
        // Automatically switch to the new config
        setActiveConfig(newConfig)
        // Save all configs
        viewModelScope.launch {
            settingsRepository.saveAllConfigs(currentList)
        }
    }

    fun saveConfig(config: ConfigData) {
        val currentList = _allConfigs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == config.id }
        if (index != -1) {
            currentList[index] = config // Update existing
        } else {
            currentList.add(config) // Add new
        }
        _allConfigs.value = currentList
        viewModelScope.launch {
            settingsRepository.saveAllConfigs(currentList)
        }
    }

    fun deleteConfig(config: ConfigData) {
        val currentList = _allConfigs.value.toMutableList()
        if (currentList.size <= 1) return // Do not delete the last one
        currentList.remove(config)
        _allConfigs.value = currentList
        if (_activeConfig.value.id == config.id) {
            setActiveConfig(currentList.first()) // Switch to first if active was deleted
        }
        viewModelScope.launch {
            settingsRepository.saveAllConfigs(currentList)
        }
    }

    /**
     * 封装启动/停止按钮的决策逻辑，由UI层调用
     */
    fun handleControlButtonClick(activity: MainActivity) {
        if (isRunning.value) {
            stopEasyTier()
        } else {
            // 启动流程需要权限，所以委托给Activity处理
            activity.requestVpnPermission()
        }
    }

    /**
     * 在获得VPN权限后，由Activity调用此方法来真正启动服务
     */
    fun startEasyTier(activity: ComponentActivity) {
        if (isRunning.value) {
            Log.w(TAG, "EasyTier is already running.")
            return
        }

        // 使用 _activeConfig.value 来生成配置
        val configToml = generateTomlConfig(_activeConfig.value)
        Log.d(TAG, "Generated Config:\n$configToml")

        // 创建 EasyTierManager 实例
        easyTierManager = EasyTierManager(
            activity = activity,
            instanceName = _activeConfig.value.instanceName, // 使用 _activeConfig.value
            networkConfig = configToml
        )
        easyTierManager?.start() // 启动服务
    }

    /**
     * 停止服务
     */
    fun stopEasyTier() {
        easyTierManager?.stop()
        easyTierManager = null
        // 立即更新UI状态以获得即时反馈
        status.value = null
        detailedInfo.value = null
    }

    fun manualRefreshDetailedInfo() {
        viewModelScope.launch {
            refreshDetailedInfo(showToast = true)
        }
    }

    /**
     * 刷新详细网络信息（带Toast的可选开关）
     */
    fun refreshDetailedInfo(showToast: Boolean) {
        viewModelScope.launch {
            // 在调用前再次检查，确保服务仍在运行
            if (easyTierManager == null || !isRunning.value) {
                detailedInfo.value = null
                if (showToast) Toast.makeText(getApplication(), "服务未运行", Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }

            // 在后台线程获取和解析JSON，避免阻塞UI
            val info = withContext(Dispatchers.IO) {
                try {
                    // 通过 manager 获取 JSON 字符串
                    val jsonString = EasyTierJNI.collectNetworkInfos(10)
                    if (jsonString != null) {
                        NetworkInfoParser.parse(jsonString, _activeConfig.value.instanceName)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse detailed network info", e)
                    // 如果需要显示Toast，必须切回主线程
                    if (showToast) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "解析信息失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    null
                }
            }

            // 在主线程更新UI状态
            detailedInfo.value = info
            if (showToast && info != null) {
                Toast.makeText(getApplication(), "详细信息已刷新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 根据 ConfigData 生成 TOML 格式的配置字符串
     */
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
            ""
        }

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