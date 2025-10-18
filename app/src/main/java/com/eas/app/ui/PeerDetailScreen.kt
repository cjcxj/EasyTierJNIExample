package com.easytier.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
                title = { Text("节点详情: ${peer.hostname}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("连接状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Divider(Modifier.padding(vertical = 8.dp))
                    
                    StatusRow("节点名称:", peer.hostname)
                    StatusRow("虚拟 IP:", peer.virtualIp)
                    StatusRow("连接类型:", if (peer.isDirectConnection) "直连 (P2P)" else "中转 (Relay)")
                    StatusRow(if (peer.isDirectConnection) "物理地址/端口:" else "下一跳节点:", peer.connectionDetails)
                    StatusRow("延迟 (ms):", peer.latency)
                    StatusRow("收/发流量:", peer.traffic)
                    
                    // 假设可以在 FinalPeerInfo 中添加更多连接信息
                    // Text("\n所有连接信息:", style = MaterialTheme.typography.titleMedium)
                    // ... (如果 FinalPeerInfo 包含完整的 ConnectionData 列表，可以在此展示)
                }
            }
        }
    }
}
