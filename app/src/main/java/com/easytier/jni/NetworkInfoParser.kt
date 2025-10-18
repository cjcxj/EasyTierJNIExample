package com.easytier.jni

import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow

/**
 * 【JSON解析工具类】
 * 负责将从EasyTier JNI获取的JSON字符串解析为结构化的Kotlin数据对象。
 * 这是一个 object (单例)，可以直接通过 NetworkInfoParser.parse(...) 调用。
 */
object NetworkInfoParser {

    // --- 公共入口函数 ---
    /**
     * 主解析函数，将完整的JSON字符串转换为我们定义的数据结构。
     * @param jsonString 从JNI获取的原始JSON字符串。
     * @param instanceName 当前运行的实例名称。
     * @return 一个包含所有解析信息的 DetailedNetworkInfo 对象。
     * @throws org.json.JSONException 如果JSON格式有问题或缺少关键字段。
     */
    fun parse(jsonString: String, instanceName: String): DetailedNetworkInfo {
        val root = JSONObject(jsonString)
        val instance = root.getJSONObject("map").getJSONObject(instanceName)

        val myNode = parseMyNodeInfo(instance.getJSONObject("my_node_info"))
        val events = parseEvents(instance.getJSONArray("events"))
        val routesMap = parseRoutes(instance.getJSONArray("routes"))
        val peersMap = parsePeers(instance.getJSONArray("peers"))

        val finalPeerList = mutableListOf<FinalPeerInfo>()
        routesMap.values.forEach { route ->
            val peerConn = peersMap[route.peerId]
            if (peerConn != null) {
                finalPeerList.add(
                    FinalPeerInfo(
                        hostname = route.hostname,
                        virtualIp = route.virtualIp,
                        isDirectConnection = true,
                        connectionDetails = peerConn.physicalAddr,
                        latency = "${peerConn.latencyUs / 1000} ms",
                        traffic = "${formatBytes(peerConn.rxBytes)} / ${formatBytes(peerConn.txBytes)}"
                    )
                )
            } else {
                val nextHopHostname = routesMap[route.nextHopPeerId]?.hostname ?: "未知"
                finalPeerList.add(
                    FinalPeerInfo(
                        hostname = route.hostname,
                        virtualIp = route.virtualIp,
                        isDirectConnection = false,
                        connectionDetails = "通过 $nextHopHostname",
                        latency = "${route.pathLatency} ms (路径)",
                        traffic = "N/A"
                    )
                )
            }
        }

        return DetailedNetworkInfo(
            myNode = myNode,
            events = events,
            finalPeerList = finalPeerList.sortedBy { it.hostname }
        )
    }

    // --- 私有解析函数 ---

    private fun parseMyNodeInfo(myNodeJson: JSONObject): MyNodeInfo {
        val myStunInfoJson = myNodeJson.getJSONObject("stun_info")
        val ipsJson = myNodeJson.getJSONObject("ips")
        val virtualIpv4Json = myNodeJson.getJSONObject("virtual_ipv4")
        val virtualIpAddr = parseIntegerToIp(virtualIpv4Json.getJSONObject("address").getInt("addr"))
        val virtualIpPrefix = virtualIpv4Json.getInt("network_length")
        val virtualIp = "$virtualIpAddr/$virtualIpPrefix"
        val listenersArray = myNodeJson.getJSONArray("listeners")
        val listenersList = (0 until listenersArray.length()).map { listenersArray.getJSONObject(it).getString("url") }
        val interfaceIpsArray = ipsJson.getJSONArray("interface_ipv4s")
        val interfaceIpsList = (0 until interfaceIpsArray.length()).map { parseIntegerToIp(interfaceIpsArray.getJSONObject(it).getInt("addr")) }
        val publicIpsArray = myStunInfoJson.getJSONArray("public_ip")
        val publicIpsStr = if (publicIpsArray.length() > 0) {
            (0 until publicIpsArray.length()).joinToString(", ") { publicIpsArray.getString(it) }
        } else "N/A"

        return MyNodeInfo(
            hostname = myNodeJson.getString("hostname"),
            version = myNodeJson.getString("version"),
            virtualIp = virtualIp,
            publicIp = publicIpsStr,
            natType = parseNatType(myStunInfoJson.getInt("udp_nat_type")),
            listeners = listenersList,
            interfaceIps = interfaceIpsList
        )
    }

