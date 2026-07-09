package com.easytier.jni

import android.util.Log
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow

/**
 * 【JSON解析工具类】
 * 负责将从EasyTier JNI获取的JSON字符串解析为结构化的Kotlin数据对象。
 * 兼容 protobuf JSON snake_case 输出格式。
 */
object NetworkInfoParser {

    private const val TAG = "NetworkInfoParser"

    // --- 公共入口函数 ---

    /**
     * 主解析函数，用于解析完整的网络快照。
     * @param jsonString 从JNI获取的原始JSON字符串。
     * @param instanceName 当前运行的实例名称。
     * @return 一个包含所有解析信息的 DetailedNetworkInfo 对象。
     */
    fun parse(jsonString: String, instanceName: String): DetailedNetworkInfo {
        return try {
            val root = JSONObject(jsonString)
            val mapObj = root.optJSONObject("map")
            if (mapObj == null) {
                Log.w(TAG, "JSON root missing 'map' key. Keys: ${root.keys().asSequence().toList()}")
                return DetailedNetworkInfo(myNode = null, events = emptyList(), finalPeerList = emptyList())
            }

            val instance = mapObj.optJSONObject(instanceName)
            if (instance == null) {
                Log.w(TAG, "Map missing instance '$instanceName'. Available keys: ${mapObj.keys().asSequence().toList()}")
                return DetailedNetworkInfo(myNode = null, events = emptyList(), finalPeerList = emptyList())
            }

            val myNode = parseMyNodeInfo(instance.optJSONObject("my_node_info"))
            val routesMap = parseRoutes(instance.optJSONArray("routes"))
            val peersMap = parsePeers(instance.optJSONArray("peers"))
            val snapshotEvents = parseEventList(instance.optJSONArray("events"))

            val finalPeerList = buildFinalPeerList(routesMap, peersMap)

            DetailedNetworkInfo(
                myNode = myNode,
                events = snapshotEvents,
                finalPeerList = finalPeerList.sortedBy { it.hostname }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse network info snapshot", e)
            DetailedNetworkInfo(myNode = null, events = emptyList(), finalPeerList = emptyList())
        }
    }

    /**
     * 从完整的网络快照JSON中，提取出原始的事件JSON字符串数组。
     * 供 ViewModel 的增量日志收集逻辑调用。
     */
    fun extractRawEventStrings(jsonString: String, instanceName: String): List<String> {
        return try {
            val root = JSONObject(jsonString)
            val instance = root.getJSONObject("map").getJSONObject(instanceName)
            val eventsArray = instance.getJSONArray("events")
            (0 until eventsArray.length()).map { eventsArray.getString(it) }.reversed()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract raw event strings", e)
            emptyList()
        }
    }

    // --- 私有解析函数 ---

    private fun parseMyNodeInfo(myNodeJson: JSONObject?): MyNodeInfo? {
        if (myNodeJson == null) {
            Log.w(TAG, "my_node_info is null")
            return null
        }
        return try {
            val myStunInfoJson = myNodeJson.optJSONObject("stun_info")
            val ipsJson = myNodeJson.optJSONObject("ips")
            val virtualIpv4Json = myNodeJson.optJSONObject("virtual_ipv4")
            val virtualIp = if (virtualIpv4Json != null) {
                val addrJson = virtualIpv4Json.optJSONObject("address")
                val addr = addrJson?.optInt("addr", 0) ?: 0
                val netLen = virtualIpv4Json.optInt("network_length", 24)
                "${parseIntegerToIp(addr)}/$netLen"
            } else {
                "正在获取中..."
            }

            val listenersList = myNodeJson.optJSONArray("listeners")?.let { arr ->
                (0 until arr.length()).map { i ->
                    arr.optJSONObject(i)?.optString("url", "") ?: ""
                }.filter { it.isNotBlank() }
            } ?: emptyList()

            val interfaceIpsList = ipsJson?.optJSONArray("interface_ipv4s")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val addr = arr.optJSONObject(i)?.optInt("addr", 0) ?: 0
                    parseIntegerToIp(addr)
                }
            } ?: emptyList()

            val publicIpsArray = myStunInfoJson?.optJSONArray("public_ip")
            val publicIpsStr = if (publicIpsArray != null && publicIpsArray.length() > 0) {
                (0 until publicIpsArray.length()).joinToString(", ") { publicIpsArray.optString(it, "") }
            } else {
                "N/A"
            }

            val natTypeRaw = myStunInfoJson?.opt("udp_nat_type")

            MyNodeInfo(
                hostname = myNodeJson.optString("hostname", "未知"),
                version = myNodeJson.optString("version", ""),
                virtualIp = virtualIp,
                publicIp = publicIpsStr,
                natType = parseNatType(natTypeRaw),
                listeners = listenersList,
                interfaceIps = interfaceIpsList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse my_node_info", e)
            null
        }
    }

