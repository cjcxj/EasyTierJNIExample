package com.easytier.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.app.ui.StatusRow
import com.easytier.jni.DetailedNetworkInfo
import com.easytier.jni.EasyTierManager
import com.easytier.jni.EventInfo
import com.easytier.jni.FinalPeerInfo
import com.easytier.jni.NetworkInfoParser

@Composable
fun StatusTab(
    status: EasyTierManager.EasyTierStatus?,
    isRunning: Boolean,
    detailedInfo: DetailedNetworkInfo?,
    onRefreshDetailedInfo: () -> Unit,
    onPeerClick: (FinalPeerInfo) -> Unit,
    onCopyJsonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(status = status, isRunning = isRunning)
        DetailedInfoCard(
            info = detailedInfo,
            onRefresh = onRefreshDetailedInfo,
            onPeerClick = onPeerClick,
            onCopyJsonClick = onCopyJsonClick,
            isRunning = isRunning
        )
    }
}


/**
 * 日志展示界面组件，用于显示解析后的事件日志，并提供导出功能。
 *
 * @param rawEvents 原始的日志字符串列表，每个元素代表一条未解析的日志记录。
 * @param onExportClicked 当用户点击“导出原始日志”按钮时触发的回调函数。
 */
@Composable
fun LogTab(rawEvents: List<String>, onExportClicked: () -> Unit) {
    val parsedEvents by remember(rawEvents) {
        derivedStateOf {
            rawEvents.mapNotNull { NetworkInfoParser.parseSingleRawEvent(it) }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("日志 & 配置", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onExportClicked, enabled = parsedEvents.isNotEmpty()) {
                Icon(Icons.Default.Save, "导出", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("导出原始日志")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (parsedEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("服务运行时将在此处显示配置和事件日志。")
            }
        } else {
            val lazyListState = rememberLazyListState()
            LaunchedEffect(parsedEvents.size) {
                if (parsedEvents.isNotEmpty()) {
                    lazyListState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                state = lazyListState,
                reverseLayout = true,
            ) {
                items(
                    items = parsedEvents.asReversed(),
                    key = { it.rawTime }
                ) { event ->
                    val logColor = when (event.level) {
                        EventInfo.Level.SUCCESS -> Color(0xFF81C784)
                        EventInfo.Level.ERROR -> Color(0xFFE57373)
                        EventInfo.Level.WARNING -> Color(0xFFFFD54F)
                        EventInfo.Level.INFO -> Color.White
                        EventInfo.Level.CONFIG -> Color(0xFF80DEEA)
                    }

                    val fontSize = if (event.level == EventInfo.Level.CONFIG) 10.sp else 11.sp
                    val logText = if (event.level == EventInfo.Level.CONFIG) {
                        event.message
                    } else {
                        "[${event.time}] ${event.message}"
                    }

                    Text(
                        text = logText,
                        color = logColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// --- UI Components ---
@Composable
fun StatusCard(status: EasyTierManager.EasyTierStatus?, isRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("状态信息", style = MaterialTheme.typography.titleMedium)
            Divider(Modifier.padding(vertical = 8.dp))
            StatusRow("服务状态:", if (isRunning) "运行中" else "已停止")
            StatusRow("实例名称:", status?.instanceName ?: "暂无")
            StatusRow("虚拟 IPv4:", status?.currentIpv4 ?: "暂无", isCopyable = true)
        }
    }
}


/**
 * 显示详细的网络状态信息卡片。
 *
 * @param info 当前的详细网络信息，如果为 null 则显示提示信息。
 * @param onRefresh 刷新按钮点击时触发的回调函数。
 * @param onPeerClick 点击某个对等节点时触发的回调函数，传入被点击的节点信息。
 * @param onCopyJsonClick 复制网络信息为 JSON 按钮点击时触发的回调函数。
 * @param isRunning 表示服务是否正在运行，用于控制“复制网络信息”按钮的启用状态。
 */
@Composable
fun DetailedInfoCard(
    info: DetailedNetworkInfo?,
    onRefresh: () -> Unit,
    onPeerClick: (FinalPeerInfo) -> Unit,
    onCopyJsonClick: () -> Unit,
    isRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("详细网络状态", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))

            if (info == null) {
                Text(
                    "服务运行时将自动显示详细信息。",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
            } else {
                InfoSection(title = "本机信息") {
                    StatusRow("主机名:", info.myNode.hostname)
                    StatusRow("版本:", info.myNode.version)
                    StatusRow("虚拟IPv4:", info.myNode.virtualIp, isCopyable = true)
                }
                InfoSection(title = "STUN探测信息") {
                    StatusRow("公网 IP:", info.myNode.publicIp, isCopyable = true)
                    StatusRow("NAT 类型:", info.myNode.natType)
                }
                InfoSection(title = "监听器") {
                    Text(
                        info.myNode.listeners.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                }
                InfoSection(title = "接口IP地址") {
                    Text(
                        info.myNode.interfaceIps.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "对等节点 (${info.finalPeerList.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(info.finalPeerList) { peer ->
                            FinalPeerInfoItem(peer, onClick = { onPeerClick(peer) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCopyJsonClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = isRunning
            ) {
                Text("复制网络信息 (JSON)")
            }
        }
    }
}

@Composable
fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}


/**
 * 显示最终对端节点信息的可组合项
 *
 * @param peer 包含对端节点详细信息的数据对象
 * @param onClick 当用户点击该信息项时触发的回调函数
 */
@Composable
fun FinalPeerInfoItem(peer: FinalPeerInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    peer.hostname,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!peer.isDirectConnection) {
                    Text(
                        text = "中转",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Divider(Modifier.padding(vertical = 4.dp))
            StatusRow("虚拟 IP:", peer.virtualIp)
            StatusRow(
                if (peer.isDirectConnection) "物理地址:" else "下一跳:",
                peer.connectionDetails
            )
            StatusRow("延迟:", peer.latency)
            StatusRow("流量 (收/发):", peer.traffic)
        }
    }
}