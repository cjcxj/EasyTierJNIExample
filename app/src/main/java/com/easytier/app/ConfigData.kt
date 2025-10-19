package com.easytier.app

import java.util.UUID


data class ConfigData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "默认配置",
    val hostname: String = "Android-Device",
    val instanceName: String = "easytier",
    val ipv4: String = "",
    val dhcp: Boolean = true,
    val listeners: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011",
    val rpcPortal: String = "0.0.0.0:0",
    val networkName: String = "easytier",
    val networkSecret: String = "",
    val peers: String = "tcp://public.easytier.top:11010",
    val enableKcpProxy: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val latencyFirst: Boolean = false,
    val privateMode: Boolean = false
)