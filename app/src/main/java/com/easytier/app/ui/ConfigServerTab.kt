package com.easytier.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.jni.ConfigServerClientManager
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置服务器客户端 Tab：连接远程配置服务器，接收下发配置事件。
 *
 * 事件来源：
 * 1. FFI 回调（server 推送 run_network_instance / delete_network_instance）
 * 2. 本地连接状态变更（CONNECTING / CONNECTED / DISCONNECTED）
 */
@Composable
fun ConfigServerTab(
    manager: ConfigServerClientManager?,
    machineId: String,
    initialUrl: String,
    initialHostname: String,
    initialSecureMode: Boolean,
    initialAutoConnect: Boolean,
    onSettingsSaved: (url: String, hostname: String, secureMode: Boolean, autoConnect: Boolean) -> Unit,
    onDisconnect: () -> Unit = {}
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var hostname by remember(initialHostname) { mutableStateOf(initialHostname) }
    var secureMode by remember(initialSecureMode) { mutableStateOf(initialSecureMode) }
    var autoConnect by remember(initialAutoConnect) { mutableStateOf(initialAutoConnect) }

    val fallbackState = remember { MutableStateFlow(ConfigServerClientManager.ConnectionState.DISCONNECTED) }
    val connectionState by (manager?.connectionState ?: fallbackState).collectAsState()

    // 事件列表：每项 (时间, 可读摘要, 原始JSON, 级别)
    val events = remember { mutableStateListOf<EventEntry>() }

    fun now() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    fun addEvent(summary: String, rawJson: String? = null, level: EventLevel = EventLevel.INFO) {
        events.add(0, EventEntry(time = now(), summary = summary, rawJson = rawJson, level = level))
        if (events.size > 100) events.removeAt(events.size - 1)
    }

    // 收集 FFI 事件
    LaunchedEffect(manager) {
        manager?.events?.collectLatest { json ->
            val (summary, level) = formatEventJson(json)
            addEvent(summary = summary, rawJson = json, level = level)
        }
    }

    // 记录连接状态变更
    var lastState by remember { mutableStateOf(ConfigServerClientManager.ConnectionState.DISCONNECTED) }
    LaunchedEffect(connectionState) {
        if (connectionState != lastState) {
            when (connectionState) {
                ConfigServerClientManager.ConnectionState.CONNECTING ->
                    addEvent("⟳ 正在连接配置服务器 $url ...", level = EventLevel.CONNECTING)
                ConfigServerClientManager.ConnectionState.CONNECTED ->
                    addEvent("✓ 已连接到配置服务器", level = EventLevel.SUCCESS)
                ConfigServerClientManager.ConnectionState.DISCONNECTED -> {
                    if (lastState != ConfigServerClientManager.ConnectionState.DISCONNECTED) {
                        addEvent("✗ 已断开连接", level = EventLevel.ERROR)
                    }
                }
            }
            lastState = connectionState
        }
    }

    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === 连接配置区 ===
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("配置服务器连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                ConnectionStatusBadge(state = connectionState)

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器 URL") },
                    singleLine = true,
                    enabled = connectionState == ConfigServerClientManager.ConnectionState.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it.ifBlank { "" } },
                    label = { Text("主机名（可选，留空使用系统主机名）") },
                    singleLine = true,
                    enabled = connectionState == ConfigServerClientManager.ConnectionState.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = machineId,
                        onValueChange = {},
                        label = { Text("机器 ID（自动生成）") },
                        singleLine = true,
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(machineId))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制机器 ID")
                    }
                }

                ConfigSwitchWithInlineHelp(
                    label = "安全模式 (secureMode)",
                    checked = secureMode,
                    onCheckedChange = { secureMode = it },
                    helpText = "启用后使用加密通道与配置服务器通信，需配合密钥对使用。",
                    enabled = connectionState == ConfigServerClientManager.ConnectionState.DISCONNECTED
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoConnect,
                        onCheckedChange = {
                            autoConnect = it
                            onSettingsSaved(url, hostname, secureMode, it)
                        },
                        enabled = connectionState == ConfigServerClientManager.ConnectionState.DISCONNECTED
                    )
                    Text(
                        "启动应用时自动连接此服务器",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSettingsSaved(url, hostname, secureMode, autoConnect)
                            manager?.start(
                                url = url,
                                hostname = hostname.ifBlank { null },
                                machineId = machineId,
                                secureMode = secureMode
                            )
                        },
                        enabled = manager != null && connectionState == ConfigServerClientManager.ConnectionState.DISCONNECTED,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("连接")
                    }
                    OutlinedButton(
                        onClick = {
                            manager?.stop()
                            onDisconnect()
                        },
                        enabled = connectionState != ConfigServerClientManager.ConnectionState.DISCONNECTED,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("断开")
                    }
                }
            }
        }

        // === 事件流区 ===
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("事件日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "${events.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { events.clear() }) { Text("清空") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "连接状态变更和服务器下发的配置事件（启动/删除实例）均显示在此",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "点击「连接」开始，连接后将显示事件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        events.forEach { entry ->
                            EventItem(
                                entry = entry,
                                onCopy = {
                                    val text = entry.rawJson ?: entry.summary
                                    clipboardManager.setText(AnnotatedString(text))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 事件级别 */
enum class EventLevel { SUCCESS, ERROR, CONNECTING, INFO }

/** 解析 FFI 事件 JSON 为可读摘要和级别 */
private fun formatEventJson(json: String): Pair<String, EventLevel> {
    return try {
        val obj = JSONObject(json)
        val event = obj.optString("event", "unknown")
        val success = obj.optBoolean("success", true)
        val instName = obj.optString("instance_name", "")
        val netName = obj.optString("network_name", "")
        val error = obj.optString("error", "")
        val instId = obj.optString("instance_id", "")

        val icon = if (success) "✓" else "✗"
        val level = if (success) EventLevel.SUCCESS else EventLevel.ERROR

        val summary = when (event) {
            "run_network_instance" -> {
                val parts = mutableListOf<String>()
                if (instName.isNotEmpty()) parts.add("实例: $instName")
                if (netName.isNotEmpty()) parts.add("网络: $netName")
                if (instId.isNotEmpty()) parts.add("ID: ${instId.takeLast(8)}")
                if (!success && error.isNotEmpty() && error != "null") parts.add("错误: $error")
                "$icon 启动网络实例 ${parts.joinToString(", ")}"
            }
            "delete_network_instance" -> {
                val parts = mutableListOf<String>()
                if (instName.isNotEmpty()) parts.add("实例: $instName")
                if (!success && error.isNotEmpty() && error != "null") parts.add("错误: $error")
                "$icon 删除网络实例 ${parts.joinToString(", ")}"
            }
            else -> "$icon $event ${if (instName.isNotEmpty()) "($instName)" else ""}"
        }
        summary to level
    } catch (e: Exception) {
        json to EventLevel.INFO
    }
}

private data class EventEntry(
    val time: String,
    val summary: String,
    val rawJson: String?,
    val level: EventLevel = EventLevel.INFO
)

@Composable
private fun ConnectionStatusBadge(state: ConfigServerClientManager.ConnectionState) {
    val (color, text) = when (state) {
        ConfigServerClientManager.ConnectionState.DISCONNECTED -> Color.Gray to "已断开"
        ConfigServerClientManager.ConnectionState.CONNECTING -> Color(0xFFFFA000) to "连接中..."
        ConfigServerClientManager.ConnectionState.CONNECTED -> Color(0xFF4CAF50) to "已连接"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EventItem(entry: EventEntry, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val accentColor = when (entry.level) {
        EventLevel.SUCCESS -> Color(0xFF4CAF50)
        EventLevel.ERROR -> Color(0xFFEF5350)
        EventLevel.CONNECTING -> Color(0xFFFFA000)
        EventLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "[${entry.time}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                if (entry.rawJson != null) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制原始 JSON", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "展开/折叠 JSON",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onCopy, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = accentColor,
                fontWeight = if (entry.level == EventLevel.ERROR || entry.level == EventLevel.SUCCESS) FontWeight.Medium else FontWeight.Normal
            )
            if (expanded && entry.rawJson != null) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.rawJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
