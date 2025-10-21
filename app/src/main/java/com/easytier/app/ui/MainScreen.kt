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
import com.easytier.jni.NetworkInfoParser
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
    rawEventHistory: List<String>,
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
        Column(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
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
                        rawEvents = rawEventHistory,
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
    allConfigs: List<ConfigData>,
    activeConfig: ConfigData,
    onActiveConfigChange: (ConfigData) -> Unit,
    onAddNewConfig: () -> Unit,
    onDeleteConfig: (ConfigData) -> Unit,
    onConfigChange: (ConfigData) -> Unit,
    isRunning: Boolean,
    onControlButtonClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Top Control Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onControlButtonClick,
                modifier = Modifier.weight(1f),
                enabled = activeConfig.instanceName.isNotBlank(),// 确保实例名称不为空
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) { Text(if (isRunning) "停止服务" else "启动服务", fontSize = 18.sp) }

            Box {
                IconButton(onClick = { showMenu = true }, enabled = !isRunning) {
                    Icon(
                        Icons.Default.MoreVert,
                        "配置选项"
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    allConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = { Text(config.instanceName) },
                            onClick = { onActiveConfigChange(config); showMenu = false },
                            leadingIcon = {
                                if (config.id == activeConfig.id) Icon(
                                    Icons.Default.Check,
                                    "当前选中"
                                )
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        { Text("添加新配置") },
                        { onAddNewConfig(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Add, "添加") })
                    DropdownMenuItem(
                        { Text("删除当前配置") },
                        { showDeleteDialog = true; showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, "删除") },
                        enabled = allConfigs.size > 1
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // --- Editable Config Sections ---
        ConfigSection(title = "基本信息", initiallyExpanded = true) {
            ConfigTextField("实例名", activeConfig.instanceName, { onConfigChange(activeConfig.copy(instanceName = it)) }, !isRunning)
            ConfigTextField("主机名", activeConfig.hostname, { onConfigChange(activeConfig.copy(hostname = it)) }, !isRunning)
        }
        ConfigSection(title = "网络身份") {
            ConfigTextField("网络名", activeConfig.networkName, { onConfigChange(activeConfig.copy(networkName = it)) }, !isRunning)
            ConfigTextField("网络密钥", activeConfig.networkSecret, { onConfigChange(activeConfig.copy(networkSecret = it)) }, !isRunning)
        }
        ConfigSection(title = "IP 与连接") {
            ConfigSwitch("DHCP", activeConfig.dhcp, { onConfigChange(activeConfig.copy(dhcp = it, ipv4 = if(it) "" else activeConfig.ipv4)) }, !isRunning)
            ConfigTextField("IPv4", activeConfig.ipv4, { onConfigChange(activeConfig.copy(ipv4 = it)) }, !isRunning && !activeConfig.dhcp, placeholder = "例如: 10.0.0.2/24")
            ConfigTextField("对等节点 (peers)", activeConfig.peers, { onConfigChange(activeConfig.copy(peers = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(100.dp))
            ConfigTextField("监听器 (listeners)", activeConfig.listeners, { onConfigChange(activeConfig.copy(listeners = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(100.dp))
            ConfigTextField("映射监听器", activeConfig.mappedListeners, { onConfigChange(activeConfig.copy(mappedListeners = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(80.dp))
        }
        ConfigSection(title = "高级路由与服务") {
            ConfigTextField("代理网络 (proxy_network)", activeConfig.proxyNetworks, { onConfigChange(activeConfig.copy(proxyNetworks = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(80.dp), placeholder = "每行一个CIDR")
            ConfigTextField("手动路由 (routes)", activeConfig.routes, { onConfigChange(activeConfig.copy(routes = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(80.dp))
            ConfigTextField("出口节点 (exit_nodes)", activeConfig.exitNodes, { onConfigChange(activeConfig.copy(exitNodes = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(80.dp))
            ConfigTextField("SOCKS5 代理", activeConfig.socks5Proxy, { onConfigChange(activeConfig.copy(socks5Proxy = it)) }, !isRunning, placeholder = "例如: socks5://0.0.0.0:1080")
            ConfigTextField("RPC 门户", activeConfig.rpcPortal, { onConfigChange(activeConfig.copy(rpcPortal = it)) }, !isRunning)
            ConfigTextField("RPC 白名单", activeConfig.rpcPortalWhitelist, { onConfigChange(activeConfig.copy(rpcPortalWhitelist = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(80.dp))
        }
        ConfigSection(title = "VPN 门户") {
            ConfigTextField("客户端网段", activeConfig.vpnPortalClientCidr, { onConfigChange(activeConfig.copy(vpnPortalClientCidr = it)) }, !isRunning)
            ConfigTextField("WireGuard 监听", activeConfig.vpnPortalWgListen, { onConfigChange(activeConfig.copy(vpnPortalWgListen = it)) }, !isRunning)
        }
        ConfigSection(title = "端口转发") {
            ConfigTextField("转发规则", activeConfig.portForwards, { onConfigChange(activeConfig.copy(portForwards = it)) }, !isRunning, singleLine = false, modifier = Modifier.height(100.dp), placeholder = "bind,dst,proto (每行一条)")
        }
        ConfigSection(title = "功能标志 (Flags)") {
            ConfigTextField("TUN设备名 (dev_name)", activeConfig.devName, { onConfigChange(activeConfig.copy(devName = it)) }, !isRunning)
            ConfigTextField("转发白名单", activeConfig.relayNetworkWhitelist, { onConfigChange(activeConfig.copy(relayNetworkWhitelist = it)) }, !isRunning)
            ConfigSwitch("魔法DNS", activeConfig.acceptDns, { onConfigChange(activeConfig.copy(acceptDns = it)) }, !isRunning)
            ConfigSwitch("禁用KCP输入", activeConfig.disableKcpInput, { onConfigChange(activeConfig.copy(disableKcpInput = it)) }, !isRunning)
            ConfigSwitch("禁用P2P", activeConfig.disableP2p, { onConfigChange(activeConfig.copy(disableP2p = it)) }, !isRunning)
            ConfigSwitch("禁用QUIC输入", activeConfig.disableQuicInput, { onConfigChange(activeConfig.copy(disableQuicInput = it)) }, !isRunning)
            ConfigSwitch("禁用对称NAT打洞", activeConfig.disableSymHolePunching, { onConfigChange(activeConfig.copy(disableSymHolePunching = it)) }, !isRunning)
            ConfigSwitch("禁用UDP打洞", activeConfig.disableUdpHolePunching, { onConfigChange(activeConfig.copy(disableUdpHolePunching = it)) }, !isRunning)
            ConfigSwitch("启用加密", activeConfig.enableEncryption, { onConfigChange(activeConfig.copy(enableEncryption = it)) }, !isRunning)
            ConfigSwitch("允许作为出口节点", activeConfig.enableExitNode, { onConfigChange(activeConfig.copy(enableExitNode = it)) }, !isRunning)
            ConfigSwitch("启用IPv6", activeConfig.enableIpv6, { onConfigChange(activeConfig.copy(enableIpv6 = it)) }, !isRunning)
            ConfigSwitch("启用KCP代理", activeConfig.enableKcpProxy, { onConfigChange(activeConfig.copy(enableKcpProxy = it)) }, !isRunning)
            ConfigSwitch("启用QUIC代理", activeConfig.enableQuicProxy, { onConfigChange(activeConfig.copy(enableQuicProxy = it)) }, !isRunning)
            ConfigSwitch("延迟优先", activeConfig.latencyFirst, { onConfigChange(activeConfig.copy(latencyFirst = it)) }, !isRunning)
            ConfigSwitch("不创建TUN", activeConfig.noTun, { onConfigChange(activeConfig.copy(noTun = it)) }, !isRunning)
            ConfigSwitch("私有模式", activeConfig.privateMode, { onConfigChange(activeConfig.copy(privateMode = it)) }, !isRunning)
            ConfigSwitch("系统内核转发", activeConfig.proxyForwardBySystem, { onConfigChange(activeConfig.copy(proxyForwardBySystem = it)) }, !isRunning)
            ConfigSwitch("转发所有RPC", activeConfig.relayAllPeerRpc, { onConfigChange(activeConfig.copy(relayAllPeerRpc = it)) }, !isRunning)
            ConfigSwitch("使用SmolTCP", activeConfig.useSmoltcp, { onConfigChange(activeConfig.copy(useSmoltcp = it)) }, !isRunning)
        }
    }
}

// 状态
@Composable
fun StatusTab(
    status: EasyTierManager.EasyTierStatus?, isRunning: Boolean, detailedInfo: DetailedNetworkInfo?,
    onRefreshDetailedInfo: () -> Unit, onPeerClick: (FinalPeerInfo) -> Unit, onCopyJsonClick: () -> Unit
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
        DetailedInfoCard(info = detailedInfo, onRefresh = onRefreshDetailedInfo, onPeerClick = onPeerClick, onCopyJsonClick = onCopyJsonClick, isRunning = isRunning)
    }
}

// 日志
@Composable
fun LogTab(rawEvents: List<String>, onExportClicked: () -> Unit) {

    val parsedEvents by remember(rawEvents) {
        derivedStateOf {
            rawEvents.mapNotNull { NetworkInfoParser.parseSingleRawEvent(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                    // 自动滚动到最新的日志 (视觉上的底部)
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
                        EventInfo.Level.CONFIG -> Color(0xFF80DEEA) // 青色
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
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = fontSize,
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
