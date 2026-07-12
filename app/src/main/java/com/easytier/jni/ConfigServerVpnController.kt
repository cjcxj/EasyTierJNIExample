package com.easytier.jni

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import com.squareup.moshi.Moshi
import com.squareup.wire.WireJsonAdapterFactory
import api.manage.NetworkInstanceRunningInfoMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 远程配置服务器下发实例的 VPN 控制器。
 *
 * 监控指定实例的网络状态，检测到 IPv4/proxy_cidrs 变化时建立 Android VPN 接口，
 * 将 TUN fd 传递给 EasyTier 完成组网。与 [EasyTierManager] 的区别：
 * - 用 [Context] 而非 Activity，适配 FFI 回调场景
 * - 不绑定本地配置参数（DNS/IPv6 留空，走系统回退）
 * - 预检 VPN 权限，未授权时通过 [onVpnAuthRequired] 回调通知上层缓存待启动状态
 */
class ConfigServerVpnController(
    private val context: Context,
    private val instanceName: String,
    private val onVpnAuthRequired: () -> Unit
) {
    companion object {
        private const val TAG = "ConfigServerVpnCtrl"
        private const val MONITOR_INTERVAL = 3000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitorJob: Job? = null

    @Volatile
    private var currentIpv4: String? = null
    private var currentProxyCidrs: List<String> = emptyList()
    private var vpnServiceIntent: Intent? = null

    // 缓存最近一次网络信息，供 retryStartVpn 使用
    @Volatile
    private var lastIpv4: String? = null
    @Volatile
    private var lastProxyCidrs: List<String> = emptyList()

    private val moshi = Moshi.Builder().add(WireJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(NetworkInstanceRunningInfoMap::class.java)

    /** 启动监控协程 */
    fun start() {
        if (monitorJob?.isActive == true) {
            Log.w(TAG, "监控已在运行中")
            return
        }
        monitorJob = scope.launch {
            while (isActive) {
                monitorNetworkStatus()
                delay(MONITOR_INTERVAL)
            }
        }
        Log.i(TAG, "VPN 监控已启动: $instanceName")
    }

    /** 停止监控并关闭 VPN 接口（不停止 EasyTier 实例，由 MainViewModel 负责） */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        stopVpnService()
        currentIpv4 = null
        currentProxyCidrs = emptyList()
        Log.i(TAG, "VPN 监控已停止: $instanceName")
    }

    /** 权限授予后重试启动 VPN（使用最近缓存的网络信息） */
    fun retryStartVpn() {
        val ipv4 = lastIpv4
        if (ipv4 == null) {
            Log.w(TAG, "无缓存网络信息，触发一次监控")
            scope.launch { monitorNetworkStatus() }
            return
        }
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "VPN 权限仍未授予")
            onVpnAuthRequired()
            return
        }
        startVpnService(ipv4, lastProxyCidrs)
    }

    private suspend fun monitorNetworkStatus() {
        try {
            val networkInfo = withContext(Dispatchers.IO) {
                val infosJson = EasyTierJNI.collectNetworkInfos(10)
                if (infosJson.isNullOrEmpty()) {
                    return@withContext null
                }
                val networkInfoMap = parseNetworkInfo(infosJson)
                networkInfoMap?.map?.get(instanceName)
            }

            if (networkInfo == null) {
                Log.d(TAG, "未获取到实例 $instanceName 的网络信息")
                return
            }

            if (!networkInfo.running) {
                Log.w(TAG, "实例未运行: ${networkInfo.error_msg}")
                return
            }

            val newIpv4Inet = networkInfo.my_node_info?.virtual_ipv4
            if (newIpv4Inet == null) {
                Log.w(TAG, "实例 $instanceName 暂无 IPv4")
                return
            }

            val newIpv4 = parseIpv4InetToString(newIpv4Inet) ?: return

            val newProxyCidrs = mutableListOf<String>()
            networkInfo.routes?.forEach { route ->
                route.proxy_cidrs?.let { cidrs -> newProxyCidrs.addAll(cidrs) }
            }

            lastIpv4 = newIpv4
            lastProxyCidrs = newProxyCidrs.toList()

            val ipv4Changed = newIpv4 != currentIpv4
            val proxyCidrsChanged = newProxyCidrs != currentProxyCidrs

            if (ipv4Changed || proxyCidrsChanged) {
                Log.i(TAG, "网络状态变化: IPv4 $currentIpv4 -> $newIpv4, ProxyCIDRs ${currentProxyCidrs.size} -> ${newProxyCidrs.size}")
                currentIpv4 = newIpv4
                currentProxyCidrs = newProxyCidrs.toList()
                restartVpnService(newIpv4, newProxyCidrs)
            } else {
                Log.d(TAG, "网络状态无变化: $currentIpv4, ProxyCIDRs ${currentProxyCidrs.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "监控网络状态异常", e)
        }
    }

    private fun parseNetworkInfo(jsonString: String): NetworkInstanceRunningInfoMap? {
        return try {
            adapter.fromJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "解析网络信息失败", e)
            null
        }
    }

    private fun restartVpnService(ipv4: String, proxyCidrs: List<String>) {
        stopVpnService()
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "VPN 权限未授予，缓存待启动状态")
            onVpnAuthRequired()
            return
        }
        startVpnService(ipv4, proxyCidrs)
    }

    private fun startVpnService(ipv4: String, proxyCidrs: List<String>) {
        val intent = Intent(context, EasyTierVpnService::class.java).apply {
            putExtra(EasyTierVpnService.EXTRA_IPV4_ADDRESS, ipv4)
            putStringArrayListExtra(EasyTierVpnService.EXTRA_PROXY_CIDRS, ArrayList(proxyCidrs))
            putExtra(EasyTierVpnService.EXTRA_INSTANCE_NAME, instanceName)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
            vpnServiceIntent = intent
            Log.i(TAG, "VpnService 已启动 - IPv4: $ipv4, ProxyCIDRs: ${proxyCidrs.size}")
        } catch (e: Exception) {
            Log.e(TAG, "启动 VpnService 失败，可能 App 在后台受限", e)
            onVpnAuthRequired()
        }
    }

    private fun stopVpnService() {
        vpnServiceIntent?.let { intent ->
            try {
                context.stopService(intent)
                Log.i(TAG, "VpnService 已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止 VpnService 异常", e)
            }
        }
        vpnServiceIntent = null
    }
}
