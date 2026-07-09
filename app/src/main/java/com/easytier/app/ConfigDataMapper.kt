package com.easytier.app

import java.util.UUID


/**
 * 将扁平的 ConfigData 对象转换为结构化的、可序列化为 TOML 的对象。
 */
fun ConfigData.toTomlConfig(): TomlConfig {
    // 辅助函数，用于将多行字符串解析为非空字符串列表
    fun String.toStringList() = this.lines().filter { it.isNotBlank() }

    // 构建 [flags] 部分
    val flags = Flags(
        defaultProtocol = this.defaultProtocol.takeIf { it.isNotBlank() },
        devName = this.devName.takeIf { it.isNotBlank() },
        mtu = this.mtu.toIntOrNull(),
        relayNetworkWhitelist = if (this.enableRelayNetworkWhitelist) this.relayNetworkWhitelist.takeIf { it.isNotBlank() } else null,
        latencyFirst = this.latencyFirst,
        useSmoltcp = this.useSmoltcp,
        enableIpv6 = !this.disableIpv6,
        enableKcpProxy = this.enableKcpProxy,
        disableKcpInput = this.disableKcpInput,
        disableRelayKcp = this.disableRelayKcp,
        enableQuicProxy = this.enableQuicProxy,
        disableQuicInput = this.disableQuicInput,
        disableP2p = this.disableP2p,
        bindDevice = this.bindDevice,
        noTun = this.noTun,
        enableExitNode = this.enableExitNode,
        relayAllPeerRpc = this.relayAllPeerRpc,
        multiThread = this.multiThread,
        proxyForwardBySystem = this.proxyForwardBySystem,
        enableEncryption = !this.disableEncryption,
        disableUdpHolePunching = this.disableUdpHolePunching,
        disableSymHolePunching = this.disableSymHolePunching,
        acceptDns = this.acceptDns,
        privateMode = this.privateMode,
        p2pOnly = this.p2pOnly,
        lazyP2p = this.lazyP2p,
        needP2p = this.needP2p,
        disableTcpHolePunching = this.disableTcpHolePunching,
        disableRelayQuic = this.disableRelayQuic,
        enableRelayForeignNetworkQuic = this.enableRelayForeignNetworkQuic,
        enableRelayForeignNetworkKcp = this.enableRelayForeignNetworkKcp,
        disableUpnp = this.disableUpnp,
        disableRelayData = this.disableRelayData,
        enableUdpBroadcastRelay = this.enableUdpBroadcastRelay,
        encryptionAlgorithm = this.encryptionAlgorithm.takeIf { it.isNotBlank() },
        tldDnsZone = this.tldDnsZone.takeIf { it.isNotBlank() },
        dataCompressAlgo = this.dataCompressAlgo.takeIf { it.isNotBlank() },
        foreignRelayBpsLimit = this.foreignRelayBpsLimit.toLongOrNull(),
        multiThreadCount = this.multiThreadCount.takeIf { it != 2 },
        instanceRecvBpsLimit = this.instanceRecvBpsLimit.toLongOrNull(),
        socketMark = this.socketMark.toIntOrNull()
    )

    // 构建 [[port_forward]] 部分
    val portForwards = this.portForwards
        .filter { it.bindPort != null && it.dstPort != null && it.dstIp.isNotBlank() }
        .map { pf ->
            PortForward(
                bindAddr = "${pf.bindIp}:${pf.bindPort}",
                dstAddr = "${pf.dstIp}:${pf.dstPort}",
                proto = pf.proto
            )
        }

    // 构建根对象
    return TomlConfig(
        hostname = this.hostname.takeIf { it.isNotBlank() },
        instanceName = this.instanceName,
        id = this.id,
        dhcp = this.dhcp,
        ipv4 = if (!this.dhcp && this.virtualIpv4.isNotBlank()) "${this.virtualIpv4}/${this.networkLength}" else null,
        ipv6 = this.ipv6.takeIf { it.isNotBlank() },

        listeners = this.listenerUrls.toStringList().takeIf { it.isNotEmpty() },
        mappedListeners = this.mappedListeners.toStringList().takeIf { it.isNotEmpty() },
        exitNodes = this.exitNodes.toStringList().takeIf { it.isNotEmpty() },
        routes = if (this.enableManualRoutes) this.routes.toStringList().takeIf { it.isNotEmpty() } else null,

        socks5Proxy = if (this.enableSocks5) "socks5://0.0.0.0:${this.socks5Port}" else null,

        networkIdentity = NetworkIdentity(
            networkName = this.networkName,
            networkSecret = this.networkSecret.takeIf { it.isNotBlank() }
        ),

        peer = this.peers.toStringList().map { Peer(uri = it) },
        proxyNetworks = this.proxyNetworks.toStringList().map { ProxyNetwork(cidr = it) },

        vpnPortalConfig = if (this.enableVpnPortal) VpnPortalConfig(
            clientCidr = "${this.vpnPortalClientNetworkAddr}/${this.vpnPortalClientNetworkLen}",
            wireguardListen = "0.0.0.0:${this.vpnPortalListenPort}"
        ) else null,

        portForwards = portForwards,

        stunServers = this.stunServers.toStringList().takeIf { it.isNotEmpty() },
        stunServersV6 = this.stunServersV6.toStringList().takeIf { it.isNotEmpty() },
        secureMode = if (this.secureMode) SecureModeConfig(
            enabled = true,
            localPrivateKey = this.localPrivateKey.takeIf { it.isNotBlank() },
            localPublicKey = this.localPublicKey.takeIf { it.isNotBlank() }
        ) else null,

        flags = flags,

        acl = this.acl,
        tcpWhitelist = this.tcpWhitelist.toStringList().takeIf { it.isNotEmpty() },
        udpWhitelist = this.udpWhitelist.toStringList().takeIf { it.isNotEmpty() },
        source = this.source,

        netns = this.netns.takeIf { it.isNotBlank() },
        credentialFile = this.credentialFile.takeIf { it.isNotBlank() },
        ipv6PublicAddrProvider = this.ipv6PublicAddrProvider.takeIf { it },
        ipv6PublicAddrAuto = this.ipv6PublicAddrAuto.takeIf { it },
        ipv6PublicAddrPrefix = this.ipv6PublicAddrPrefix.takeIf { it.isNotBlank() }
    )
}

