package com.easytier.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.easytier.jni.DataPlaneClient
import kotlinx.coroutines.launch

/**
 * 数据面调试 Tab：通过 EasyTier 虚拟网络进行 TCP/UDP 通信测试。
 *
 * 仅当 EasyTier 实例运行时（dataPlaneClient != null）启用控件。
 */
@Composable
fun DataPlaneDebugTab(
    dataPlaneClient: DataPlaneClient?,
    isRunning: Boolean
) {
    val enabled = isRunning && dataPlaneClient != null
    val scope = rememberCoroutineScope()

    // 统一日志（最新在顶）
    val logs = remember { mutableStateListOf<String>() }
    fun log(msg: String) {
        val time = java.time.LocalTime.now().toString().substring(0, 12)
        logs.add(0, "[$time] $msg")
        if (logs.size > 200) logs.removeAt(logs.size - 1)
    }

    // TCP 客户端状态
    var tcpClientIp by remember { mutableStateOf("10.147.0.2") }
    var tcpClientPort by remember { mutableStateOf("8080") }
    var tcpClientSendData by remember { mutableStateOf("hello") }
    var tcpStreamHandle by remember { mutableStateOf(0L) }
    var tcpRecvData by remember { mutableStateOf("") }

    // TCP 服务器状态
    var tcpServerPort by remember { mutableStateOf("9090") }
    var tcpListenerHandle by remember { mutableStateOf(0L) }
    var tcpServerAcceptLog by remember { mutableStateOf("") }

    // UDP 状态
    var udpLocalPort by remember { mutableStateOf("0") }
    var udpDstIp by remember { mutableStateOf("10.147.0.2") }
    var udpDstPort by remember { mutableStateOf("8080") }
    var udpSendData by remember { mutableStateOf("hello") }
    var udpSocketHandle by remember { mutableStateOf(0L) }
    var udpRecvData by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === TCP 客户端区 ===
        DataPlaneSectionCard(
            title = "TCP 客户端",
            icon = Icons.Default.CloudUpload,
            handleText = if (tcpStreamHandle != 0L) "流句柄: $tcpStreamHandle" else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tcpClientIp,
                    onValueChange = { tcpClientIp = it },
                    label = { Text("目标 IP") },
                    singleLine = true,
                    enabled = enabled && tcpStreamHandle == 0L,
                    modifier = Modifier.weight(2f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = tcpClientPort,
                    onValueChange = { tcpClientPort = it.filter { c -> c.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    enabled = enabled && tcpStreamHandle == 0L,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val port = tcpClientPort.toIntOrNull() ?: return@launch
                            log("TCP 连接 $tcpClientIp:$port ...")
                            val result = dataPlaneClient?.tcpConnect(tcpClientIp, port)
                            if (result != null) {
                                tcpStreamHandle = result.handle
                                log("TCP 已连接，本地 ${result.addr.ip}:${result.addr.port}")
                            } else {
                                log("TCP 连接失败: ${dataPlaneClient?.lastError ?: "未知错误"}")
                            }
                        }
                    },
                    enabled = enabled && tcpStreamHandle == 0L,
                    modifier = Modifier.weight(1f)
                ) { Text("连接") }

                Button(
                    onClick = {
                        scope.launch {
                            if (tcpStreamHandle == 0L) return@launch
                            val bytes = tcpClientSendData.toByteArray()
                            log("TCP 发送 ${bytes.size} 字节")
                            val written = dataPlaneClient?.tcpWrite(tcpStreamHandle, bytes) ?: -1
                            log("TCP 写入结果: $written")
                        }
                    },
                    enabled = enabled && tcpStreamHandle != 0L,
                    modifier = Modifier.weight(1f)
                ) { Text("发送") }

                Button(
                    onClick = {
                        scope.launch {
                            if (tcpStreamHandle == 0L) return@launch
                            log("TCP 接收...")
                            val result = dataPlaneClient?.tcpRead(tcpStreamHandle, 4096, 5000)
                            if (result != null) {
                                tcpRecvData = String(result.data)
                                log("TCP 接收到 ${result.data.size} 字节: ${String(result.data)}")
                            } else {
                                log("TCP 接收失败/超时: ${dataPlaneClient?.lastError ?: "无"}")
                            }
                        }
                    },
                    enabled = enabled && tcpStreamHandle != 0L,
                    modifier = Modifier.weight(1f)
                ) { Text("接收") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (tcpStreamHandle != 0L) {
                                dataPlaneClient?.tcpClose(tcpStreamHandle)
                                log("TCP 流已关闭")
                                tcpStreamHandle = 0L
                            }
                        }
                    },
                    enabled = enabled && tcpStreamHandle != 0L,
                    modifier = Modifier.weight(1f)
                ) { Text("断开") }
            }
            OutlinedTextField(
                value = tcpClientSendData,
                onValueChange = { tcpClientSendData = it },
                label = { Text("发送数据") },
                singleLine = true,
                enabled = enabled && tcpStreamHandle != 0L,
                modifier = Modifier.fillMaxWidth()
            )
            if (tcpRecvData.isNotEmpty()) {
                Text("接收: $tcpRecvData", style = MaterialTheme.typography.bodySmall)
            }
        }

        // === TCP 服务器区 ===
        DataPlaneSectionCard(
            title = "TCP 服务器",
            icon = Icons.Default.CloudDownload,
            handleText = if (tcpListenerHandle != 0L) "监听器: $tcpListenerHandle" else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tcpServerPort,
                    onValueChange = { tcpServerPort = it.filter { c -> c.isDigit() } },
                    label = { Text("监听端口") },
                    singleLine = true,
                    enabled = enabled && tcpListenerHandle == 0L,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        scope.launch {
                            val port = tcpServerPort.toIntOrNull() ?: return@launch
                            log("TCP 绑定 :$port ...")
                            val result = dataPlaneClient?.tcpBind(port)
                            if (result != null) {
                                tcpListenerHandle = result.handle
                                log("TCP 监听中，本地 ${result.addr.ip}:${result.addr.port}")
                                // 后台循环 accept
                                scope.launch {
                                    while (tcpListenerHandle != 0L) {
                                        val acceptResult = dataPlaneClient?.tcpAccept(tcpListenerHandle, 60000)
                                        if (acceptResult != null) {
                                            tcpServerAcceptLog = "accept: ${acceptResult.peerAddr.ip}:${acceptResult.peerAddr.port}"
                                            log("TCP accept: ${acceptResult.peerAddr.ip}:${acceptResult.peerAddr.port}")
                                        }
                                    }
                                }
                            } else {
                                log("TCP 绑定失败: ${dataPlaneClient?.lastError ?: "未知错误"}")
                            }
                        }
                    },
                    enabled = enabled && tcpListenerHandle == 0L
                ) { Text("监听") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (tcpListenerHandle != 0L) {
                                dataPlaneClient?.tcpListenerClose(tcpListenerHandle)
                                log("TCP 监听器已关闭")
                                tcpListenerHandle = 0L
                            }
                        }
                    },
                    enabled = enabled && tcpListenerHandle != 0L
                ) { Text("停止") }
            }
            if (tcpServerAcceptLog.isNotEmpty()) {
                Text("最近 accept: $tcpServerAcceptLog", style = MaterialTheme.typography.bodySmall)
            }
        }

        // === UDP 调试区 ===
        DataPlaneSectionCard(
            title = "UDP 调试",
            icon = Icons.Default.Shuffle,
            handleText = if (udpSocketHandle != 0L) "套接字: $udpSocketHandle" else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = udpLocalPort,
                    onValueChange = { udpLocalPort = it.filter { c -> c.isDigit() } },
                    label = { Text("本地端口(0=随机)") },
                    singleLine = true,
                    enabled = enabled && udpSocketHandle == 0L,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        scope.launch {
                            val port = udpLocalPort.toIntOrNull() ?: 0
                            log("UDP 绑定 :$port ...")
                            val result = dataPlaneClient?.udpBind(port)
                            if (result != null) {
                                udpSocketHandle = result.handle
                                log("UDP 已绑定 ${result.addr.ip}:${result.addr.port}")
                            } else {
                                log("UDP 绑定失败: ${dataPlaneClient?.lastError ?: "未知错误"}")
                            }
                        }
                    },
                    enabled = enabled && udpSocketHandle == 0L
                ) { Text("绑定") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (udpSocketHandle != 0L) {
                                dataPlaneClient?.udpClose(udpSocketHandle)
                                log("UDP 套接字已关闭")
                                udpSocketHandle = 0L
                            }
                        }
                    },
                    enabled = enabled && udpSocketHandle != 0L
                ) { Text("关闭") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = udpDstIp,
                    onValueChange = { udpDstIp = it },
                    label = { Text("目标 IP") },
                    singleLine = true,
                    enabled = enabled && udpSocketHandle != 0L,
                    modifier = Modifier.weight(2f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = udpDstPort,
                    onValueChange = { udpDstPort = it.filter { c -> c.isDigit() } },
                    label = { Text("目标端口") },
                    singleLine = true,
                    enabled = enabled && udpSocketHandle != 0L,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = udpSendData,
                    onValueChange = { udpSendData = it },
                    label = { Text("发送数据") },
                    singleLine = true,
                    enabled = enabled && udpSocketHandle != 0L,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        scope.launch {
                            if (udpSocketHandle == 0L) return@launch
                            val port = udpDstPort.toIntOrNull() ?: return@launch
                            val bytes = udpSendData.toByteArray()
                            log("UDP 发送 ${bytes.size} 字节到 $udpDstIp:$port")
                            val sent = dataPlaneClient?.udpSendTo(udpSocketHandle, udpDstIp, port, bytes) ?: -1
                            log("UDP 发送结果: $sent")
                        }
                    },
                    enabled = enabled && udpSocketHandle != 0L
                ) { Text("发送") }
                Button(
                    onClick = {
                        scope.launch {
                            if (udpSocketHandle == 0L) return@launch
                            log("UDP 接收...")
                            val result = dataPlaneClient?.udpRecvFrom(udpSocketHandle, 4096, 5000)
                            if (result != null) {
                                udpRecvData = String(result.data)
                                log("UDP 接收 ${result.data.size} 字节来自 ${result.peerAddr.ip}:${result.peerAddr.port}: ${String(result.data)}")
                            } else {
                                log("UDP 接收失败/超时: ${dataPlaneClient?.lastError ?: "无"}")
                            }
                        }
                    },
                    enabled = enabled && udpSocketHandle != 0L
                ) { Text("接收") }
            }
            if (udpRecvData.isNotEmpty()) {
                Text("接收: $udpRecvData", style = MaterialTheme.typography.bodySmall)
            }
        }

        // === 统一日志区 ===
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("操作日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "${logs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { logs.clear() }) { Text("清空") }
                }
                Spacer(Modifier.height(8.dp))
                if (logs.isEmpty()) {
                    Text(
                        "操作后将在此显示日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    logs.take(50).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataPlaneSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    handleText: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (handleText != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(handleText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
