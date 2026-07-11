package com.easytier.app.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.easytier.app.ConfigData
import com.easytier.app.Screen
import com.easytier.jni.ConfigServerClientManager
import com.easytier.jni.DataPlaneClient
import com.easytier.jni.DetailedNetworkInfo
import com.easytier.jni.EasyTierManager
import com.easytier.jni.RpcClient
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
    isConfigServerControlled: Boolean,
    onControlButtonClick: () -> Unit,
    onStopConfigServerInstance: () -> Unit,
    detailedInfo: DetailedNetworkInfo?,
    rawEventHistory: List<String>,
    onRefreshDetailedInfo: () -> Unit,
    onCopyJsonClick: () -> Unit,
    onExportLogsClicked: () -> Unit,
    onExportConfig: (Uri) -> Unit,
    onImportConfig: (Uri) -> Unit,
    dataPlaneClient: DataPlaneClient?,
    configServerManager: ConfigServerClientManager?,
    machineId: String,
    rpcClient: RpcClient?,
    runningInstanceName: String,
    configServerSettings: MainViewModel.ConfigServerSettings,
    onSaveConfigServerSettings: (String, String, Boolean, Boolean) -> Unit,
    onDisconnectConfigServer: () -> Unit
) {
    val tabs = listOf(
        TabItem("控制", Icons.Default.Settings),
        TabItem("状态", Icons.Default.ShowChart),
        TabItem("日志", Icons.Default.List),
        TabItem("调试", Icons.Default.NetworkCheck),
        TabItem("配置中心", Icons.Default.CloudSync),
        TabItem("RPC", Icons.Default.Terminal)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = isRunning) {
        if (isRunning) {
            pagerState.animateScrollToPage(1)
        }
    }

    val statusColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(600),
        label = "statusColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("EasyTier VPN", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                tabs.forEachIndexed { index, tabItem ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            if (index == 1) {
                                val peerCount = detailedInfo?.finalPeerList?.size ?: 0
                                if (peerCount > 0) {
                                    BadgedBox(badge = { Badge { Text(peerCount.toString()) } }) {
                                        Icon(
                                            tabItem.icon,
                                            contentDescription = tabItem.title,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        tabItem.icon,
                                        contentDescription = tabItem.title,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    tabItem.icon,
                                    contentDescription = tabItem.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                tabItem.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> ControlTab(
                    allConfigs = allConfigs,
                    activeConfig = activeConfig,
                    onActiveConfigChange = onActiveConfigChange,
                    onAddNewConfig = onAddNewConfig,
                    onDeleteConfig = onDeleteConfig,
                    onConfigChange = onConfigChange,
                    isRunning = isRunning,
                    status = status,
                    isConfigServerControlled = isConfigServerControlled,
                    onControlButtonClick = onControlButtonClick,
                    onStopConfigServerInstance = onStopConfigServerInstance,
                    onExportConfig = onExportConfig,
                    onImportConfig = onImportConfig
                )

                1 -> StatusTab(
                    status = status, isRunning = isRunning, detailedInfo = detailedInfo,
                    onRefreshDetailedInfo = onRefreshDetailedInfo,
                    onPeerClick = { peer ->
                        navController.navigate(
                            Screen.PeerDetail.createRoute(
                                peer.peerId
                            )
                        )
                    },
                    onCopyJsonClick = onCopyJsonClick
                )

                2 -> LogTab(
                    rawEvents = rawEventHistory,
                    onExportClicked = onExportLogsClicked
                )

                3 -> DataPlaneDebugTab(
                    dataPlaneClient = dataPlaneClient,
                    isRunning = isRunning
                )

                4 -> ConfigServerTab(
                    manager = configServerManager,
                    machineId = machineId,
                    initialUrl = configServerSettings.url,
                    initialHostname = configServerSettings.hostname,
                    initialSecureMode = configServerSettings.secureMode,
                    initialAutoConnect = configServerSettings.autoConnect,
                    onSettingsSaved = onSaveConfigServerSettings,
                    onDisconnect = onDisconnectConfigServer
                )

                5 -> RpcDebugTab(
                    rpcClient = rpcClient,
                    instanceName = runningInstanceName
                )
            }
        }
    }
}
