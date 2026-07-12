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
import kotlin.concurrent.thread

class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var instanceName: String? = null
    private var setupThread: Thread? = null

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理通知栏"停止"action
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        val ipv4Address = intent?.getStringExtra(EXTRA_IPV4_ADDRESS)
        val ipv6Address = intent?.getStringExtra(EXTRA_IPV6_ADDRESS)
        val proxyCidrs = intent?.getStringArrayListExtra(EXTRA_PROXY_CIDRS) ?: arrayListOf()
        val dnsServers = intent?.getStringArrayListExtra(EXTRA_DNS_SERVERS) ?: arrayListOf()
        instanceName = intent?.getStringExtra(EXTRA_INSTANCE_NAME)

        if (ipv4Address == null || instanceName == null) {
            Log.e(TAG, "缺少必要参数: ipv4Address=$ipv4Address, instanceName=$instanceName")
            stopSelf()
            return START_NOT_STICKY
        }

        // 前台服务必须先启动，再做后续工作
        startForeground(NOTIFICATION_ID, buildNotification())

        Log.i(
            TAG,
            "启动 VPN Service - IPv4: $ipv4Address, IPv6: $ipv6Address, " +
                    "DNS(显式): $dnsServers, Proxy CIDRs: ${proxyCidrs.size}, Instance: $instanceName"
        )

        setupThread = thread(name = "easytier-vpn-setup") {
            try {
                setupVpnInterface(ipv4Address, ipv6Address, proxyCidrs, dnsServers)
            } catch (t: Throwable) {
                Log.e(TAG, "VPN 设置失败", t)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun setupVpnInterface(
        ipv4Address: String,
        ipv6Address: String?,
        proxyCidrs: List<String>,
        dnsServers: List<String>
    ) {
        val (ip, networkLength) = parseIpv4Address(ipv4Address)

        val builder = Builder()
        builder.setSession("EasyTier VPN")
            .addAddress(ip, networkLength)
            .addDisallowedApplication(packageName)

        // IPv6 地址（如果配置了）
        if (!ipv6Address.isNullOrBlank()) {
            val (ip6, prefixLen) = parseIpv6Address(ipv6Address)
            builder.addAddress(ip6, prefixLen)
            Log.d(TAG, "添加 IPv6 地址: $ip6/$prefixLen")
        }

        // DNS 服务器：显式配置 > 系统 DNS > 回退默认
        val effectiveDns = when {
            dnsServers.isNotEmpty() -> dnsServers
            else -> getSystemDnsServers().ifEmpty { FALLBACK_DNS }
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
        proxyCidrs.forEach { cidr ->
            try {
                val (routeIp, routeLength) = parseCidr(cidr)
                builder.addRoute(routeIp, routeLength)
                Log.d(TAG, "添加路由: $routeIp/$routeLength")
            } catch (e: Exception) {
                Log.w(TAG, "解析 CIDR 失败: $cidr", e)
            }
        }

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            Log.e(TAG, "创建 VPN 接口失败")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        Log.i(TAG, "VPN 接口创建成功")

        // 将 TUN 文件描述符传递给 EasyTier
        instanceName?.let { name ->
            val fd = vpnInterface!!.fd
            val result = EasyTierJNI.setTunFd(name, fd)
            if (result == 0) {
                Log.i(TAG, "TUN 文件描述符设置成功: $fd")
            } else {
                Log.e(TAG, "TUN 文件描述符设置失败: $result")
            }
        }
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

    private fun parseIpv4Address(ipv4Address: String): Pair<String, Int> {
        return if (ipv4Address.contains("/")) {
            val parts = ipv4Address.split("/")
            Pair(parts[0], parts[1].toInt())
        } else {
            Pair(ipv4Address, 24)
        }
    }

    private fun parseIpv6Address(ipv6Address: String): Pair<String, Int> {
        return if (ipv6Address.contains("/")) {
            val parts = ipv6Address.split("/", limit = 2)
            Pair(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 64)
        } else {
            Pair(ipv6Address, 64)
        }
    }

    private fun parseCidr(cidr: String): Pair<String, Int> {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            throw IllegalArgumentException("无效的 CIDR 格式: $cidr")
        }
        return Pair(parts[0], parts[1].toInt())
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "停止", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    /** 停止 VPN：关闭接口 + 释放 EasyTier 实例 + 停止前台服务 */
    private fun stopVpn() {
        Log.i(TAG, "停止 VPN")
        setupThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        instanceName?.let { EasyTierJNI.retainNetworkInstance(null) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
        setupThread?.interrupt()
        if (vpnInterface != null) {
            vpnInterface?.close()
            vpnInterface = null
            instanceName?.let { EasyTierJNI.retainNetworkInstance(null) }
        }
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
