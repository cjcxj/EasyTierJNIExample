package com.easytier.app

import androidx.annotation.Keep
import java.util.UUID

/**
 * 数据模型严格、精确地匹配提供的标准TOML文件。
 */
@Keep
data class ConfigData(
    // --- [DEFAULT SECTION] ---
    val id: String = UUID.randomUUID().toString(), // instance_id, 内部使用
    val instanceName: String = "cjcxj-easytier",
    val hostname: String = "Android-Device",
    val ipv4: String = "",
    val dhcp: Boolean = true,
    val listeners: String = "",       // 多行
    val mappedListeners: String = "", // 多行
    val exitNodes: String = "",       // 多行
    val rpcPortal: String = "",
    val rpcPortalWhitelist: String = "", // 多行
    val routes: String = "",             // 多行
    val socks5Proxy: String = "",

    // --- [network_identity] ---
    val networkName: String = "cjcxj-easytier",
    val networkSecret: String = "",

    // --- [[peer]] ---
    val peers: String = "", // 多行

    // --- [[proxy_network]] ---
    val proxyNetworks: String = "", // 多行, e.g., "10.0.0.0/24"

    // --- [vpn_portal_config] ---
    val vpnPortalClientCidr: String = "",
    val vpnPortalWgListen: String = "",

    // --- [[port_forward]] ---
    val portForwards: String = "", // 格式: "bind_addr,dst_addr,proto" (每行一个)

    // --- [flags] ---
    val acceptDns: Boolean = false,
    val devName: String = "",
    val disableKcpInput: Boolean = false,
    val disableP2p: Boolean = false,
    val disableQuicInput: Boolean = false,
    val disableSymHolePunching: Boolean = false,
    val disableUdpHolePunching: Boolean = false,
    val enableEncryption: Boolean = true,
    val enableExitNode: Boolean = false,
    val enableIpv6: Boolean = true,
    val enableKcpProxy: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val latencyFirst: Boolean = false,
    val noTun: Boolean = false,
    val privateMode: Boolean = false,
    val proxyForwardBySystem: Boolean = false,
    val relayAllPeerRpc: Boolean = false,
    val relayNetworkWhitelist: String = "",
    val useSmoltcp: Boolean = false
)