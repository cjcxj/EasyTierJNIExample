package com.easytier.jni

/**
 * RPC 预设：基于 EasyTier proto 定义。
 *
 * 注意：serviceName 必须以 "Service" 结尾，这是上游 FFI call_json_rpc 的 match 约定。
 * 例如 proto 中 `service PeerManageRpc` 对应的 serviceName 为 `api.instance.PeerManageRpcService`。
 *
 * payloadTemplate 中 `{instance}` 占位符在调用前会被替换为实际实例名。
 * domainName 仅 TcpProxyRpcService 使用（"tcp"/"kcp_src"/"kcp_dst"/"quic_src"/"quic_dst"）。
 *
 * 注：api.manage.WebClientService 不被 JNI 支持，已排除。
 */
data class RpcPreset(
    val displayName: String,
    val serviceName: String,
    val methodName: String,
    val domainName: String = "",
    val payloadTemplate: String,
    val description: String
)

object RpcServiceRegistry {

    /** 常用 RPC 预设列表 */
    val PRESETS: List<RpcPreset> = listOf(
        RpcPreset(
            displayName = "列出节点 (ListPeer)",
            serviceName = "api.instance.PeerManageRpcService",
            methodName = "list_peer",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出当前网络中所有对等节点信息"
        ),
        RpcPreset(
            displayName = "列出路由 (ListRoute)",
            serviceName = "api.instance.PeerManageRpcService",
            methodName = "list_route",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出当前节点的路由表"
        ),
        RpcPreset(
            displayName = "节点信息 (ShowNodeInfo)",
            serviceName = "api.instance.PeerManageRpcService",
            methodName = "show_node_info",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "展示当前节点详细信息"
        ),
        RpcPreset(
            displayName = "列出连接器 (ListConnector)",
            serviceName = "api.instance.ConnectorManageRpcService",
            methodName = "list_connector",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出所有连接器及其状态"
        ),
        RpcPreset(
            displayName = "VPN Portal 信息",
            serviceName = "api.instance.VpnPortalRpcService",
            methodName = "get_vpn_portal_info",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取 VPN Portal 配置与已连接客户端"
        ),
        RpcPreset(
            displayName = "TCP 代理表项 (ListTcpProxyEntry)",
            serviceName = "api.instance.TcpProxyRpcService",
            methodName = "list_tcp_proxy_entry",
            domainName = "tcp",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出 TCP 代理连接表项（需 domainName=tcp）"
        ),
        RpcPreset(
            displayName = "统计指标 (GetStats)",
            serviceName = "api.instance.StatsRpcService",
            methodName = "get_stats",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取节点运行指标快照"
        ),
        RpcPreset(
            displayName = "Prometheus 指标",
            serviceName = "api.instance.StatsRpcService",
            methodName = "get_prometheus_stats",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取 Prometheus 文本格式指标"
        ),
        RpcPreset(
            displayName = "ACL 统计 (GetAclStats)",
            serviceName = "api.instance.AclManageRpcService",
            methodName = "get_acl_stats",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取 ACL 命中统计"
        ),
        RpcPreset(
            displayName = "端口转发列表 (ListPortForward)",
            serviceName = "api.instance.PortForwardManageRpcService",
            methodName = "list_port_forward",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出已配置的端口转发规则"
        ),
        RpcPreset(
            displayName = "Mapped Listener 列表",
            serviceName = "api.instance.MappedListenerManageRpcService",
            methodName = "list_mapped_listener",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "列出映射监听器"
        ),
        RpcPreset(
            displayName = "日志配置 (GetLoggerConfig)",
            serviceName = "api.logger.LoggerRpcService",
            methodName = "get_logger_config",
            payloadTemplate = """{}""",
            description = "获取当前日志级别配置（无需 instance selector）"
        ),
        RpcPreset(
            displayName = "配置信息 (GetConfig)",
            serviceName = "api.config.ConfigRpcService",
            methodName = "get_config",
            payloadTemplate = """{"instance":{"instance_selector":{"name":"{instance}"}}}""",
            description = "获取当前实例的运行配置"
        )
    )

    /** 将模板中的 {instance} 占位符替换为实际实例名 */
    fun buildPayload(template: String, instanceName: String): String {
        return template.replace("{instance}", instanceName)
    }
}
