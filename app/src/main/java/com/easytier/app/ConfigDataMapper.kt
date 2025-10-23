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
        devName = this.devName.takeIf { it.isNotBlank() },
        mtu = this.mtu.toIntOrNull(),
        relayNetworkWhitelist = if (this.enableRelayNetworkWhitelist) this.relayNetworkWhitelist.takeIf { it.isNotBlank() } else null,
        latencyFirst = this.latencyFirst,
        useSmoltcp = this.useSmoltcp,
        disableIpv6 = this.disableIpv6,
        enableKcpProxy = this.enableKcpProxy,
        disableKcpInput = this.disableKcpInput,
        enableQuicProxy = this.enableQuicProxy,
        disableQuicInput = this.disableQuicInput,
        disableP2p = this.disableP2p,
        bindDevice = this.bindDevice,
        noTun = this.noTun,
        enableExitNode = this.enableExitNode,
        relayAllPeerRpc = this.relayAllPeerRpc,
        multiThread = this.multiThread,
        proxyForwardBySystem = this.proxyForwardBySystem,
        disableEncryption = this.disableEncryption,
        disableUdpHolePunching = this.disableUdpHolePunching,
        disableSymHolePunching = this.disableSymHolePunching,
        acceptDns = this.acceptDns,
        privateMode = this.privateMode
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

        listeners = this.listenerUrls.toStringList().takeIf { it.isNotEmpty() },
        mappedListeners = this.mappedListeners.toStringList().takeIf { it.isNotEmpty() },
        exitNodes = this.exitNodes.toStringList().takeIf { it.isNotEmpty() },
        rpcPortal = this.rpcPortal.takeIf { it.isNotBlank()}, // 可以根据需要过滤掉默认值
        rpcPortalWhitelist = this.rpcPortalWhitelist.toStringList().takeIf { it.isNotEmpty() },
        routes = if (this.enableManualRoutes) this.routes.toStringList().takeIf { it.isNotEmpty() } else null,

        socks5Proxy = if (this.enableSocks5) "socks5://0.0.0.0:${this.socks5Port}" else null,

        networkIdentity = NetworkIdentity(
            networkName = this.networkName,
            networkSecret = this.networkSecret
        ),

        peer = this.peers.toStringList().map { Peer(uri = it) },
        proxyNetworks = this.proxyNetworks.toStringList().map { ProxyNetwork(cidr = it) },

        vpnPortalConfig = if (this.enableVpnPortal) VpnPortalConfig(
            clientCidr = "${this.vpnPortalClientNetworkAddr}/${this.vpnPortalClientNetworkLen}",
            wireguardListen = "0.0.0.0:${this.vpnPortalListenPort}"
        ) else null,

        portForwards = portForwards,

        flags = flags
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
        id = this.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        instanceName = this.instanceName,

        // --- 基本设置 ---
        virtualIpv4 = parsedIp,
        networkLength = parsedLength,
        dhcp = this.dhcp,
        networkName = this.networkIdentity.networkName,
        networkSecret = this.networkIdentity.networkSecret,
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
        rpcPortal = this.rpcPortal ?: "0.0.0.0:0",
        rpcPortalWhitelist = this.rpcPortalWhitelist.toMultiLineString(),

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
            } catch (e: Exception) {
                null // 捕获任何潜在的解析错误，并过滤掉该条目
            }
        } ?: emptyList(), // 如果 this.portForwards 为 null，则返回一个空列表

        // --- Flags (布尔开关) ---
        latencyFirst = this.flags.latencyFirst,
        useSmoltcp = this.flags.useSmoltcp,
        disableIpv6 = this.flags.disableIpv6,
        enableKcpProxy = this.flags.enableKcpProxy,
        disableKcpInput = this.flags.disableKcpInput,
        enableQuicProxy = this.flags.enableQuicProxy,
        disableQuicInput = this.flags.disableQuicInput,
        disableP2p = this.flags.disableP2p,
        bindDevice = this.flags.bindDevice,
        noTun = this.flags.noTun,
        enableExitNode = this.flags.enableExitNode,
        relayAllPeerRpc = this.flags.relayAllPeerRpc,
        multiThread = this.flags.multiThread,
        proxyForwardBySystem = this.flags.proxyForwardBySystem,
        disableEncryption = this.flags.disableEncryption,
        disableUdpHolePunching = this.flags.disableUdpHolePunching,
        disableSymHolePunching = this.flags.disableSymHolePunching,
        acceptDns = this.flags.acceptDns,
        privateMode = this.flags.privateMode
    )
}