@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlinx.serialization.InternalSerializationApi::class
)

package com.easytier.app

import android.annotation.SuppressLint
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 目标数据结构 (用于序列化) ---

@Serializable
data class TomlConfig(
    val netns: String? = null,
    val hostname: String? = null,
    @SerialName("instance_name")
    val instanceName: String = "default",
    @SerialName("instance_id")
    val id: String? = null,

    val dhcp: Boolean = false,
    val ipv4: String? = null, // e.g., "10.0.0.1/24"
    val ipv6: String? = null, // e.g., "fd00::1/64"

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val listeners: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("mapped_listeners")
    val mappedListeners: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("exit_nodes")
    val exitNodes: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val routes: List<String>? = null,

    @SerialName("socks5_proxy")
    val socks5Proxy: String? = null, // e.g., "socks5://0.0.0.0:1080"

    @SerialName("network_identity")
    val networkIdentity: NetworkIdentity = NetworkIdentity(),

    val peer: List<Peer>? = null,

    @SerialName("proxy_network")
    val proxyNetworks: List<ProxyNetwork>? = null,

    @SerialName("vpn_portal_config")
    val vpnPortalConfig: VpnPortalConfig? = null,

    @SerialName("port_forward")
    val portForwards: List<PortForward>? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("stun_servers")
    val stunServers: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("stun_servers_v6")
    val stunServersV6: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("secure_mode")
    val secureMode: SecureModeConfig? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val flags: Flags = Flags(),

    val acl: Acl? = null,
    @SerialName("tcp_whitelist")
    val tcpWhitelist: List<String>? = null,
    @SerialName("udp_whitelist")
    val udpWhitelist: List<String>? = null,

    val source: ConfigSourceConfig? = null,

    // --- 上游新增字段 ---
    @SerialName("credential_file")
    val credentialFile: String? = null,
    @SerialName("ipv6_public_addr_provider")
    val ipv6PublicAddrProvider: Boolean? = null,
    @SerialName("ipv6_public_addr_auto")
    val ipv6PublicAddrAuto: Boolean? = null,
    @SerialName("ipv6_public_addr_prefix")
    val ipv6PublicAddrPrefix: String? = null
)

@Serializable
data class NetworkIdentity(
    @SerialName("network_name")
    val networkName: String = "default",
    @SerialName("network_secret")
    val networkSecret: String? = null
)


@Serializable
data class Peer(
    val uri: String,
    @SerialName("peer_public_key")
    val peerPublicKey: String? = null
)


@Serializable
data class ProxyNetwork(
    val cidr: String,
    @SerialName("mapped_cidr")
    val mappedCidr: String? = null,
    val allow: List<String>? = null
)


@Serializable
data class VpnPortalConfig(
    @SerialName("client_cidr")
    val clientCidr: String,
    @SerialName("wireguard_listen")
    val wireguardListen: String
)


@Serializable
data class PortForward(
    @SerialName("bind_addr")
    val bindAddr: String,
    @SerialName("dst_addr")
    val dstAddr: String,
    val proto: String
)


@Serializable
data class SecureModeConfig(
    val enabled: Boolean = false,
    @SerialName("local_private_key") val localPrivateKey: String? = null,
    @SerialName("local_public_key") val localPublicKey: String? = null
)


