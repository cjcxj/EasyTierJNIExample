package com.easytier.app

import android.app.Activity
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.easytier.app.ui.MainScreen
import com.easytier.app.ui.MainViewModel
import com.easytier.app.ui.PeerDetailScreen
import kotlin.getValue

// --- Data class for UI state ---
data class ConfigData(
    val hostname: String = "Android-Device",
    val instanceName: String = "easytier",
    val ipv4: String = "",
    val dhcp: Boolean = true,
    val listeners: String = "tcp://0.0.0.0:11010\nudp://0.0.0.0:11010\nwg://0.0.0.0:11011",
    val rpcPortal: String = "0.0.0.0:0",
    val networkName: String = "easytier",
    val networkSecret: String = "",
    val peers: String = "tcp://public.easytier.top:11010",
    val enableKcpProxy: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val latencyFirst: Boolean = false,
    val privateMode: Boolean = false
)

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "VPN permission granted.")
                viewModel.startEasyTier(this)
            } else {
                Log.w(TAG, "VPN permission denied.")
                Toast.makeText(this, "VPN permission is required to start the service.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val configData by viewModel.configData
                val status by viewModel.status
                val isRunning by viewModel.isRunning
                val detailedInfo by viewModel.detailedInfo

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            navController = navController,
                            configData = configData,
                            onConfigChange = viewModel::onConfigChange,
                            status = status,
                            isRunning = isRunning,
                            onControlButtonClick = {
                                if (isRunning) {
                                    viewModel.stopEasyTier()
                                } else {
                                    requestVpnPermission()
                                }
                            },
                            detailedInfo = detailedInfo,
                            onRefreshDetailedInfo = { }, // Will be handled by ViewModel
                            getLatestStatus = { status }
                        )
                    }
                    composable("peerDetail/{peerId}") { backStackEntry ->
                        val peerId = backStackEntry.arguments?.getString("peerId")
                        val peer = viewModel.detailedInfo.value?.finalPeerList?.find { it.hostname == peerId }
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

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopEasyTier()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.i(TAG, "Requesting VPN permission...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.i(TAG, "VPN permission already granted, starting service.")
            viewModel.startEasyTier(this)
        }
    }
}