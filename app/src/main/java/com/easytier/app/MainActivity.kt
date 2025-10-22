package com.easytier.app

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.easytier.app.ui.MainScreen
import com.easytier.app.ui.MainViewModel
import com.easytier.app.ui.PeerDetailScreen
import kotlinx.coroutines.flow.collectLatest
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 【导航路由常量】
sealed class Screen(val route: String) {
    object Main : Screen("main")
    object PeerDetail : Screen("peer_detail/{peerId}") {
        fun createRoute(peerId: Long) = "peer_detail/$peerId"
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // 使用自定义 Factory 来创建 ViewModel 实例
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    // VPN 权限请求回调
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "VPN permission granted.")
                // 权限成功后，通知 ViewModel 启动服务
                viewModel.startEasyTier(this)
            } else {
                Log.w(TAG, "VPN permission denied.")
                Toast.makeText(this, "需要VPN权限才能启动服务。", Toast.LENGTH_SHORT).show()
            }
        }

    // 用于“创建文档”的 ActivityResultLauncher
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
                            "没有可导出的原始事件",
                            Toast.LENGTH_SHORT
                        ).show()
                        Toast.makeText(
                            this@MainActivity,
                            "没有可导出的事件日志",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    // 用于从UI层触发文件创建流程
    fun launchCreateLogFile() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "easytier_events_$timeStamp.json"
        createFileLauncher.launch(fileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {

            val context = LocalContext.current // 获取当前 Composable 的 Context

            // 动态设置状态栏颜色和图标样式
            val darkTheme = isSystemInDarkTheme()
            val view = LocalView.current

            // 使用 DisposableEffect，当 darkTheme 状态改变时，代码会重新执行
            DisposableEffect(darkTheme) {
                // 将状态栏背景设置为完全透明
                window.statusBarColor = Color.TRANSPARENT

                // 获取窗口的 InsetsController，用于控制系统栏的外观
                val insetsController = WindowCompat.getInsetsController(window, view)

                // 根据是否为深色主题，设置状态栏图标的颜色
                // isAppearanceLightStatusBars = true -> 图标为深色（用于浅色背景）
                // isAppearanceLightStatusBars = false -> 图标为浅色（用于深色背景）
                insetsController.isAppearanceLightStatusBars = !darkTheme

                // onDispose 用于清理副作用，这里我们不需要做什么
                onDispose { }
            }

            // 使用 LaunchedEffect 来监听 ViewModel 的事件 Flow
            LaunchedEffect(key1 = true) {
                viewModel.toastEvents.collectLatest { message ->
                    // 当收到新消息时，显示 Toast
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            MaterialTheme {
                val navController = rememberNavController()

                // 从 ViewModel 获取所有状态
                val allConfigs by viewModel.allConfigs
                val activeConfig by viewModel.activeConfig
                val status by viewModel.statusState // `status` -> `statusState`
                val detailedInfo by viewModel.detailedInfoState // `detailedInfo` -> `detailedInfoState`
                val fullRawEventHistory by viewModel.fullRawEventHistory
                val isRunning = viewModel.isRunning // 直接从 ViewModel 的 getter 属性获取

                NavHost(navController = navController, startDestination = Screen.Main.route) {
                    // 主屏幕路由
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
                            onControlButtonClick = {
                                viewModel.handleControlButtonClick(this@MainActivity)
                            },
                            detailedInfo = detailedInfo,
                            rawEventHistory = fullRawEventHistory,
                            onRefreshDetailedInfo = { viewModel.manualRefreshDetailedInfo() },
                            onCopyJsonClick = viewModel::copyJsonToClipboard,
                            onExportLogsClicked = ::launchCreateLogFile,
                        )
                    }

                    // 详情页路由
                    composable(
                        route = Screen.PeerDetail.route,
                        arguments = listOf(navArgument("peerId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val peerId = backStackEntry.arguments?.getLong("peerId")
                        // 通过 peerId 从 ViewModel 的状态中查找对应的 peer 对象
                        val peer = detailedInfo?.finalPeerList?.find { it.peerId == peerId }

                        if (peer != null) {
                            PeerDetailScreen(
                                peer = peer,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            //可以在此处显示加载或错误UI
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopEasyTier()
    }

    /**
     * 在 Activity 中处理 VPN 权限请求的逻辑
     */
    fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.i(TAG, "Requesting VPN permission...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.i(TAG, "VPN permission already granted, starting service.")
            // 权限已有时，直接通知 ViewModel 启动
            viewModel.startEasyTier(this)
        }
    }
}

/**
 * 自定义的 ViewModelProvider.Factory，用于创建 MainViewModel 实例。
 */
class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}