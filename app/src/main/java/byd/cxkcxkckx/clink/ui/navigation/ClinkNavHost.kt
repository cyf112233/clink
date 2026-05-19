package byd.cxkcxkckx.clink.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import byd.cxkcxkckx.clink.core.DeviceItem
import byd.cxkcxkckx.clink.core.PeerItem
import byd.cxkcxkckx.clink.core.TransferItem
import byd.cxkcxkckx.clink.core.TransferStatus
import byd.cxkcxkckx.clink.ui.screens.DevicesScreen
import byd.cxkcxkckx.clink.ui.screens.HomeScreen
import byd.cxkcxkckx.clink.ui.screens.TransfersScreen

private sealed class ClinkDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : ClinkDestination("home", "首页", Icons.Filled.Home)
    object Devices : ClinkDestination("devices", "设备", Icons.Filled.Devices)
    object Transfers : ClinkDestination("transfers", "传输", Icons.Filled.SwapHoriz)
}

private val destinations = listOf(
    ClinkDestination.Home,
    ClinkDestination.Devices,
    ClinkDestination.Transfers
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinkNavHost(
    deviceName: String,
    serviceStatus: String,
    connectedPeers: List<PeerItem>,
    discoveredDevices: List<DeviceItem>,
    logs: List<String>,
    transfers: List<TransferItem>,
    selectedPeerIds: List<String>,
    onConnectDevice: (DeviceItem) -> Unit,
    onToggleTransferPeer: (String) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ClinkDestination.Home.route
    val currentDestination = destinations.firstOrNull { it.route == currentRoute } ?: ClinkDestination.Home

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(currentDestination.title) })
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(ClinkDestination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ClinkDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ClinkDestination.Home.route) {
                HomeScreen(
                    deviceName = deviceName,
                    serviceStatus = serviceStatus,
                    connectedPeers = connectedPeers,
                    discoveredDevices = discoveredDevices,
                    logs = logs,
                    onConnectDevice = onConnectDevice,
                    onOpenPhotos = onOpenPhotos,
                    onOpenVideos = onOpenVideos,
                    onOpenFiles = onOpenFiles
                )
            }
            composable(ClinkDestination.Devices.route) {
                DevicesScreen(
                    connectedPeers = connectedPeers,
                    discoveredDevices = discoveredDevices,
                    onConnectDevice = onConnectDevice
                )
            }
            composable(ClinkDestination.Transfers.route) {
                TransfersScreen(
                    connectedPeers = connectedPeers,
                    selectedPeerIds = selectedPeerIds,
                    transfers = transfers,
                    onTogglePeer = onToggleTransferPeer,
                    onCancelTransfer = onCancelTransfer,
                    onOpenPhotos = onOpenPhotos,
                    onOpenVideos = onOpenVideos,
                    onOpenFiles = onOpenFiles
                )
            }
        }
    }
}
