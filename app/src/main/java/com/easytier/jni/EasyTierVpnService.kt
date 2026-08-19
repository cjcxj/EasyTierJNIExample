package com.easytier.jni

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.easytier.app.MainActivity
import com.easytier.app.R
import java.util.concurrent.Executors

class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    /** 所有对 vpnInterface 的读写/关闭都在该锁内进行，避免主线程与 setup 线程竞争 */
    private val lock = Any()

    /** 单一 setup 线程：连续 startService 的重建任务会排队串行执行，避免并发抢用旧接口 */
    private val setupExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "easytier-vpn-setup")
    }

    companion object {
        private const val TAG = "EasyTierVpnService"
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "easytier_vpn_channel"
        const val ACTION_STOP = "com.easytier.jni.action.STOP_VPN"

        // DNS 回退：当用户未配置且系统 DNS 不可用时使用
        private val FALLBACK_DNS = listOf("223.5.5.5", "114.114.114.114")

        const val EXTRA_IPV4_ADDRESS = "ipv4_address"
        const val EXTRA_IPV6_ADDRESS = "ipv6_address"
        const val EXTRA_PROXY_CIDRS = "proxy_cidrs"
        const val EXTRA_DNS_SERVERS = "dns_servers"
        const val EXTRA_INSTANCE_NAME = "instance_name"

        // STICKY 自愈：进程被杀后由系统以 null intent 重启时，用这里保存的最后配置重建接口
        private const val PREFS_NAME = "easytier_vpn_last_config"
        private const val KEY_INSTANCE = "instance_name"
        private const val KEY_IPV4 = "ipv4"
        private const val KEY_IPV6 = "ipv6"
        private const val KEY_PROXY_CIDRS = "proxy_cidrs"
        private const val KEY_DNS = "dns"
        // 首次解析出的有效 DNS：隧道建立后 active network 会变成 VPN 自身，
        // 缓存它可避免重建时回读造成 DNS 回环
        private const val KEY_RESOLVED_DNS = "resolved_dns"
    }

    /** VPN 建立所需参数快照 */
    private data class VpnParams(
        val instanceName: String,
        val ipv4Address: String,
        val ipv6Address: String?,
        val proxyCidrs: List<String>,
        val dnsServers: List<String>
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理通知栏/外部"停止"action
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        val params = readParams(intent)

        if (params == null) {
            Log.e(TAG, "无法获取 VPN 配置，停止服务")
            stopSelf()
            return START_NOT_STICKY
        }

        // 前台服务必须先启动，再做后续工作（必须在主线程尽快调用）
        startForeground(NOTIFICATION_ID, buildNotification())
        launchSetup(params)
        return START_STICKY
    }

    /**
     * 从 intent 读取参数；intent 为 null（STICKY 重启）或参数缺失时尝试从持久化配置恢复。
     */
    private fun readParams(intent: Intent?): VpnParams? {
        if (intent != null) {
            val ipv4 = intent.getStringExtra(EXTRA_IPV4_ADDRESS)
            val instance = intent.getStringExtra(EXTRA_INSTANCE_NAME)
            if (ipv4 != null && instance != null) {
                return VpnParams(
                    instanceName = instance,
                    ipv4Address = ipv4,
                    ipv6Address = intent.getStringExtra(EXTRA_IPV6_ADDRESS),
                    proxyCidrs = intent.getStringArrayListExtra(EXTRA_PROXY_CIDRS) ?: emptyList(),
                    dnsServers = intent.getStringArrayListExtra(EXTRA_DNS_SERVERS) ?: emptyList()
                )
            }
            Log.w(TAG, "onStartCommand 缺少必要参数，尝试从已保存配置恢复")
        } else {
            Log.i(TAG, "STICKY 重启（intent==null），从已保存配置恢复 VPN")
        }
        return restoreSavedState()
    }

    /**
     * 把设置任务提交到单一 executor 上，串行执行。
     * 不中断已提交的任务：连续的重建请求按顺序排队，各自处理自己的 old/new 接口切换。
     */
    private fun launchSetup(params: VpnParams) {
        setupExecutor.execute {
            try {
                setupVpnInterface(params)
            } catch (t: Throwable) {
                Log.e(TAG, "VPN 设置失败", t)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun setupVpnInterface(params: VpnParams) {
        val (ip, networkLength) = VpnConfigParsers.parseIpv4Address(params.ipv4Address)

        val builder = Builder()
            .setSession("EasyTier VPN")
            .addAddress(ip, networkLength)
            .addDisallowedApplication(packageName)

        // IPv6 地址（如果配置了）
        params.ipv6Address?.takeIf { it.isNotBlank() }?.let { ipv6Address ->
            val (ip6, prefixLen) = VpnConfigParsers.parseIpv6Address(ipv6Address)
            builder.addAddress(ip6, prefixLen)
            Log.d(TAG, "添加 IPv6 地址: $ip6/$prefixLen")
        }

        // DNS 服务器：显式配置 > 已缓存解析值（避免回环）> 系统 DNS > 回退默认
        val effectiveDns = when {
            params.dnsServers.isNotEmpty() -> params.dnsServers
            else -> getCachedResolvedDns() ?: getSystemDnsServers().ifEmpty { FALLBACK_DNS }
        }
        effectiveDns.forEach { dns ->
            try {
                builder.addDnsServer(dns)
                Log.d(TAG, "添加 DNS: $dns")
            } catch (e: Exception) {
                Log.w(TAG, "添加 DNS 失败: $dns", e)
            }
        }

        // 路由表
        params.proxyCidrs.forEach { cidr ->
            try {
                val (routeIp, routeLength) = VpnConfigParsers.parseCidr(cidr)
                builder.addRoute(routeIp, routeLength)
                Log.d(TAG, "添加路由: $routeIp/$routeLength")
            } catch (e: Exception) {
                Log.w(TAG, "解析 CIDR 失败: $cidr", e)
            }
        }

        val newInterface = builder.establish()

        if (newInterface == null) {
            Log.e(TAG, "创建 VPN 接口失败")
            // 仅在没有可用旧接口时才停止服务；重建场景保留旧接口继续工作
            synchronized(lock) {
                if (vpnInterface == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            return
        }

        // 新接口就绪后切换：替换引用并关闭旧接口（在锁内完成，避免与主线程竞争）
        synchronized(lock) {
            val oldInterface = vpnInterface
            vpnInterface = newInterface
            if (oldInterface != null) {
                try {
                    oldInterface.close()
                    Log.i(TAG, "旧 VPN 接口已关闭")
                } catch (e: Exception) {
                    Log.w(TAG, "关闭旧 VPN 接口失败", e)
                }
            }
        }
        Log.i(TAG, "VPN 接口创建成功")

        // 将 TUN 文件描述符传递给 EasyTier
        val fd = newInterface.fd
        val result = EasyTierJNI.setTunFd(params.instanceName, fd)
        if (result == 0) {
            Log.i(TAG, "TUN 文件描述符设置成功: $fd")
        } else {
            Log.e(TAG, "TUN 文件描述符设置失败: $result")
        }

        // 成功后持久化，供 STICKY 重启恢复
        persistState(params, effectiveDns)
    }

    private fun persistState(params: VpnParams, resolvedDns: List<String>) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_INSTANCE, params.instanceName)
            .putString(KEY_IPV4, params.ipv4Address)
            .putString(KEY_IPV6, params.ipv6Address)
            .putStringSet(KEY_PROXY_CIDRS, params.proxyCidrs.toSet())
            .putStringSet(KEY_DNS, params.dnsServers.toSet())
            .putStringSet(KEY_RESOLVED_DNS, resolvedDns.toSet())
            .apply()
    }

    /** 读取缓存的有效 DNS；没有则返回 null（表示需要在首次建立时读取系统 DNS） */
    private fun getCachedResolvedDns(): List<String>? {
        val set = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getStringSet(KEY_RESOLVED_DNS, null) ?: return null
        return set.toList().takeIf { it.isNotEmpty() }
    }

    private fun restoreSavedState(): VpnParams? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val instance = prefs.getString(KEY_INSTANCE, null)
        val ipv4 = prefs.getString(KEY_IPV4, null)
        if (instance == null || ipv4 == null) return null
        return VpnParams(
            instanceName = instance,
            ipv4Address = ipv4,
            ipv6Address = prefs.getString(KEY_IPV6, null),
            proxyCidrs = prefs.getStringSet(KEY_PROXY_CIDRS, emptySet())?.toList() ?: emptyList(),
            dnsServers = prefs.getStringSet(KEY_DNS, emptySet())?.toList() ?: emptyList()
        )
    }

    private fun clearPersistedState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
    }

    /** 通过 ConnectivityManager 获取系统当前活跃网络的 DNS 服务器 */
    private fun getSystemDnsServers(): List<String> {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return emptyList()
            val lp = cm.getLinkProperties(activeNetwork) ?: return emptyList()
            lp.dnsServers.mapNotNull { it.hostAddress }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "获取系统 DNS 失败", e)
            emptyList()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EasyTier VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "EasyTier VPN 服务运行通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_STOP_VPN
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EasyTier VPN")
            .setContentText("VPN 服务运行中")
            .setSmallIcon(R.drawable.ic_stat_easytier)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "停止", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    /** 停止 VPN：关闭接口 + 清除持久化配置 + 停止前台服务 */
    private fun stopVpn() {
        Log.i(TAG, "停止 VPN")
        synchronized(lock) {
            vpnInterface?.close()
            vpnInterface = null
        }
        clearPersistedState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
        setupExecutor.shutdownNow()
        synchronized(lock) {
            vpnInterface?.close()
            vpnInterface = null
        }
        // 显式停止（stopService/stopSelf）才会走到 onDestroy，此时清除持久化配置，
        // 避免服务被用户停掉后 STICKY 重启又自动拉起一个用户不想要的 VPN。
        // 注意：系统因内存回收杀掉进程时不会调用 onDestroy，持久化配置得以保留供自愈。
        clearPersistedState()
        // 双重保险移除通知
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "取消通知失败", e)
        }
    }
}
