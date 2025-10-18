package com.easytier.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easytier.jni.FinalPeerInfo
import com.easytier.app.ui.common.StatusRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDetailScreen(peer: FinalPeerInfo, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点详情: ${peer.hostname}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        //使用可滚动的Column并增加卡片间距
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 卡片1: 连接状态 ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("连接状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider(Modifier.padding(vertical = 8.dp))

                    StatusRow("节点名称:", peer.hostname)
                    StatusRow("虚拟 IP:", peer.virtualIp)
                    StatusRow("连接类型:", if (peer.isDirectConnection) "直连 (P2P)" else "中转 (Relay)")
                    StatusRow(if (peer.isDirectConnection) "物理地址/端口:" else "下一跳节点:", peer.connectionDetails)
                }
            }

            // --- 卡片2: 性能与路由 ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("性能与路由", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider(Modifier.padding(vertical = 8.dp))

                    StatusRow("延迟:", peer.latency)
                    StatusRow("收/发流量:", peer.traffic)
                    StatusRow("路由成本(Cost):", peer.routeCost.toString())
                    StatusRow("下一跳ID:", peer.nextHopPeerId.toString())
                }
            }

            // --- 卡片3: 节点信息 ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("节点信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider(Modifier.padding(vertical = 8.dp))

                    StatusRow("版本号:", peer.version)
                    StatusRow("NAT 类型:", peer.natType)
                    StatusRow("节点ID (Peer ID):", peer.peerId.toString())
                    StatusRow("实例ID:", peer.instId)
                }
            }
        }
    }
}
