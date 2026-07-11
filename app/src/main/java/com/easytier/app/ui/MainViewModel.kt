package com.easytier.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.easytier.app.ConfigData
import com.easytier.app.MainActivity
import com.easytier.app.SettingsRepository
import com.easytier.app.TomlConfig
import com.easytier.app.toConfigData
import com.easytier.app.toTomlConfig
import com.easytier.jni.ConfigServerClientManager
import com.easytier.jni.DetailedNetworkInfo
import com.easytier.jni.EasyTierJNI
import com.easytier.jni.EasyTierManager
import com.easytier.jni.EventInfo
import com.easytier.jni.NetworkInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val settingsRepository = SettingsRepository(application)
    private var easyTierManager: EasyTierManager? = null
    private val _dataPlaneClient = mutableStateOf<com.easytier.jni.DataPlaneClient?>(null)
    val dataPlaneClient: State<com.easytier.jni.DataPlaneClient?> = _dataPlaneClient

    private val _configServerManager = mutableStateOf<ConfigServerClientManager?>(null)
    val configServerManager: State<ConfigServerClientManager?> = _configServerManager

    private val _machineId = mutableStateOf("")
    val machineId: State<String> = _machineId

    private val _rpcClient = mutableStateOf<com.easytier.jni.RpcClient?>(null)
    val rpcClient: State<com.easytier.jni.RpcClient?> = _rpcClient

    // 配置服务器推送启动的实例名（非 null 表示有配置服务器启动的实例在运行）
    private val _configServerInstanceName = mutableStateOf<String?>(null)

    // 配置服务器持久化设置
    data class ConfigServerSettings(
        val url: String = "https://config.easytier.cn",
        val hostname: String = "",
        val secureMode: Boolean = false,
        val autoConnect: Boolean = false
    )

    private val _configServerSettings = mutableStateOf(ConfigServerSettings())
    val configServerSettings: State<ConfigServerSettings> = _configServerSettings

    /** 是否由配置服务器控制（配置服务器启动了实例且无本地实例） */
    val isConfigServerControlled: Boolean
        get() = _configServerInstanceName.value != null && easyTierManager == null

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

    /** 当前活跃的实例名：手动启动的优先，否则用配置服务器推送的 */
    private val currentTrackingInstanceName: String?
        get() = when {
            easyTierManager != null && isRunning -> _activeConfig.value.instanceName
            _configServerInstanceName.value != null -> _configServerInstanceName.value
            else -> null
        }

    /** 供 UI 使用的当前运行实例名（RPC Tab 等） */
    val runningInstanceName: String
        get() = currentTrackingInstanceName ?: _activeConfig.value.instanceName

    init {
        viewModelScope.launch {
            loadAllConfigs()
        }
        viewModelScope.launch {
            _machineId.value = settingsRepository.getMachineId()
            val mgr = ConfigServerClientManager()
            _configServerManager.value = mgr
            _rpcClient.value = com.easytier.jni.RpcClient()

            // 加载持久化的配置服务器设置
            val savedUrl = settingsRepository.getConfigServerUrl()
            val savedHostname = settingsRepository.getConfigServerHostname()
            val savedSecureMode = settingsRepository.getConfigServerSecureMode()
            val savedAutoConnect = settingsRepository.getAutoConnectConfigServer()
            _configServerSettings.value = ConfigServerSettings(
                url = savedUrl ?: "https://config.easytier.cn",
                hostname = savedHostname ?: "",
                secureMode = savedSecureMode,
                autoConnect = savedAutoConnect
            )

            // 自动连接配置服务器
            if (savedAutoConnect && !_configServerSettings.value.url.isBlank()) {
                Log.i(TAG, "自动连接配置服务器: ${_configServerSettings.value.url}")
                mgr.start(
                    url = _configServerSettings.value.url,
                    hostname = _configServerSettings.value.hostname.ifBlank { null },
                    machineId = _machineId.value,
                    secureMode = _configServerSettings.value.secureMode
                )
            }

            // 监听配置服务器事件，跟踪实例生命周期
            mgr.events.collect { json ->
                try {
                    val obj = JSONObject(json)
                    val event = obj.optString("event", "")
                    val instName = obj.optString("instance_name", "")
                    val success = obj.optBoolean("success", true)
                    if (instName.isEmpty()) return@collect

                    when (event) {
                        "run_network_instance" -> {
                            if (success) {
                                _configServerInstanceName.value = instName
                                if (!isRunning) {
                                    _dataPlaneClient.value = com.easytier.jni.DataPlaneClient(instName)
                                }
                                _toastEvents.emit("配置服务器启动了实例: $instName")
                            }
                        }
                        "delete_network_instance" -> {
                            if (_configServerInstanceName.value == instName) {
                                _configServerInstanceName.value = null
                                if (easyTierManager == null) {
                                    _dataPlaneClient.value = null
                                    _statusState.value = null
                                    _detailedInfoState.value = null
                                }
                                _toastEvents.emit("配置服务器删除了实例: $instName")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析配置服务器事件失败", e)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                val manualStatus = easyTierManager?.getStatus()
                val csInstance = _configServerInstanceName.value

                when {
                    manualStatus != null && manualStatus.isRunning -> {
                        _statusState.value = manualStatus
                        refreshDetailedInfoSnapshot(false, manualStatus.instanceName)
                        collectNewEvents(manualStatus.instanceName)
                    }
                    csInstance != null -> {
                        // 配置服务器启动的实例
                        _statusState.value = EasyTierManager.EasyTierStatus(
                            isRunning = true,
                            instanceName = csInstance,
                            currentIpv4 = null,
                            currentProxyCidrs = emptyList()
                        )
                        refreshDetailedInfoSnapshot(false, csInstance)
                        collectNewEvents(csInstance)
                    }
                    else -> {
                        if (_statusState.value != null) _statusState.value = null
                    }
                }
                delay(2000)
            }
        }
    }

    // --- 配置服务器设置 ---

    fun saveConfigServerSettings(url: String, hostname: String, secureMode: Boolean, autoConnect: Boolean) {
        _configServerSettings.value = ConfigServerSettings(url, hostname, secureMode, autoConnect)
        viewModelScope.launch {
            settingsRepository.setConfigServerUrl(url)
            settingsRepository.setConfigServerHostname(hostname)
            settingsRepository.setConfigServerSecureMode(secureMode)
            settingsRepository.setAutoConnectConfigServer(autoConnect)
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

    fun exportConfig(uri: Uri) {
        viewModelScope.launch {
            try {
                // 1. 获取当前配置数据
                val configData = activeConfig.value

                // 2. 将 ConfigData 映射为 TomlConfig 并序列化
                val tomlString = generateTomlConfig(configData)

                // 3. 使用 ContentResolver 将字符串写入用户选择的文件
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(tomlString.toByteArray())
                }
                _toastEvents.emit("配置已导出")
            } catch (e: Exception) {
                // 处理错误，例如显示错误消息
                e.printStackTrace()
            }
        }
    }

    // 导入配置
    fun importConfig(uri: Uri) {
        viewModelScope.launch {
            try {
                // 1. 使用 ContentResolver 读取文件内容
                val stringBuilder = StringBuilder()
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).forEachLine {
                        stringBuilder.append(it).append("\n")
                    }
                }
                val tomlString = stringBuilder.toString()
                if (tomlString.isBlank()) {
                    _toastEvents.emit("导入失败：文件为空或无法读取")
                    return@launch
                }


                // 2. 将 TOML 字符串反序列化为 TomlConfig 对象
                val tomlConfig = Toml.decodeFromString<TomlConfig>(tomlString)

                // 3. 将 TomlConfig 对象映射回应用的 ConfigData 对象
                var importedConfig = tomlConfig.toConfigData()

                // --- 4. 集成到现有配置管理逻辑中 ---

                val currentConfigs = _allConfigs.value
                val existingIds = currentConfigs.map { it.id }
                val existingNames = currentConfigs.map { it.instanceName }

                // 检查 ID 冲突：如果导入的 ID 已存在，则生成一个新的 ID
                if (existingIds.contains(importedConfig.id)) {
                    importedConfig = importedConfig.copy(id = UUID.randomUUID().toString())
                }

                // 检查名称冲突：如果名称已存在，则添加后缀，类似 addNewConfig 的逻辑
                var newName = importedConfig.instanceName
                var counter = 2
                while (existingNames.contains(newName)) {
                    newName = "${importedConfig.instanceName}_$counter"
                    counter++
                }
                if (newName != importedConfig.instanceName) {
                    importedConfig = importedConfig.copy(instanceName = newName)
                }

                // 5. 将处理好的新配置添加到列表，并保存
                val newList = currentConfigs + importedConfig
                _allConfigs.value = newList
                setActiveConfig(importedConfig) // 将导入的配置设为当前活动配置

                settingsRepository.saveAllConfigs(newList)

                // 6. 发送成功提示
                _toastEvents.emit("配置 '${importedConfig.instanceName}' 导入成功")

            } catch (e: kotlinx.serialization.SerializationException) {
                _toastEvents.emit("导入失败：文件格式无效")
                Log.e(TAG, "TOML parsing failed", e)
            } catch (e: Exception) {
                _toastEvents.emit("导入失败：发生未知错误")
                Log.e(TAG, "Import failed", e)
            }
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
            networkConfig = configToml,
            vpnDnsServers = _activeConfig.value.vpnDnsServers,
            ipv6Config = _activeConfig.value.ipv6
        )
        easyTierManager?.start()
        _dataPlaneClient.value = com.easytier.jni.DataPlaneClient(_activeConfig.value.instanceName)
    }

    fun stopEasyTier() {
        easyTierManager?.stop()
        easyTierManager = null

        // 同时停止配置服务器启动的实例
        if (_configServerInstanceName.value != null) {
            EasyTierJNI.stopAllInstances()
            _configServerInstanceName.value = null
        }

        _statusState.value = null
        _detailedInfoState.value = null
        _fullEventHistory.value = emptyList()
        _fullRawEventHistory.value = emptyList()
        _dataPlaneClient.value = null
    }

    /** 仅停止配置服务器启动的实例（不影响本地启动的实例） */
    fun stopConfigServerInstance() {
        if (_configServerInstanceName.value == null) return
        EasyTierJNI.stopAllInstances()
        _configServerInstanceName.value = null
        if (easyTierManager == null) {
            _statusState.value = null
            _detailedInfoState.value = null
            _dataPlaneClient.value = null
        }
        _fullEventHistory.value = emptyList()
        _fullRawEventHistory.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        _configServerInstanceName.value = null
        _configServerManager.value?.stop()
        stopEasyTier()
    }

    // --- 数据获取与导出 ---

    fun manualRefreshDetailedInfo() {
        viewModelScope.launch {
            val instName = currentTrackingInstanceName
            if (instName != null) {
                refreshDetailedInfoSnapshot(true, instName)
            }
        }
    }

    private suspend fun refreshDetailedInfoSnapshot(showToast: Boolean, instanceName: String) {
        if (!isRunning) {
            _detailedInfoState.value = null
            if (showToast) viewModelScope.launch { _toastEvents.emit("服务未运行") }
            return
        }
        val info = withContext(Dispatchers.IO) {
            try {
                val jsonString = EasyTierJNI.collectNetworkInfos(10)
                if (jsonString != null) {
                    NetworkInfoParser.parse(jsonString, instanceName)
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

    private suspend fun collectNewEvents(instanceName: String) {
        if (!isRunning) return

        val fullJsonString = getRawJsonForClipboard() ?: return

        // 1. 直接从快照中提取原始事件字符串列表
        val snapshotRawEvents = withContext(Dispatchers.Default) {
            NetworkInfoParser.extractRawEventStrings(
                fullJsonString,
                instanceName
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
        if (!isRunning) return null
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

    /**
     * 将 ConfigData 对象序列化为 TOML 格式的字符串。
     * @param data 包含所有配置信息的 ConfigData 实例。
     * @return 格式化好的 TOML 字符串。
     */
    private fun generateTomlConfig(data: ConfigData): String {
        // 1. 将你的应用数据模型映射到可序列化的数据模型
        val serializableConfig = data.toTomlConfig()

        // 2. 使用 ktoml 库进行序列化
        //    这里的 Toml 实例可以配置，但默认行为通常就足够了
        return Toml.encodeToString(serializableConfig)
    }
}