package com.easytier.app

import androidx.annotation.Keep
import java.util.UUID


@Keep
data class ConfigData(
    val id: String = UUID.randomUUID().toString(),
    val instanceName: String = "easytier",

    // --- 基本设置 ---
    val virtualIpv4: String = "",
    val networkLength: Int = 24,
    val dhcp: Boolean = true,
    val networkName: String = "easytier",
    val networkSecret: String = "",
    val peers: String = "tcp://public.easytier.top:11010",

    // --- 高级设置 ---
    val hostname: String = "",
    val proxyNetworks: String = "",
    val enableVpnPortal: Boolean = false,
    val vpnPortalClientNetworkAddr: String = "10.14.14.0",
    val vpnPortalClientNetworkLen: Int = 24,
    val vpnPortalListenPort: Int = 11011,
    val listenerUrls: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011",
    val devName: String = "",
    val mtu: String = "",
    val enableRelayNetworkWhitelist: Boolean = false,
    val relayNetworkWhitelist: String = "",
    val enableManualRoutes: Boolean = false,
    val routes: String = "",
    val enableSocks5: Boolean = false,
    val socks5Port: Int = 1080,
    val exitNodes: String = "",
    val mappedListeners: String = "",
    val rpcPortal: String = "0.0.0.0:0",
    val rpcPortalWhitelist: String = "",

    // --- 端口转发 ---
    val portForwards: List<PortForwardItem> = emptyList(),

    // --- Flags (布尔开关) ---
    val latencyFirst: Boolean = false,
    val useSmoltcp: Boolean = false,
    val disableIpv6: Boolean = false,
    val enableKcpProxy: Boolean = false,
    val disableKcpInput: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val disableQuicInput: Boolean = false,
    val disableP2p: Boolean = false,
    val bindDevice: Boolean = false,
    val noTun: Boolean = false,
    val enableExitNode: Boolean = false,
    val relayAllPeerRpc: Boolean = false,
    val multiThread: Boolean = true,
    val proxyForwardBySystem: Boolean = false,
    val disableEncryption: Boolean = false,
    val disableUdpHolePunching: Boolean = false,
    val disableSymHolePunching: Boolean = false,
    val acceptDns: Boolean = false,
    val privateMode: Boolean = false
)

@Keep
data class PortForwardItem(
    val id: String = UUID.randomUUID().toString(),
    var proto: String = "tcp",
    var bindIp: String = "0.0.0.0",
    var bindPort: Int? = null,
    var dstIp: String = "",
    var dstPort: Int? = null
)