package com.easytier.app

import android.app.Activity
import android.app.Application
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
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

// 【导航路由常量】
// 定义清晰的路由结构，方便管理和调用
sealed class Screen(val route: String) {
    object Main : Screen("main")
    object PeerDetail : Screen("peer_detail/{peerId}") {
        fun createRoute(peerId: Long) = "peer_detail/$peerId"
    }
    object ConfigManagement : Screen("config_management")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                // 从 ViewModel 获取所有状态
                val allConfigs by viewModel.allConfigs
                val activeConfig by viewModel.activeConfig
                val status by viewModel.status
                val isRunning by viewModel.isRunning
                val detailedInfo by viewModel.detailedInfo

                NavHost(navController = navController, startDestination = Screen.Main.route) {
                    // 主屏幕路由
                    composable(Screen.Main.route) {
                        MainScreen(
                            navController = navController,
                            allConfigs = allConfigs,
                            activeConfig = activeConfig,
                            onActiveConfigChange = viewModel::setActiveConfig,
                            onConfigChange = viewModel::updateConfig,
                            status = status,
                            isRunning = isRunning,
                            onControlButtonClick = {
                                viewModel.handleControlButtonClick(this@MainActivity)
                            },
                            detailedInfo = detailedInfo,
                            onRefreshDetailedInfo = { viewModel.manualRefreshDetailedInfo() }
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