    private fun parseRoutes(routesJson: JSONArray?): Map<Long, RouteData> {
        if (routesJson == null) return emptyMap()
        return try {
            (0 until routesJson.length()).mapNotNull { i ->
                try {
                    val route = routesJson.getJSONObject(i)
                    val peerId = route.optLong("peer_id", -1)
                    if (peerId < 0) return@mapNotNull null
                    val ipv4AddrJson = route.optJSONObject("ipv4_addr")
                    val virtualIp = if (ipv4AddrJson != null) {
                        val addr = ipv4AddrJson.optJSONObject("address")?.optInt("addr", 0) ?: 0
                        parseIntegerToIp(addr)
                    } else "无虚拟IP"

                    val stunInfoJson = route.optJSONObject("stun_info")
                    val natTypeRaw = stunInfoJson?.opt("udp_nat_type")

                    peerId to RouteData(
                        peerId = peerId,
                        hostname = route.optString("hostname", "未知"),
                        virtualIp = virtualIp,
                        nextHopPeerId = route.optLong("next_hop_peer_id", 0),
                        pathLatency = route.optInt("path_latency", 0),
                        cost = route.optInt("cost", 0),
                        version = route.optString("version", ""),
                        natType = parseNatType(natTypeRaw),
                        instId = route.optString("inst_id", "")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse route at index $i", e)
                    null
                }
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse routes", e)
            emptyMap()
        }
    }

    private fun parsePeers(peersJson: JSONArray?): Map<Long, PeerConnectionData> {
        if (peersJson == null) return emptyMap()
        val peersMap = mutableMapOf<Long, PeerConnectionData>()
        try {
            for (i in 0 until peersJson.length()) {
                try {
                    val peer = peersJson.getJSONObject(i)
                    val conns = peer.optJSONArray("conns") ?: continue
                    if (conns.length() == 0) continue
                    val conn = conns.optJSONObject(0) ?: continue
                    val peerId = conn.optLong("peer_id", -1)
                    if (peerId < 0) continue

                    val tunnelJson = conn.optJSONObject("tunnel")
                    val remoteAddrJson = tunnelJson?.optJSONObject("remote_addr")
                    val physicalAddr = remoteAddrJson?.optString("url", "未知") ?: "未知"

                    val statsJson = conn.optJSONObject("stats")
                    val latencyUs = statsJson?.optLong("latency_us", 0) ?: 0
                    val rxBytes = statsJson?.optLong("rx_bytes", 0) ?: 0
                    val txBytes = statsJson?.optLong("tx_bytes", 0) ?: 0

                    peersMap[peerId] = PeerConnectionData(
                        peerId = peerId,
                        physicalAddr = physicalAddr,
                        latencyUs = latencyUs,
                        rxBytes = rxBytes,
                        txBytes = txBytes
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse peer at index $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse peers", e)
        }
        return peersMap
    }

    private fun buildFinalPeerList(
        routesMap: Map<Long, RouteData>,
        peersMap: Map<Long, PeerConnectionData>
    ): List<FinalPeerInfo> {
        return routesMap.values.map { route ->
            val peerConn = peersMap[route.peerId]
            if (peerConn != null) {
                FinalPeerInfo(
                    hostname = route.hostname,
                    virtualIp = route.virtualIp,
                    isDirectConnection = true,
                    connectionDetails = peerConn.physicalAddr,
                    latency = "${peerConn.latencyUs / 1000} ms",
                    traffic = "${formatBytes(peerConn.rxBytes)} / ${formatBytes(peerConn.txBytes)}",
                    version = route.version,
                    natType = route.natType,
                    routeCost = route.cost,
                    nextHopPeerId = route.nextHopPeerId,
                    peerId = route.peerId,
                    instId = route.instId
                )
            } else {
                val nextHopHostname = routesMap[route.nextHopPeerId]?.hostname ?: "未知"
                FinalPeerInfo(
                    hostname = route.hostname,
                    virtualIp = route.virtualIp,
                    isDirectConnection = false,
                    connectionDetails = "通过 $nextHopHostname",
                    latency = "${route.pathLatency} ms (路径)",
                    traffic = "N/A",
                    version = route.version,
                    natType = route.natType,
                    routeCost = route.cost,
                    nextHopPeerId = route.nextHopPeerId,
                    peerId = route.peerId,
                    instId = route.instId
                )
            }
        }
    }

    private fun parseEventList(eventsJson: JSONArray?): List<EventInfo> {
        if (eventsJson == null) return emptyList()
        val rawEventStrings = try {
            (0 until eventsJson.length()).map { eventsJson.getString(it) }.reversed()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse event list", e)
            emptyList()
        }
        return rawEventStrings.mapNotNull { parseSingleRawEvent(it) }
    }

    fun parseSingleRawEvent(eventStr: String): EventInfo? {
        return try {
            val eventJson = JSONObject(eventStr)
            val rawTime = eventJson.getString("time")
            val time = rawTime.substring(11, 19)
            val eventObject = eventJson.getJSONObject("event")
            val eventType = eventObject.keys().next()

            val (message, level) = when (eventType) {
                "GeneratedTomlConfig" -> {
                    val tomlContent = eventObject.getString("GeneratedTomlConfig")
                    tomlContent to EventInfo.Level.INFO
                }

                "PeerConnAdded" -> {
                    val conn = eventObject.getJSONObject("PeerConnAdded")
                    val peerId = conn.optLong("peer_id", 0).toString().takeLast(4)
                    val tunnelJson = conn.optJSONObject("tunnel")
                    val tunnelType = tunnelJson?.optString("tunnel_type", "?")?.uppercase() ?: "?"
                    val remoteAddr = tunnelJson?.optJSONObject("remote_addr")
                        ?.optString("url", "未知") ?: "未知"
                    "[$tunnelType] 节点($peerId)已连接: $remoteAddr" to EventInfo.Level.SUCCESS
                }

                "PeerConnRemoved" -> "节点(${
                    eventObject.optJSONObject("PeerConnRemoved")
                        ?.optLong("peer_id", 0).toString().takeLast(4)
                })连接已断开" to EventInfo.Level.WARNING

                "PeerAdded" -> "发现新节点(${
                    eventObject.optLong("PeerAdded", 0).toString().takeLast(4)
                })" to EventInfo.Level.INFO

                "PeerRemoved" -> "节点(${
                    eventObject.optLong("PeerRemoved", 0).toString().takeLast(4)
                })已移除" to EventInfo.Level.WARNING

                "ConnectionAccepted" -> {
                    val arr = eventObject.optJSONArray("ConnectionAccepted")
                    val addr = arr?.optString(1, "未知") ?: "未知"
                    "接受来自 $addr 的连接" to EventInfo.Level.SUCCESS
                }

                "ConnectionError" -> {
                    val arr = eventObject.optJSONArray("ConnectionError")
                    val msg = arr?.optString(2, "未知错误") ?: "未知错误"
                    "连接错误: $msg" to EventInfo.Level.ERROR
                }

                "ListenerAdded" -> "开始监听: ${eventObject.optString("ListenerAdded", "")}" to EventInfo.Level.INFO
                "Connecting" -> "正在连接: ${eventObject.optString("Connecting", "")}" to EventInfo.Level.INFO
                "TunDeviceReady" -> "虚拟网卡已就绪" to EventInfo.Level.SUCCESS
                "DhcpIpv4Changed" -> {
                    val arr = eventObject.optJSONArray("DhcpIpv4Changed")
                    val oldIp = arr?.optString(0, "无") ?: "无"
                    val newIp = arr?.optString(1, "N/A") ?: "N/A"
                    "DHCP IP 变更: $oldIp -> $newIp" to EventInfo.Level.INFO
                }

                else -> {
                    val content = eventObject.opt(eventType)?.toString() ?: ""
                    "$eventType: $content" to EventInfo.Level.INFO
                }
            }
            EventInfo(time, message, level, rawTime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse single event string: $eventStr", e)
            null
        }
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

    /**
     * 解析 NAT 类型。pbjson 将枚举序列化为字符串（如 "Symmetric"），
     * 但也兼容旧的整数格式（如 6）。
     */
    private fun parseNatType(raw: Any?): String {
        return when (raw) {
            is String -> natTypeFromName(raw)
            is Int -> natTypeFromCode(raw)
            else -> "Unknown (未知类型)"
        }
    }

    private fun natTypeFromName(name: String): String {
        return when (name) {
            "Unknown" -> "Unknown (未知类型)"
            "OpenInternet" -> "Open Internet (开放互联网)"
            "NoPAT" -> "No PAT (无端口转换)"
            "FullCone" -> "Full Cone (完全锥形)"
            "Restricted" -> "Restricted Cone (限制锥形)"
            "PortRestricted" -> "Port Restricted (端口限制锥形)"
            "Symmetric" -> "Symmetric (对称型)"
            "SymUdpFirewall" -> "Symmetric UDP Firewall (对称UDP防火墙)"
            "SymmetricEasyInc" -> "Symmetric Easy Inc (对称型-端口递增)"
            "SymmetricEasyDec" -> "Symmetric Easy Dec (对称型-端口递减)"
            else -> name
        }
    }

    private fun natTypeFromCode(typeCode: Int): String {
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
@Keep
data class DetailedNetworkInfo(
    val myNode: MyNodeInfo?,
    val events: List<EventInfo>,
    val finalPeerList: List<FinalPeerInfo>
)

@Keep
data class MyNodeInfo(
    val hostname: String,
    val version: String,
    val virtualIp: String,
    val publicIp: String,
    val natType: String,
    val listeners: List<String>,
    val interfaceIps: List<String>
)

@Keep
data class EventInfo(val time: String, val message: String, val level: Level, val rawTime: String) {
    enum class Level { INFO, SUCCESS, WARNING, ERROR, CONFIG }
}

@Keep
data class RouteData(
    val peerId: Long,
    val hostname: String,
    val virtualIp: String,
    val nextHopPeerId: Long,
    val pathLatency: Int,
    val cost: Int,
    val version: String,
    val natType: String,
    val instId: String
)

@Keep
data class PeerConnectionData(
    val peerId: Long,
    val physicalAddr: String,
    val latencyUs: Long,
    val rxBytes: Long,
    val txBytes: Long
)

@Keep
data class FinalPeerInfo(
    val hostname: String,
    val virtualIp: String,
    val isDirectConnection: Boolean,
    val connectionDetails: String,
    val latency: String,
    val traffic: String,
    val version: String,
    val natType: String,
    val routeCost: Int,
    val nextHopPeerId: Long,
    val peerId: Long,
    val instId: String
)
