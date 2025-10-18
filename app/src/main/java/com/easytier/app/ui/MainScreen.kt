package com.easytier.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.easytier.app.ConfigData
import com.easytier.app.Screen
import com.easytier.app.ui.common.StatusRow
import com.easytier.jni.DetailedNetworkInfo
import com.easytier.jni.EasyTierManager
import com.easytier.jni.FinalPeerInfo

/**
 * - 可折叠配置项、详细信息卡片和导航逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    configData: ConfigData,
    onConfigChange: (ConfigData) -> Unit,
    status: EasyTierManager.EasyTierStatus?,
    isRunning: Boolean,
    onControlButtonClick: () -> Unit,
    detailedInfo: DetailedNetworkInfo?,
    onRefreshDetailedInfo: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EasyTier VPN 控制面板") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // -- 控制按钮 --
            Button(
                onClick = onControlButtonClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "停止服务" else "启动服务", fontSize = 18.sp)
            }
            Spacer(Modifier.height(16.dp))

            // -- 状态卡片 --
            StatusCard(status = status, isRunning = isRunning)
            Spacer(Modifier.height(16.dp))

            // -- 可折叠配置项 --
            ConfigSection(title = "基本设置", initiallyExpanded = true) {
                ConfigTextField("主机名", configData.hostname, { onConfigChange(configData.copy(hostname = it)) }, enabled = !isRunning)
                ConfigTextField("实例名", configData.instanceName, { onConfigChange(configData.copy(instanceName = it)) }, enabled = !isRunning)
            }
            ConfigSection(title = "网络身份") {
                ConfigTextField("网络名", configData.networkName, { onConfigChange(configData.copy(networkName = it)) }, enabled = !isRunning)
                ConfigTextField("网络密钥", configData.networkSecret, { onConfigChange(configData.copy(networkSecret = it)) }, enabled = !isRunning)
            }
            ConfigSection(title = "IP 设置") {
                ConfigTextField(
                    label = "虚拟 IPv4",
                    value = configData.ipv4,
                    onValueChange = { onConfigChange(configData.copy(ipv4 = it)) },
                    enabled = !isRunning && !configData.dhcp,
                    placeholder = if (configData.dhcp) "由DHCP自动分配" else "例如: 10.0.0.1/24"
                )
                ConfigSwitch(
                    label = "自动分配IP (DHCP)",
                    checked = configData.dhcp,
                    onCheckedChange = { dhcpEnabled ->
                        val newConfig = if (dhcpEnabled) configData.copy(dhcp = true, ipv4 = "")
                        else configData.copy(dhcp = false, ipv4 = "10.0.0.1/24")
                        onConfigChange(newConfig)
                    },
                    enabled = !isRunning
                )
            }
            ConfigSection(title = "连接设置") {
                ConfigTextField("对等节点 (每行一个)", configData.peers, { onConfigChange(configData.copy(peers = it)) }, enabled = !isRunning, singleLine = false, modifier = Modifier.height(100.dp))
                ConfigTextField("监听器 (每行一个)", configData.listeners, { onConfigChange(configData.copy(listeners = it)) }, enabled = !isRunning, singleLine = false, modifier = Modifier.height(120.dp))
            }
            ConfigSection(title = "功能标志") {
                ConfigSwitch("延迟优先", configData.latencyFirst, { onConfigChange(configData.copy(latencyFirst = it)) }, enabled = !isRunning)
                ConfigSwitch("私有模式", configData.privateMode, { onConfigChange(configData.copy(privateMode = it)) }, enabled = !isRunning)
                ConfigSwitch("启用 KCP 代理", configData.enableKcpProxy, { onConfigChange(configData.copy(enableKcpProxy = it)) }, enabled = !isRunning)
                ConfigSwitch("启用 QUIC 代理", configData.enableQuicProxy, { onConfigChange(configData.copy(enableQuicProxy = it)) }, enabled = !isRunning)
            }

            // -- 详细信息卡片 --
            DetailedInfoCard(
                info = detailedInfo,
                onRefresh = onRefreshDetailedInfo,
                onPeerClick = { peer ->
                    navController.navigate(Screen.PeerDetail.createRoute(peer.peerId))
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}


// --- UI 组件 ---

@Composable
fun StatusCard(status: EasyTierManager.EasyTierStatus?, isRunning: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("状态信息", style = MaterialTheme.typography.titleMedium)
            Divider(Modifier.padding(vertical = 8.dp))
            StatusRow("服务状态:", if (isRunning) "运行中" else "已停止")
            StatusRow("实例名称:", status?.instanceName ?: "暂无")
            StatusRow("虚拟 IPv4:", status?.currentIpv4 ?: "暂无", isCopyable = true)
        }
    }
}

@Composable
fun ConfigSection(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfigTextField(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean, singleLine: Boolean = true, placeholder: String = "", modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
    )
}

@Composable
fun ConfigSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun DetailedInfoCard(info: DetailedNetworkInfo?, onRefresh: () -> Unit, onPeerClick: (FinalPeerInfo) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
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
                Text("服务运行时将自动显示详细信息。", modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
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
                InfoSection(title = "监听器") { Text(info.myNode.listeners.joinToString("\n"), style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp) }
                InfoSection(title = "接口IP地址") { Text(info.myNode.interfaceIps.joinToString("\n"), style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp) }

                Spacer(Modifier.height(16.dp))
                Text("对等节点 (${info.finalPeerList.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(info.finalPeerList) { peer ->
                            FinalPeerInfoItem(peer, onClick = { onPeerClick(peer) })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("事件日志", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    LazyColumn {
                        items(info.events) { event ->
                            Text(text = "[${event.time}] ${event.message}", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
fun FinalPeerInfoItem(peer: FinalPeerInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(peer.hostname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (!peer.isDirectConnection) {
                    Text(
                        text = "中转",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Divider(Modifier.padding(vertical = 4.dp))
            StatusRow("虚拟 IP:", peer.virtualIp)
            StatusRow(if (peer.isDirectConnection) "物理地址:" else "下一跳:", peer.connectionDetails)
            StatusRow("延迟:", peer.latency)
            StatusRow("流量 (收/发):", peer.traffic)
        }
    }
}