package com.easytier.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.easytier.jni.RpcClient
import com.easytier.jni.RpcServiceRegistry
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class RpcHistoryItem(
    val serviceName: String,
    val methodName: String,
    val domainName: String,
    val payload: String,
    val response: String?,
    val error: String?,
    val durationMs: Long,
    val timestamp: String
)

/**
 * 通用 RPC 调试 Tab。
 *
 * 选择预设或手输 serviceName/methodName/domainName/payloadJson 后调用，
 * 展示响应、耗时与调用历史。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcDebugTab(
    rpcClient: RpcClient?,
    instanceName: String
) {
    // 预设下拉
    var presetExpanded by remember { mutableStateOf(false) }
    var selectedPresetName by rememberSaveable { mutableStateOf("") }

    // 输入字段
    var serviceName by rememberSaveable { mutableStateOf("api.instance.PeerManageRpcService") }
    var methodName by rememberSaveable { mutableStateOf("list_peer") }
    var domainName by rememberSaveable { mutableStateOf("") }
    var payloadJson by rememberSaveable { mutableStateOf("") }

    // 调用状态
    var calling by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<RpcClient.RpcResult?>(null) }

    // 调用历史
    val history = remember { mutableStateListOf<RpcHistoryItem>() }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // 当 instanceName 可用时，自动填充默认 payload
    LaunchedEffect(instanceName) {
        if (payloadJson.isBlank() && instanceName.isNotBlank()) {
            payloadJson = RpcServiceRegistry.buildPayload(
                RpcServiceRegistry.PRESETS.first().payloadTemplate,
                instanceName
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === 预设选择区 ===
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("RPC 调用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // 预设下拉（用 Box + DropdownMenu 避免暴露式下拉的 API 变更风险）
                Box {
                    OutlinedButton(
                        onClick = { presetExpanded = !presetExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !calling
                    ) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selectedPresetName.isBlank()) "选择预设（自动填充）" else selectedPresetName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        RpcServiceRegistry.PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(preset.displayName, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${preset.serviceName}.${preset.methodName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPresetName = preset.displayName
                                    serviceName = preset.serviceName
                                    methodName = preset.methodName
                                    domainName = preset.domainName
                                    payloadJson = RpcServiceRegistry.buildPayload(
                                        preset.payloadTemplate,
                                        instanceName
                                    )
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }

                // 当前实例提示
                if (instanceName.isNotBlank()) {
                    Text(
                        "当前实例: $instanceName（payload 中 {instance} 已替换）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // === 请求输入区 ===
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请求参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("serviceName") },
                    singleLine = true,
                    enabled = !calling,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = methodName,
                    onValueChange = { methodName = it },
                    label = { Text("methodName (snake_case)") },
                    singleLine = true,
                    enabled = !calling,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = domainName,
                    onValueChange = { domainName = it },
                    label = { Text("domainName (可选，仅 TcpProxyRpc 用)") },
                    singleLine = true,
                    enabled = !calling,
                    modifier = Modifier.fillMaxWidth()
                )

                // payloadJson 多行 + 美化按钮
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = payloadJson,
                        onValueChange = { payloadJson = it },
                        label = { Text("payloadJson") },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !calling,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        TextButton(
                            onClick = {
                                payloadJson = try {
                                    JSONObject(payloadJson).toString(2)
                                } catch (e: Exception) {
                                    payloadJson
                                }
                            },
                            enabled = !calling && payloadJson.isNotBlank()
                        ) {
                            Icon(Icons.Default.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("美化")
                        }
                    }
                }

                // 调用按钮
                Button(
                    onClick = {
                        if (rpcClient == null || calling) return@Button
                        calling = true
                        scope.launch {
                            val result = rpcClient.call(
                                serviceName = serviceName,
                                methodName = methodName,
                                domainName = domainName.ifBlank { null },
                                payloadJson = payloadJson
                            )
                            lastResult = result
                            calling = false

                            val time = java.text.SimpleDateFormat(
                                "HH:mm:ss",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date())
                            history.add(0, RpcHistoryItem(
                                serviceName = serviceName,
                                methodName = methodName,
                                domainName = domainName,
                                payload = payloadJson,
                                response = result.response,
                                error = result.error,
                                durationMs = result.durationMs,
                                timestamp = time
                            ))
                            if (history.size > 10) history.removeAt(history.size - 1)
                        }
                    },
                    enabled = rpcClient != null && !calling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (calling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("调用中...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("调用 RPC")
                    }
                }
            }
        }

        // === 响应展示区 ===
        lastResult?.let { result ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("响应", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (result.success) Color(0xFF4CAF50) else Color(0xFFE53935)
                        ) {
                            Text(
                                if (result.success) "成功" else "失败",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${result.durationMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        val content = result.response ?: result.error ?: ""
                        if (content.isNotBlank()) {
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(content)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (result.error != null) {
                        Text(
                            result.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    result.response?.let { resp ->
                        val pretty = remember(resp) {
                            try { JSONObject(resp).toString(2) } catch (e: Exception) { resp }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                pretty,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // === 调用历史区 ===
        if (history.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("调用历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "${history.size}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { history.clear() }) { Text("清空") }
                    }
                    Spacer(Modifier.height(8.dp))

                    history.forEach { item ->
                        RpcHistoryRow(item = item, onClick = {
                            serviceName = item.serviceName
                            methodName = item.methodName
                            domainName = item.domainName
                            payloadJson = item.payload
                            lastResult = RpcClient.RpcResult(
                                success = item.response != null,
                                response = item.response,
                                error = item.error,
                                durationMs = item.durationMs
                            )
                        })
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RpcHistoryRow(item: RpcHistoryItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (item.response != null) Color(0xFF4CAF50) else Color(0xFFE53935)
                ) {
                    Box(modifier = Modifier.size(8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "[${item.timestamp}] ${item.methodName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${item.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClick) { Text("回填") }
            }
            Text(
                "${item.serviceName}${if (item.domainName.isNotBlank()) " @${item.domainName}" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
