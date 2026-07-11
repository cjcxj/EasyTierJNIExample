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
    val ipv6: String = "",
    val stunServers: String = "",
    val stunServersV6: String = "",
    val secureMode: Boolean = false,
    val localPrivateKey: String = "",
    val localPublicKey: String = "",

    // --- 端口转发 ---
    val portForwards: List<PortForwardItem> = emptyList(),

    // --- IPv6 公网地址 ---
    val ipv6PublicAddrProvider: Boolean = false,
    val ipv6PublicAddrAuto: Boolean = false,
    val ipv6PublicAddrPrefix: String = "",

    // --- Flags (布尔开关) ---
    val latencyFirst: Boolean = false,
    val useSmoltcp: Boolean = false,
    val disableIpv6: Boolean = false,
    val enableKcpProxy: Boolean = false,
    val disableKcpInput: Boolean = false,
    val disableRelayKcp: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val disableQuicInput: Boolean = false,
    val disableP2p: Boolean = false,
    val bindDevice: Boolean = true,
    val noTun: Boolean = false,
    val enableExitNode: Boolean = false,
    val relayAllPeerRpc: Boolean = false,
    val multiThread: Boolean = true,
    val proxyForwardBySystem: Boolean = false,
    val disableEncryption: Boolean = false,
    val disableUdpHolePunching: Boolean = false,
    val disableSymHolePunching: Boolean = false,
    val acceptDns: Boolean = false,
    val privateMode: Boolean = false,
    val p2pOnly: Boolean = false,
    val lazyP2p: Boolean = false,
    val needP2p: Boolean = false,
    val disableTcpHolePunching: Boolean = false,
    val disableRelayQuic: Boolean = false,
    val enableRelayForeignNetworkQuic: Boolean = false,
    val enableRelayForeignNetworkKcp: Boolean = false,
    val disableUpnp: Boolean = false,
    val disableRelayData: Boolean = false,
    val enableUdpBroadcastRelay: Boolean = false,
    val encryptionAlgorithm: String = "",

    // --- 高级选项 ---
    val dataCompressAlgo: String = "",  // ""=default, "none", "zstd"
    val multiThreadCount: Int = 2,
    val foreignRelayBpsLimit: String = "",
    val instanceRecvBpsLimit: String = "",
    val tldDnsZone: String = "",
    val socketMark: String = "",
    val credentialFile: String = "",

    // --- Android 端独有配置（不进 TomlConfig，仅持久化到 DataStore）---
    // VPN 接口的 DNS 服务器，多行字符串每行一个 IP；为空时使用系统 DNS，再回退到默认
    val vpnDnsServers: String = "",

    // --- 非 UI 字段 (仅用于 TOML 配置文件读写保留, 对齐上游) ---
    val netns: String = "",
    val acl: Acl? = null,
    val tcpWhitelist: String = "",
    val udpWhitelist: String = "",
    val defaultProtocol: String = "",
    val source: ConfigSourceConfig? = null
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