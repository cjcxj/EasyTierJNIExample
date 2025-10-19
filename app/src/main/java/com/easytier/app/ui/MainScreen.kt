package com.easytier.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.easytier.jni.EventInfo
import com.easytier.jni.FinalPeerInfo
import kotlinx.coroutines.launch

data class TabItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    allConfigs: List<ConfigData>,
    activeConfig: ConfigData,
    onActiveConfigChange: (ConfigData) -> Unit,
    onConfigChange: (ConfigData) -> Unit,
    onAddNewConfig: () -> Unit,
    onDeleteConfig: (ConfigData) -> Unit,
    status: EasyTierManager.EasyTierStatus?,
    isRunning: Boolean,
    onControlButtonClick: () -> Unit,
    detailedInfo: DetailedNetworkInfo?,
    fullEventHistory: List<EventInfo>,
    onRefreshDetailedInfo: () -> Unit,
    onCopyJsonClick: () -> Unit,
    onExportLogsClicked: () -> Unit
) {
    val tabs = listOf(
        TabItem("控制", Icons.Default.Settings),
        TabItem("状态", Icons.Default.ShowChart),
        TabItem("日志", Icons.Default.List)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = isRunning) {
        if (isRunning) {
            pagerState.animateScrollToPage(1)
        }
    }

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
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tabItem ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tabItem.title) },
                        icon = { Icon(tabItem.icon, contentDescription = tabItem.title) }
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ControlTab(
                        allConfigs = allConfigs, activeConfig = activeConfig,
                        onActiveConfigChange = onActiveConfigChange, onAddNewConfig = onAddNewConfig,
                        onDeleteConfig = onDeleteConfig, onConfigChange = onConfigChange,
                        isRunning = isRunning, onControlButtonClick = onControlButtonClick
                    )
                    1 -> StatusTab(
                        status = status, isRunning = isRunning, detailedInfo = detailedInfo,
                        onRefreshDetailedInfo = onRefreshDetailedInfo,
                        onPeerClick = { peer -> navController.navigate(Screen.PeerDetail.createRoute(peer.peerId)) },
                        onCopyJsonClick = onCopyJsonClick
                    )
                    2 -> LogTab(
                        events = fullEventHistory,
                        onExportClicked = onExportLogsClicked
                    )
                }
            }
        }
    }
}

// --- 标签页1: 控制 (Control) ---
/**
 * “控制”标签页的UI
 * 集成了多配置管理（切换、添加、删除）和当前配置的完整编辑功能。
 *
 * @param allConfigs 所有已保存的配置列表。
 * @param activeConfig 当前激活的配置。
 * @param onActiveConfigChange 当用户切换配置时调用。
 * @param onAddNewConfig 当用户点击“添加新配置”时调用。
 * @param onDeleteConfig 当用户确认删除当前配置时调用。
 * @param onConfigChange 当用户编辑当前配置的任何字段时调用。
 * @param isRunning 服务当前是否在运行。
 * @param onControlButtonClick 当主启动/停止按钮被点击时调用。
 */
