package com.easytier.jni

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 配置服务器客户端管理器。
 *
 * 封装 [EasyTierJNI] 的 startConfigServerClient/stopConfigServerClient/isConfigServerClientConnected，
 * 将远程下发配置事件通过 [events] SharedFlow 暴露，连接状态通过 [connectionState] StateFlow 暴露。
 *
 * 事件 JSON 的具体 schema 由上游定义，此处以原始字符串透传，UI 自行解析展示。
 */
class ConfigServerClientManager {

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    companion object {
        private const val TAG = "ConfigServerClientMgr"
        private const val POLL_INTERVAL = 2000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollJob: Job? = null
    private var callback: ConfigServerEventCallback? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 事件重放缓冲（新订阅者可收到最近的事件）
    private val _events = MutableSharedFlow<String>(replay = 16, extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** 启动配置服务器客户端。返回 0 表示成功提交（不代表已连接）。 */
    fun start(url: String, hostname: String?, machineId: String, secureMode: Boolean): Int {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            Log.w(TAG, "客户端已在运行中")
            return -1
        }

        callback = ConfigServerEventCallback { eventJson ->
            Log.d(TAG, "收到配置服务器事件: $eventJson")
            _events.tryEmit(eventJson)
        }

        val result = try {
            EasyTierJNI.startConfigServerClient(url, hostname, machineId, secureMode, callback)
        } catch (e: Exception) {
            Log.e(TAG, "startConfigServerClient 异常", e)
            -1
        }

        if (result == 0) {
            _connectionState.value = ConnectionState.CONNECTING
            // 启动轮询协程更新连接状态
            pollJob = scope.launch {
                while (isActive) {
                    delay(POLL_INTERVAL)
                    try {
                        val connected = EasyTierJNI.isConfigServerClientConnected()
                        val newState = if (connected) ConnectionState.CONNECTED else ConnectionState.CONNECTING
                        if (_connectionState.value != newState) {
                            _connectionState.value = newState
                            Log.i(TAG, "连接状态变更: $newState")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "查询连接状态异常", e)
                    }
                }
            }
        } else {
            Log.e(TAG, "startConfigServerClient 失败: $result, ${EasyTierJNI.getLastError()}")
            callback = null
        }

        return result
    }

    /** 停止配置服务器客户端。 */
    fun stop() {
        pollJob?.cancel()
        pollJob = null

        try {
            EasyTierJNI.stopConfigServerClient()
        } catch (e: Exception) {
            Log.e(TAG, "stopConfigServerClient 异常", e)
        }

        _connectionState.value = ConnectionState.DISCONNECTED
        callback = null
        Log.i(TAG, "配置服务器客户端已停止")
    }

    /** 当前是否已连接 */
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
}