    private fun parseEvents(eventsJson: org.json.JSONArray): List<EventInfo> {
        return (0 until eventsJson.length()).map {
            val eventStr = eventsJson.getString(it)
            val eventJson = JSONObject(eventStr)
            val time = eventJson.getString("time").substring(11, 19)
            val message = eventJson.getJSONObject("event").toString().replace("\"", "").replace(":", ": ").replace(",", ", ")
            EventInfo(time, message)
        }.take(20)
    }

    private fun parseRoutes(routesJson: org.json.JSONArray): Map<Long, RouteData> {
        return (0 until routesJson.length()).associate {
            val route = routesJson.getJSONObject(it)
            val peerId = route.getLong("peer_id")
            val ipv4AddrJson = route.optJSONObject("ipv4_addr")
            val virtualIp = if (ipv4AddrJson != null) parseIntegerToIp(ipv4AddrJson.getJSONObject("address").getInt("addr")) else "无虚拟IP"
            peerId to RouteData(
                peerId = peerId,
                hostname = route.getString("hostname"),
                virtualIp = virtualIp,
                nextHopPeerId = route.getLong("next_hop_peer_id"),
                pathLatency = route.getInt("path_latency"),
                cost = route.getInt("cost")
            )
        }
    }

    private fun parsePeers(peersJson: org.json.JSONArray): Map<Long, PeerConnectionData> {
        val peersMap = mutableMapOf<Long, PeerConnectionData>()
        for (i in 0 until peersJson.length()) {
            val peer = peersJson.getJSONObject(i)
            val conns = peer.getJSONArray("conns")
            if (conns.length() > 0) {
                val conn = conns.getJSONObject(0)
                val peerId = conn.getLong("peer_id")
                peersMap[peerId] = PeerConnectionData(
                    peerId = peerId,
                    physicalAddr = conn.getJSONObject("tunnel").getJSONObject("remote_addr").getString("url"),
                    latencyUs = conn.getJSONObject("stats").getLong("latency_us"),
                    rxBytes = conn.getJSONObject("stats").getLong("rx_bytes"),
                    txBytes = conn.getJSONObject("stats").getLong("tx_bytes")
                )
            }
        }
        return peersMap
    }

    // --- 辅助函数 ---

    private fun parseIntegerToIp(addr: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            (addr ushr 24) and 0xFF, (addr ushr 16) and 0xFF, (addr ushr 8) and 0xFF, addr and 0xFF
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
    }

    private fun parseNatType(typeCode: Int): String {
        return when (typeCode) {
            0 -> "Unknown (未知类型)"; 1 -> "Open Internet (开放互联网)"; 2 -> "No PAT (无端口转换)"
            3 -> "Full Cone (完全锥形)"; 4 -> "Restricted Cone (限制锥形)"; 5 -> "Port Restricted (端口限制锥形)"
            6 -> "Symmetric (对称型)"; 7 -> "Symmetric UDP Firewall (对称UDP防火墙)"
            8 -> "Symmetric Easy Inc (对称型-端口递增)"; 9 -> "Symmetric Easy Dec (对称型-端口递减)"
            else -> "Other Type ($typeCode)"
        }
    }
}

// --- 数据模型 (Data Models) ---

data class DetailedNetworkInfo(
    val myNode: MyNodeInfo,
    val events: List<EventInfo>,
    val finalPeerList: List<FinalPeerInfo>
)

data class MyNodeInfo(
    val hostname: String, val version: String, val virtualIp: String,
    val publicIp: String, val natType: String, val listeners: List<String>,
    val interfaceIps: List<String>
)

data class EventInfo(val time: String, val message: String)

data class RouteData(
    val peerId: Long, val hostname: String, val virtualIp: String,
    val nextHopPeerId: Long, val pathLatency: Int, val cost: Int
)

data class PeerConnectionData(
    val peerId: Long, val physicalAddr: String, val latencyUs: Long,
    val rxBytes: Long, val txBytes: Long
)

data class FinalPeerInfo(
    val hostname: String, val virtualIp: String, val isDirectConnection: Boolean,
    val connectionDetails: String, val latency: String, val traffic: String
)