@Serializable
data class Flags(
    @SerialName("default_protocol")
    val defaultProtocol: String? = null,
    @SerialName("dev_name")
    val devName: String? = null,
    val mtu: Int? = null,
    @SerialName("relay_network_whitelist")
    val relayNetworkWhitelist: String? = null,
    @SerialName("latency_first")
    val latencyFirst: Boolean = false,
    @SerialName("use_smoltcp")
    val useSmoltcp: Boolean = false,
    @SerialName("enable_ipv6")
    val enableIpv6: Boolean = true,
    @SerialName("enable_kcp_proxy")
    val enableKcpProxy: Boolean = false,
    @SerialName("disable_kcp_input")
    val disableKcpInput: Boolean = false,
    @SerialName("enable_quic_proxy")
    val enableQuicProxy: Boolean = false,
    @SerialName("disable_quic_input")
    val disableQuicInput: Boolean = false,
    @SerialName("disable_p2p")
    val disableP2p: Boolean = false,
    @SerialName("bind_device")
    val bindDevice: Boolean = true,
    @SerialName("no_tun")
    val noTun: Boolean = false,
    @SerialName("enable_exit_node")
    val enableExitNode: Boolean = false,
    @SerialName("relay_all_peer_rpc")
    val relayAllPeerRpc: Boolean = false,
    @SerialName("multi_thread")
    val multiThread: Boolean = true,
    @SerialName("proxy_forward_by_system")
    val proxyForwardBySystem: Boolean = false,
    @SerialName("enable_encryption")
    val enableEncryption: Boolean = true,
    @SerialName("disable_udp_hole_punching")
    val disableUdpHolePunching: Boolean = false,
    @SerialName("disable_sym_hole_punching")
    val disableSymHolePunching: Boolean = false,
    @SerialName("accept_dns")
    val acceptDns: Boolean = false,
    @SerialName("private_mode")
    val privateMode: Boolean = false,
    @SerialName("p2p_only")
    val p2pOnly: Boolean = false,
    @SerialName("lazy_p2p")
    val lazyP2p: Boolean = false,
    @SerialName("need_p2p")
    val needP2p: Boolean = false,
    @SerialName("disable_tcp_hole_punching")
    val disableTcpHolePunching: Boolean = false,
    @SerialName("disable_relay_quic")
    val disableRelayQuic: Boolean = false,
    @SerialName("enable_relay_foreign_network_quic")
    val enableRelayForeignNetworkQuic: Boolean = false,
    @SerialName("disable_upnp")
    val disableUpnp: Boolean = false,
    @SerialName("disable_relay_data")
    val disableRelayData: Boolean = false,
    @SerialName("enable_udp_broadcast_relay")
    val enableUdpBroadcastRelay: Boolean = false,
    @SerialName("encryption_algorithm")
    val encryptionAlgorithm: String? = null,
    @SerialName("disable_relay_kcp")
    val disableRelayKcp: Boolean = false,
    @SerialName("enable_relay_foreign_network_kcp")
    val enableRelayForeignNetworkKcp: Boolean = false,
    @SerialName("tld_dns_zone")
    val tldDnsZone: String? = null,
    @SerialName("data_compress_algo")
    val dataCompressAlgo: String? = null,
    @SerialName("foreign_relay_bps_limit")
    val foreignRelayBpsLimit: Long? = null,
    @SerialName("multi_thread_count")
    val multiThreadCount: Int? = null,
    @SerialName("instance_recv_bps_limit")
    val instanceRecvBpsLimit: Long? = null,
    @SerialName("socket_mark")
    val socketMark: Int? = null
)


// --- ACL 配置 (对齐上游 acl.proto，仅用于 TOML 读写，不暴露 UI) ---

@Serializable
data class Acl(
    @SerialName("acl_v1")
    val aclV1: AclV1? = null
)

@Serializable
data class AclV1(
    val chains: List<AclChain>? = null,
    val group: AclGroupInfo? = null
)

@Serializable
data class AclChain(
    val name: String? = null,
    @SerialName("chain_type")
    val chainType: Int? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val rules: List<AclRule>? = null,
    @SerialName("default_action")
    val defaultAction: Int? = null
)

@Serializable
data class AclRule(
    val name: String? = null,
    val description: String? = null,
    val priority: Int? = null,
    val enabled: Boolean? = null,
    val protocol: Int? = null,
    val ports: List<String>? = null,
    @SerialName("source_ips")
    val sourceIps: List<String>? = null,
    @SerialName("destination_ips")
    val destinationIps: List<String>? = null,
    @SerialName("source_ports")
    val sourcePorts: List<String>? = null,
    val action: Int? = null,
    @SerialName("rate_limit")
    val rateLimit: Int? = null,
    @SerialName("burst_limit")
    val burstLimit: Int? = null,
    val stateful: Boolean? = null,
    @SerialName("source_groups")
    val sourceGroups: List<String>? = null,
    @SerialName("destination_groups")
    val destinationGroups: List<String>? = null
)

@Serializable
data class AclGroupInfo(
    val declares: List<AclGroupIdentity>? = null,
    val members: List<String>? = null
)

@Serializable
data class AclGroupIdentity(
    @SerialName("group_name")
    val groupName: String? = null,
    @SerialName("group_secret")
    val groupSecret: String? = null
)


// --- 配置来源 (对齐上游 ConfigSourceConfig) ---

@Serializable
data class ConfigSourceConfig(
    val source: String = "user"
)