@Composable
fun ControlTab(
    allConfigs: List<ConfigData>, activeConfig: ConfigData,
    onActiveConfigChange: (ConfigData) -> Unit, onAddNewConfig: () -> Unit,
    onDeleteConfig: (ConfigData) -> Unit, onConfigChange: (ConfigData) -> Unit,
    isRunning: Boolean, onControlButtonClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onControlButtonClick,
                modifier = Modifier.weight(1f),
                enabled = activeConfig.instanceName.isNotBlank(),// 确保实例名称不为空
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) { Text(if (isRunning) "停止服务" else "启动服务", fontSize = 18.sp) }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    enabled = !isRunning,
                ) {
                    Icon(Icons.Default.MoreVert, "配置选项")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    allConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = { Text(config.instanceName) },
                            onClick = { onActiveConfigChange(config); showMenu = false },
                            leadingIcon = { if (config.id == activeConfig.id) Icon(Icons.Default.Check, "当前选中") }
                        )
                    }
                    Divider()
                    DropdownMenuItem({ Text("添加新配置") }, { onAddNewConfig(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Add, "添加") })
                    DropdownMenuItem({ Text("删除当前配置") }, { showDeleteDialog = true; showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, "删除") }, enabled = allConfigs.size > 1)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ConfigSection(title = "基本设置", initiallyExpanded = true) {
            ConfigTextField("主机名", activeConfig.hostname, { onConfigChange(activeConfig.copy(hostname = it)) }, enabled = !isRunning)
            ConfigTextField("实例名", activeConfig.instanceName, { onConfigChange(activeConfig.copy(instanceName = it)) }, enabled = !isRunning)
        }
        ConfigSection("网络身份") {
            ConfigTextField("网络名", activeConfig.networkName, { onConfigChange(activeConfig.copy(networkName = it)) }, enabled = !isRunning)
            ConfigTextField("网络密钥", activeConfig.networkSecret, { onConfigChange(activeConfig.copy(networkSecret = it)) }, enabled = !isRunning)
        }
        ConfigSection("IP 设置") {
            ConfigTextField("虚拟 IPv4", activeConfig.ipv4, { onConfigChange(activeConfig.copy(ipv4 = it)) }, !isRunning && !activeConfig.dhcp, placeholder = if (activeConfig.dhcp) "由DHCP自动分配" else "例如: 10.0.0.1/24")
            ConfigSwitch("自动分配IP (DHCP)", activeConfig.dhcp, { dhcpEnabled ->
                onConfigChange(if (dhcpEnabled) activeConfig.copy(dhcp = true, ipv4 = "") else activeConfig.copy(dhcp = false, ipv4 = "10.0.0.1/24"))
            }, !isRunning)
        }
        ConfigSection("连接设置") {
            ConfigTextField("对等节点 (每行一个)", activeConfig.peers, { onConfigChange(activeConfig.copy(peers = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(100.dp))
            ConfigTextField("监听器 (每行一个)", activeConfig.listeners, { onConfigChange(activeConfig.copy(listeners = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(120.dp))
        }
        ConfigSection("功能标志") {
            ConfigSwitch("延迟优先", activeConfig.latencyFirst, { onConfigChange(activeConfig.copy(latencyFirst = it)) }, !isRunning)
            ConfigSwitch("私有模式", activeConfig.privateMode, { onConfigChange(activeConfig.copy(privateMode = it)) }, !isRunning)
            ConfigSwitch("启用 KCP 代理", activeConfig.enableKcpProxy, { onConfigChange(activeConfig.copy(enableKcpProxy = it)) }, !isRunning)
            ConfigSwitch("启用 QUIC 代理", activeConfig.enableQuicProxy, { onConfigChange(activeConfig.copy(enableQuicProxy = it)) }, !isRunning)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("您确定要删除配置 '${activeConfig.instanceName}' 吗？此操作无法撤销。") },
            confirmButton = { Button({ onDeleteConfig(activeConfig); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") } },
            dismissButton = { OutlinedButton({ showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun StatusTab(
    status: EasyTierManager.EasyTierStatus?, isRunning: Boolean, detailedInfo: DetailedNetworkInfo?,
    onRefreshDetailedInfo: () -> Unit, onPeerClick: (FinalPeerInfo) -> Unit, onCopyJsonClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(status = status, isRunning = isRunning)
        DetailedInfoCard(info = detailedInfo, onRefresh = onRefreshDetailedInfo, onPeerClick = onPeerClick, onCopyJsonClick = onCopyJsonClick, isRunning = isRunning)
    }
}

@Composable
fun LogTab(events: List<EventInfo>, onExportClicked: () -> Unit) {
    // 创建并记住 LazyColumn 的滚动状态
    val lazyListState = rememberLazyListState()

    // 使用 LaunchedEffect 来观察日志列表的大小。
    // `key1 = events.size` 意味着只有当日志数量变化时，这个协程才会重新启动。
    LaunchedEffect(key1 = events.size) {
        // 确保列表不为空
        if (events.isNotEmpty()) {
            // 动画地滚动到列表的第一项。
            // 因为使用了 `reverseLayout = true`，所以列表的第一项 (index 0)
            // 在视觉上会显示在最底部。
            lazyListState.animateScrollToItem(index = 0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("事件日志", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onExportClicked, enabled = events.isNotEmpty()) {
                Icon(Icons.Default.Save, "导出", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("导出原始日志")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("服务运行时将显示事件日志。")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                // 将创建的状态与 LazyColumn 关联起来
                state = lazyListState,
                // 保持 reverseLayout = true，这对于日志显示非常关键
                reverseLayout = true
            ) {
                items(
                    items = events.asReversed(), //反转列表，使最新的日志在数据上也是第一个
                    key = { it.rawTime } // 使用唯一的时间戳作为 key，提高性能
                ) { event ->
                    val logColor = when (event.level) {
                        EventInfo.Level.SUCCESS -> Color(0xFF81C784)
                        EventInfo.Level.ERROR -> Color(0xFFE57373)
                        EventInfo.Level.WARNING -> Color(0xFFFFD54F)
                        EventInfo.Level.INFO -> Color.White
                    }
                    Text(
                        "[${event.time}] ${event.message}",
                        color = logColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}


// --- UI 组件 ---

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

@Composable
fun ConfigSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = true,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
    )
}

@Composable
fun ConfigSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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

            // 【复制JSON按钮】
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCopyJsonClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = isRunning // 使用传入的 isRunning 状态
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
