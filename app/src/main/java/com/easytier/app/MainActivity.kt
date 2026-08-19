package com.easytier.app

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.easytier.app.ui.MainScreen
import com.easytier.app.ui.MainViewModel
import com.easytier.app.ui.PeerDetailScreen
import com.easytier.app.ui.theme.EasyTierTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object PeerDetail : Screen("peer_detail/{peerId}") {
        fun createRoute(peerId: Long) = "peer_detail/$peerId"
    }
}

class MainActivity : ComponentActivity() {

    private enum class VpnAction { NONE, LOCAL_START, CONFIG_SERVER_VPN }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_STOP_VPN = "com.easytier.app.action.STOP_VPN"
        private const val KEY_PENDING_VPN_ACTION = "pending_vpn_action"
    }

    private var pendingVpnAction = VpnAction.NONE

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    // Android 13+ 需运行时请求通知权限，否则前台服务通知不可见
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "VPN permission granted. action=$pendingVpnAction")
                when (pendingVpnAction) {
                    VpnAction.CONFIG_SERVER_VPN -> viewModel.startVpnForConfigServerInstance()
                    else -> viewModel.startEasyTier(this)
                }
            } else {
                Log.w(TAG, "VPN permission denied. action=$pendingVpnAction")
                Toast.makeText(this, "需要VPN权限才能启动服务。", Toast.LENGTH_SHORT).show()
            }
            pendingVpnAction = VpnAction.NONE
        }

    private val createFileLauncher =
        registerForActivityResult(CreateDocument("application/json")) { uri: Uri? ->
            uri?.let { fileUri ->
                lifecycleScope.launch {
                    val rawEventsJson = viewModel.getRawEventsJsonForExport()
                    if (rawEventsJson != null) {
                        viewModel.writeContentToUri(fileUri, rawEventsJson)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "没有可导出的事件日志",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    fun launchCreateLogFile() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "easytier_events_$timeStamp.json"
        createFileLauncher.launch(fileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 恢复旋转/重建前未完成的 VPN 授权动作，避免授权回调走错分支
        pendingVpnAction = savedInstanceState?.getString(KEY_PENDING_VPN_ACTION)
            ?.let { name -> runCatching { VpnAction.valueOf(name) }.getOrNull() }
            ?: VpnAction.NONE
        // Android 13+ 请求通知权限，确保前台服务"运行中"通知可见
        requestNotificationPermissionIfNeeded()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val view = LocalView.current

            DisposableEffect(darkTheme) {
                window.statusBarColor = Color.TRANSPARENT
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                onDispose { }
            }

            LaunchedEffect(key1 = true) {
                viewModel.toastEvents.collectLatest { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            EasyTierTheme {
                val navController = rememberNavController()

                val allConfigs by viewModel.allConfigs
                val activeConfig by viewModel.activeConfig
                val status by viewModel.statusState
                val detailedInfo by viewModel.detailedInfoState
                val fullRawEventHistory by viewModel.fullRawEventHistory
                val dataPlaneClient by viewModel.dataPlaneClient
                val configServerManager by viewModel.configServerManager
                val machineId by viewModel.machineId
                val rpcClient by viewModel.rpcClient
                val isRunning = viewModel.isRunning
                val configServerSettings by viewModel.configServerSettings

                NavHost(navController = navController, startDestination = Screen.Main.route) {
                    composable(Screen.Main.route) {
                        MainScreen(
                            navController = navController,
                            allConfigs = allConfigs,
                            activeConfig = activeConfig,
                            onActiveConfigChange = viewModel::setActiveConfig,
                            onConfigChange = viewModel::updateConfig,
                            onAddNewConfig = viewModel::addNewConfig,
                            onDeleteConfig = viewModel::deleteConfig,
                            status = status,
                            isRunning = isRunning,
                            isConfigServerControlled = viewModel.isConfigServerControlled,
                            onControlButtonClick = {
                                viewModel.handleControlButtonClick(this@MainActivity)
                            },
                            onStopConfigServerInstance = { viewModel.stopConfigServerInstance() },
                            detailedInfo = detailedInfo,
                            rawEventHistory = fullRawEventHistory,
                            onRefreshDetailedInfo = { viewModel.manualRefreshDetailedInfo() },
                            onCopyJsonClick = viewModel::copyJsonToClipboard,
                            onExportLogsClicked = ::launchCreateLogFile,
                            onExportConfig = { uri -> viewModel.exportConfig(uri) },
                            onImportConfig = { uri -> viewModel.importConfig(uri) },
                            dataPlaneClient = dataPlaneClient,
                            configServerManager = configServerManager,
                            machineId = machineId,
                            rpcClient = rpcClient,
                            runningInstanceName = viewModel.runningInstanceName,
                            configServerSettings = configServerSettings,
                            onSaveConfigServerSettings = { url, hostname, secureMode, autoConnect ->
                                viewModel.saveConfigServerSettings(url, hostname, secureMode, autoConnect)
                            },
                            onDisconnectConfigServer = { viewModel.stopConfigServerInstance() }
                        )
                    }

                    composable(
                        route = Screen.PeerDetail.route,
                        arguments = listOf(navArgument("peerId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val peerId = backStackEntry.arguments?.getLong("peerId")
                        val peer = detailedInfo?.finalPeerList?.find { it.peerId == peerId }

                        if (peer != null) {
                            PeerDetailScreen(
                                peer = peer,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_STOP_VPN) {
            Log.i(TAG, "收到通知停止请求，停止服务")
            viewModel.stopEasyTier()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_VPN_ACTION, pendingVpnAction.name)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 仅在 Activity 真正退出（而非旋转屏幕等配置变更导致的重建）时才停止服务。
        // 部分 ROM 在配置变更重建时会误报 isFinishing=true，导致旋转屏幕后 VPN 被停掉。
        if (isFinishing && !isChangingConfigurations) {
            Log.i(TAG, "Activity 正在退出，停止 EasyTier 服务")
            viewModel.stopEasyTier()
        } else if (isFinishing) {
            Log.i(TAG, "配置变更导致的重建（isFinishing=true 但 isChangingConfigurations=true），不停止服务")
        }
    }

    override fun onResume() {
        super.onResume()
        // 远程配置 VPN 待授权时，回到前台自动触发权限请求
        if (viewModel.consumePendingVpnForConfigServer()) {
            requestVpnPermissionForConfigServer()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestVpnPermission() {
        pendingVpnAction = VpnAction.LOCAL_START
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.i(TAG, "Requesting VPN permission (local start)...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.i(TAG, "VPN permission already granted, starting local service.")
            pendingVpnAction = VpnAction.NONE
            viewModel.startEasyTier(this)
        }
    }

    /** 远程配置场景请求 VPN 权限（授权后只启动 VPN，不启动本地 EasyTier 实例） */
    fun requestVpnPermissionForConfigServer() {
        pendingVpnAction = VpnAction.CONFIG_SERVER_VPN
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.i(TAG, "Requesting VPN permission (config server)...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.i(TAG, "VPN permission already granted, starting config server VPN.")
            pendingVpnAction = VpnAction.NONE
            viewModel.startVpnForConfigServerInstance()
        }
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
