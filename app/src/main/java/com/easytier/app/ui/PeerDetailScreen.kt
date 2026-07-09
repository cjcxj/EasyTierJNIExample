package com.easytier.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easytier.jni.FinalPeerInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDetailScreen(peer: FinalPeerInfo, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PeerHeaderCard(peer = peer)
            PeerSectionCard(
                title = "连接状态",
                icon = Icons.Default.Cable
            ) {
                StatusRow("节点名称:", peer.hostname)
                StatusRow("虚拟 IP:", peer.virtualIp, isCopyable = true)
                StatusRow(
                    "连接类型:",
                    if (peer.isDirectConnection) "直连 (P2P)" else "中转 (Relay)"
                )
                StatusRow(
                    if (peer.isDirectConnection) "物理地址/端口:" else "下一跳节点:",
                    peer.connectionDetails,
                    isCopyable = true
                )
            }
            PeerSectionCard(
                title = "性能与路由",
                icon = Icons.Default.Speed
            ) {
                StatusRow("延迟:", peer.latency)
                StatusRow("收/发流量:", peer.traffic)
                StatusRow("路由成本 (Cost):", peer.routeCost.toString())
                StatusRow("下一跳 ID:", peer.nextHopPeerId.toString())
            }
            PeerSectionCard(
                title = "节点信息",
                icon = Icons.Default.Info
            ) {
                StatusRow("版本号:", peer.version)
                StatusRow("NAT 类型:", peer.natType)
                StatusRow("节点 ID:", peer.peerId.toString())
                StatusRow("实例 ID:", peer.instId)
            }
        }
    }
}

@Composable
private fun PeerHeaderCard(peer: FinalPeerInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = peer.hostname.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = peer.hostname,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = peer.virtualIp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeerSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
