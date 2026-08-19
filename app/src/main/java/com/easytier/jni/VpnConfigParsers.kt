package com.easytier.jni

/**
 * 纯字符串解析工具：与 Android 无关，便于 JVM 单元测试。
 */
object VpnConfigParsers {

    /** 解析形如 "10.0.0.1" 或 "10.0.0.1/24" 的 IPv4 地址，返回 (ip, prefix)。 */
    fun parseIpv4Address(ipv4Address: String): Pair<String, Int> {
        return if (ipv4Address.contains("/")) {
            val parts = ipv4Address.split("/")
            val ip = parts[0]
            val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 24
            Pair(ip, prefix)
        } else {
            Pair(ipv4Address, 24)
        }
    }

    /** 解析形如 "fd00::1" 或 "fd00::1/64" 的 IPv6 地址，返回 (ip, prefix)。 */
    fun parseIpv6Address(ipv6Address: String): Pair<String, Int> {
        return if (ipv6Address.contains("/")) {
            val parts = ipv6Address.split("/", limit = 2)
            val ip = parts[0]
            val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 64
            Pair(ip, prefix)
        } else {
            Pair(ipv6Address, 64)
        }
    }

    /** 解析 CIDR，要求必须包含 "/"；非法时抛 IllegalArgumentException。 */
    fun parseCidr(cidr: String): Pair<String, Int> {
        val parts = cidr.split("/")
        require(parts.size == 2) { "无效的 CIDR 格式: $cidr" }
        val ip = parts[0]
        val prefix = parts[1].toInt()
        return Pair(ip, prefix)
    }
}
