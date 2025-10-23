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
    val hostname: String? = null,
    @SerialName("instance_name")
    val instanceName: String,
    @SerialName("instance_id")
    val id: String,

    val dhcp: Boolean,
    val ipv4: String? = null, // e.g., "10.0.0.1/24"

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val listeners: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("mapped_listeners")
    val mappedListeners: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("exit_nodes")
    val exitNodes: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("rpc_portal")
    val rpcPortal: String? = null,
    @SerialName("rpc_portal_whitelist")
    val rpcPortalWhitelist: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val routes: List<String>? = null,

    @SerialName("socks5_proxy")
    val socks5Proxy: String? = null, // e.g., "socks5://0.0.0.0:1080"

    @SerialName("network_identity")
    val networkIdentity: NetworkIdentity,

    val peer: List<Peer>? = null,

    @SerialName("proxy_network")
    val proxyNetworks: List<ProxyNetwork>? = null,

    @SerialName("vpn_portal_config")
    val vpnPortalConfig: VpnPortalConfig? = null,

    @SerialName("port_forward")
    val portForwards: List<PortForward>? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val flags: Flags = Flags()
)

@Serializable
data class NetworkIdentity(
    @SerialName("network_name")
    val networkName: String,
    @SerialName("network_secret")
    val networkSecret: String
)


@Serializable
data class Peer(
    val uri: String
)


@Serializable
data class ProxyNetwork(
    val cidr: String
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
data class Flags(
    @SerialName("dev_name")
    val devName: String? = null,
    val mtu: Int? = null,
    @SerialName("relay_network_whitelist")
    val relayNetworkWhitelist: String? = null,
    @SerialName("latency_first")
    val latencyFirst: Boolean = false,
    @SerialName("use_smoltcp")
    val useSmoltcp: Boolean = false,
    @SerialName("disable_ipv6")
    val disableIpv6: Boolean = false,
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
    val bindDevice: Boolean = false,
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
    @SerialName("disable_encryption")
    val disableEncryption: Boolean = false,
    @SerialName("disable_udp_hole_punching")
    val disableUdpHolePunching: Boolean = false,
    @SerialName("disable_sym_hole_punching")
    val disableSymHolePunching: Boolean = false,
    @SerialName("accept_dns")
    val acceptDns: Boolean = false,
    @SerialName("private_mode")
    val privateMode: Boolean = false
)