/**
 * 将结构化的 TomlConfig 对象转换回扁平的、用于应用状态的 ConfigData 对象。
 */
fun TomlConfig.toConfigData(): ConfigData {

    // --- 辅助函数 ---

    // 将字符串列表安全地转换为多行字符串
    fun List<String>?.toMultiLineString(): String = this?.joinToString("\n") ?: ""

    // --- 解析复合字段 ---

    // 安全地从 "ip/length" 格式中解析 IP 和网络长度
    val (parsedIp, parsedLength) = this.ipv4?.split('/', limit = 2)
        ?.let { parts ->
            val ip = parts.getOrNull(0)?.trim() ?: ""
            val len = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 24
            ip to len
        } ?: ("" to 24) // 如果 ipv4 字段为 null，则提供默认值

    // 安全地从 "ip:port" 格式中解析 IP 和端口
    fun parseAddress(address: String?, defaultIp: String, defaultPort: Int): Pair<String, Int?> {
        return address?.split(':', limit = 2)
            ?.let { parts ->
                val ip = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultIp
                val port = parts.getOrNull(1)?.trim()?.toIntOrNull()
                ip to port
            } ?: (defaultIp to defaultPort)
    }

    val (vpnPortalAddr, vpnPortalLen) = this.vpnPortalConfig?.clientCidr?.split('/', limit = 2)
        ?.let { parts ->
            val addr = parts.getOrNull(0)?.trim() ?: "10.14.14.0"
            val len = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 24
            addr to len
        } ?: ("10.14.14.0" to 24)

    val vpnListenPort = this.vpnPortalConfig?.wireguardListen?.split(':')?.lastOrNull()?.trim()?.toIntOrNull() ?: 11011

    val socksPort = this.socks5Proxy?.split(':')?.lastOrNull()?.trim()?.toIntOrNull() ?: 1080

    // --- 构建 ConfigData 对象 ---

    return ConfigData(
        // 如果导入的 id 为空，生成一个新的，以防万一
        id = this.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        instanceName = this.instanceName,

        // --- 基本设置 ---
        virtualIpv4 = parsedIp,
        networkLength = parsedLength,
        dhcp = this.dhcp,
        networkName = this.networkIdentity.networkName,
        networkSecret = this.networkIdentity.networkSecret ?: "",
        peers = this.peer?.map { it.uri }.toMultiLineString(),

        // --- 高级设置 ---
        hostname = this.hostname ?: "",
        proxyNetworks = this.proxyNetworks?.map { it.cidr }.toMultiLineString(),
        enableVpnPortal = this.vpnPortalConfig != null,
        vpnPortalClientNetworkAddr = vpnPortalAddr,
        vpnPortalClientNetworkLen = vpnPortalLen,
        vpnPortalListenPort = vpnListenPort,

        listenerUrls = this.listeners.toMultiLineString(),
        devName = this.flags.devName ?: "",
        mtu = this.flags.mtu?.toString() ?: "",
        enableRelayNetworkWhitelist = this.flags.relayNetworkWhitelist?.isNotBlank() == true,
        relayNetworkWhitelist = this.flags.relayNetworkWhitelist ?: "",

        enableManualRoutes = this.routes?.isNotEmpty() == true,
        routes = this.routes.toMultiLineString(),

        enableSocks5 = this.socks5Proxy != null,
        socks5Port = socksPort,

        exitNodes = this.exitNodes.toMultiLineString(),
        mappedListeners = this.mappedListeners.toMultiLineString(),
        ipv6 = this.ipv6 ?: "",
        stunServers = this.stunServers.toMultiLineString(),
        stunServersV6 = this.stunServersV6.toMultiLineString(),
        secureMode = this.secureMode?.enabled ?: false,
        localPrivateKey = this.secureMode?.localPrivateKey ?: "",
        localPublicKey = this.secureMode?.localPublicKey ?: "",

        // --- 端口转发 ---
        portForwards = this.portForwards?.mapNotNull { pf ->
            try {
                val (bindIp, bindPort) = parseAddress(pf.bindAddr, "0.0.0.0", 0)
                val (dstIp, dstPort) = parseAddress(pf.dstAddr, "", 0)

                // 只有当必要字段都存在时，才创建 PortForwardItem
                if (bindPort != null && dstPort != null && dstIp.isNotBlank()) {
                    PortForwardItem(
                        // id 会在 data class 中自动生成
                        proto = pf.proto,
                        bindIp = bindIp,
                        bindPort = bindPort,
                        dstIp = dstIp,
                        dstPort = dstPort
                    )
                } else {
                    null // 如果解析失败，则过滤掉这个无效的条目
                }
            } catch (_: Exception) {
                null // 捕获任何潜在的解析错误，并过滤掉该条目
            }
        } ?: emptyList(), // 如果 this.portForwards 为 null，则返回一个空列表

        // --- IPv6 公网地址 ---
        ipv6PublicAddrProvider = this.ipv6PublicAddrProvider ?: false,
        ipv6PublicAddrAuto = this.ipv6PublicAddrAuto ?: false,
        ipv6PublicAddrPrefix = this.ipv6PublicAddrPrefix ?: "",

        // --- Flags (布尔开关) ---
        latencyFirst = this.flags.latencyFirst,
        useSmoltcp = this.flags.useSmoltcp,
        disableIpv6 = !this.flags.enableIpv6,
        enableKcpProxy = this.flags.enableKcpProxy,
        disableKcpInput = this.flags.disableKcpInput,
        disableRelayKcp = this.flags.disableRelayKcp,
        enableQuicProxy = this.flags.enableQuicProxy,
        disableQuicInput = this.flags.disableQuicInput,
        disableP2p = this.flags.disableP2p,
        bindDevice = this.flags.bindDevice,
        noTun = this.flags.noTun,
        enableExitNode = this.flags.enableExitNode,
        relayAllPeerRpc = this.flags.relayAllPeerRpc,
        multiThread = this.flags.multiThread,
        proxyForwardBySystem = this.flags.proxyForwardBySystem,
        disableEncryption = !this.flags.enableEncryption,
        disableUdpHolePunching = this.flags.disableUdpHolePunching,
        disableSymHolePunching = this.flags.disableSymHolePunching,
        acceptDns = this.flags.acceptDns,
        privateMode = this.flags.privateMode,
        p2pOnly = this.flags.p2pOnly,
        lazyP2p = this.flags.lazyP2p,
        needP2p = this.flags.needP2p,
        disableTcpHolePunching = this.flags.disableTcpHolePunching,
        disableRelayQuic = this.flags.disableRelayQuic,
        enableRelayForeignNetworkQuic = this.flags.enableRelayForeignNetworkQuic,
        enableRelayForeignNetworkKcp = this.flags.enableRelayForeignNetworkKcp,
        disableUpnp = this.flags.disableUpnp,
        disableRelayData = this.flags.disableRelayData,
        enableUdpBroadcastRelay = this.flags.enableUdpBroadcastRelay,
        encryptionAlgorithm = this.flags.encryptionAlgorithm ?: "",

        // --- 高级选项 ---
        dataCompressAlgo = this.flags.dataCompressAlgo ?: "",
        multiThreadCount = this.flags.multiThreadCount ?: 2,
        foreignRelayBpsLimit = this.flags.foreignRelayBpsLimit?.toString() ?: "",
        instanceRecvBpsLimit = this.flags.instanceRecvBpsLimit?.toString() ?: "",
        tldDnsZone = this.flags.tldDnsZone ?: "",
        socketMark = this.flags.socketMark?.toString() ?: "",
        credentialFile = this.credentialFile ?: "",

        // --- 非 UI 字段 (TOML 配置文件读写保留) ---
        netns = this.netns ?: "",
        acl = this.acl,
        tcpWhitelist = this.tcpWhitelist.toMultiLineString(),
        udpWhitelist = this.udpWhitelist.toMultiLineString(),
        defaultProtocol = this.flags.defaultProtocol ?: "",
        source = this.source
    )
}