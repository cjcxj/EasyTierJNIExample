package com.easytier.app.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val settingsRepository = SettingsRepository(application)
    private var easyTierManager: EasyTierManager? = null

    val configData = mutableStateOf(ConfigData())
    val status = mutableStateOf<EasyTierManager.EasyTierStatus?>(null)
    val isRunning = derivedStateOf { status.value?.isRunning == true }
    val detailedInfo = mutableStateOf<DetailedNetworkInfo?>(null)

    init {
        viewModelScope.launch {
            configData.value = settingsRepository.loadConfig()
        }
        viewModelScope.launch {
            while (true) {
                status.value = easyTierManager?.getStatus()
                if (isRunning.value) {
                    refreshDetailedInfo(false)
                }
                delay(1000)
            }
        }
    }

    fun onConfigChange(newConfig: ConfigData) {
        configData.value = newConfig
        viewModelScope.launch {
            settingsRepository.saveConfig(newConfig)
        }
    }

    fun startEasyTier(activity: MainActivity) {
        if (isRunning.value) {
            Log.w(TAG, "EasyTier is already running.")
            return
        }
        val configToml = generateTomlConfig(configData.value)
        Log.d(TAG, "Generated Config:\n$configToml")

        easyTierManager = EasyTierManager(
            activity = activity,
            instanceName = configData.value.instanceName,
            networkConfig = configToml
        )
        easyTierManager?.start()
    }

    fun stopEasyTier() {
        easyTierManager?.stop()
        easyTierManager = null
        status.value = EasyTierManager.EasyTierStatus(
            false,
            configData.value.instanceName,
            null,
            emptyList()
        )
    }

    private fun refreshDetailedInfo(showToast: Boolean) {
        if (easyTierManager == null || !isRunning.value) {
            detailedInfo.value = null
            if (showToast) Toast.makeText(getApplication(), "Service not running", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonString = EasyTierJNI.collectNetworkInfos(10)
        if (jsonString != null) {
            try {
                val instanceName = configData.value.instanceName
                detailedInfo.value = NetworkInfoParser.parse(jsonString, instanceName)
                if (showToast) Toast.makeText(getApplication(), "Detailed info refreshed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse detailed network info", e)
                if (showToast) Toast.makeText(getApplication(), "Failed to parse info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (showToast) Toast.makeText(getApplication(), "Failed to get network info", Toast.LENGTH_SHORT).show()
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
            ""
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
}