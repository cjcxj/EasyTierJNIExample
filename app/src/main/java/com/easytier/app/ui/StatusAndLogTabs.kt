package com.easytier.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusDashboard(status = status, isRunning = isRunning)

        if (detailedInfo != null) {
            MetricsGrid(info = detailedInfo)
            Spacer(Modifier.height(4.dp))
            DetailedInfoCard(
                info = detailedInfo,
                onRefresh = onRefreshDetailedInfo,
                onPeerClick = onPeerClick,
                onCopyJsonClick = onCopyJsonClick,
                isRunning = isRunning
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "服务运行时将自动显示详细信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDashboard(status: EasyTierManager.EasyTierStatus?, isRunning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (isRunning)
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        else
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                    ),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                            .then(
                                if (isRunning) Modifier.background(
                                    Color(0xFF4CAF50).copy(alpha = pulseAlpha),
                                    CircleShape
                                )
                                else Modifier
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isRunning) "服务运行中" else "服务已停止",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = (if (isRunning) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(12.dp))
                DashboardRow(
                    label = "实例名称",
                    value = status?.instanceName ?: "暂无",
                    isLight = isRunning
                )
                DashboardRow(
                    label = "虚拟 IPv4",
                    value = status?.currentIpv4 ?: "暂无",
                    isLight = isRunning,
                    isCopyable = true
                )
            }
        }
    }
}

@Composable
private fun DashboardRow(
    label: String,
    value: String,
    isLight: Boolean,
    isCopyable: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = (if (isLight) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isLight) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MetricsGrid(info: DetailedNetworkInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.Person,
                label = "本机",
                value = info.myNode?.hostname ?: "未知",
                color = MaterialTheme.colorScheme.primary
            )
            MetricCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.Public,
                label = "公网 IP",
                value = info.myNode?.publicIp?.ifEmpty { "未知" } ?: "未知",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.Shuffle,
                label = "NAT 类型",
                value = info.myNode?.natType?.ifEmpty { "未知" } ?: "未知",
                color = MaterialTheme.colorScheme.secondary
            )
            MetricCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.Group,
                label = "对等节点",
                value = "${info.finalPeerList.size}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = color.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DetailedInfoCard(
    info: DetailedNetworkInfo?,
    onRefresh: () -> Unit,
    onPeerClick: (FinalPeerInfo) -> Unit,
    onCopyJsonClick: () -> Unit,
    isRunning: Boolean
) {
    val refreshInteractionSource = remember { MutableInteractionSource() }
    val copyInteractionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "对等节点列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    IconButton(
                        onClick = onRefresh,
                        interactionSource = refreshInteractionSource,
                        modifier = Modifier.pressableScale(interactionSource = refreshInteractionSource)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (info == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "服务运行时将自动显示详细信息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (info.myNode != null) {
                    InfoSection(title = "本机信息") {
                        StatusRow("主机名:", info.myNode.hostname)
                        StatusRow("版本:", info.myNode.version)
                        StatusRow("虚拟 IPv4:", info.myNode.virtualIp, isCopyable = true)
                    }
                    InfoSection(title = "STUN 探测信息") {
                        StatusRow("公网 IP:", info.myNode.publicIp, isCopyable = true)
                        StatusRow("NAT 类型:", info.myNode.natType)
                    }
                    InfoSection(title = "监听器") {
                        Text(
                            info.myNode.listeners.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    InfoSection(title = "接口 IP 地址") {
                        Text(
                            info.myNode.interfaceIps.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "对等节点",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${info.finalPeerList.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                for (i in info.finalPeerList.indices) {
                    val peer = info.finalPeerList[i]
                    val animationDelay = i * 50
                    PeerCard(
                        peer = peer,
                        onClick = { onPeerClick(peer) },
                        animationDelay = animationDelay
                    )
                    if (i < info.finalPeerList.size - 1) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onCopyJsonClick,
                    interactionSource = copyInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableScale(interactionSource = copyInteractionSource),
                    enabled = isRunning,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("复制网络信息 (JSON)")
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun PeerCard(
    peer: FinalPeerInfo,
    onClick: () -> Unit,
    animationDelay: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) +
                slideInVertically(animationSpec = tween(400)) { it / 2 }
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (peer.isDirectConnection)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (peer.isDirectConnection)
                                Icons.Default.Wifi
                            else                                 Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (peer.isDirectConnection)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            peer.hostname,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        if (!peer.isDirectConnection) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "中转",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            peer.virtualIp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            peer.latency,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LogTab(rawEvents: List<String>, onExportClicked: () -> Unit) {
    val parsedEvents by remember(rawEvents) {
        derivedStateOf {
            rawEvents.mapNotNull { NetworkInfoParser.parseSingleRawEvent(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "事件日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FilledTonalButton(
                onClick = onExportClicked,
                enabled = parsedEvents.isNotEmpty(),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("导出日志", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (parsedEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "服务运行时将在此处显示配置和事件日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val lazyListState = rememberLazyListState()
            LaunchedEffect(parsedEvents.size) {
                if (parsedEvents.isNotEmpty()) {
                    lazyListState.animateScrollToItem(0)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFF1A1A2E)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    state = lazyListState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = parsedEvents.asReversed(),
                        key = { it.rawTime }
                    ) { event ->
                        val logColor = when (event.level) {
                            EventInfo.Level.SUCCESS -> Color(0xFF66BB6A)
                            EventInfo.Level.ERROR -> Color(0xFFEF5350)
                            EventInfo.Level.WARNING -> Color(0xFFFFCA28)
                            EventInfo.Level.INFO -> Color(0xFFB0BEC5)
                            EventInfo.Level.CONFIG -> Color(0xFF4DD0E1)
                        }

                        val logText = if (event.level == EventInfo.Level.CONFIG) {
                            event.message
                        } else {
                            "[${event.time}] ${event.message}"
                        }

                        Text(
                            text = logText,
                            color = logColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
