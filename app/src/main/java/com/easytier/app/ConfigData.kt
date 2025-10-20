package com.easytier.app

import androidx.annotation.Keep
import java.util.UUID

/**
 * 存储单个网络配置的数据模型。
 */
@Keep
data class ConfigData(
    // --- 基本信息 ---
    val id: String = UUID.randomUUID().toString(),
    val instanceName: String = "easytier",
    val hostname: String = "Android-Device",

    // --- 网络身份 ---
    val networkName: String = "easytier",
    val networkSecret: String = "",

    // --- IP 与接口 ---
    val ipv4: String = "",
    val dhcp: Boolean = true,
    val ipv6: String = "",
    val mtu: String = "", // 留空以使用默认值 1380
    val noTun: Boolean = false, // 默认 false

    // --- 连接 ---
    val peers: String = "tcp://public.easytier.top:11010",
    val listeners: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010",
    val stunServers: String = "", // 每行一个

    // --- 高级路由 ---
    val proxyNetworks: String = "", // 每行一个
    val exitNodes: String = "",     // 每行一个
    val enableExitNode: Boolean = false, // 默认 false
    val acceptDns: Boolean = false,      // 默认 false

    // --- 性能与协议 (flags) ---
    val latencyFirst: Boolean = false,      // 默认 false
    val enableKcpProxy: Boolean = false,    // 默认 false
    val enableQuicProxy: Boolean = false,   // 默认 false
    val disableEncryption: Boolean = false, // 对应 enable_encryption=true
    val multiThread: Boolean = true,        // 默认 true

    // --- 安全 ---
    val privateMode: Boolean = false,       // 默认 false
    val encryptionAlgorithm: String = "", // 留空以使用默认值 "aes-gcm"
    val disableUdpHolePunching: Boolean = false,
    val disableSymHolePunching: Boolean = false
)