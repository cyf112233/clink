package byd.cxkcxkckx.clink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import byd.cxkcxkckx.clink.core.DeviceItem
import byd.cxkcxkckx.clink.core.PeerItem
import byd.cxkcxkckx.clink.ui.components.EmptyStateCard
import byd.cxkcxkckx.clink.ui.components.HeroStatusCard
import byd.cxkcxkckx.clink.ui.components.LogTerminalCard
import byd.cxkcxkckx.clink.ui.components.OverviewModules
import byd.cxkcxkckx.clink.ui.components.SectionHeader

@Composable
fun HomeScreen(
    deviceName: String,
    serviceStatus: String,
    connectedPeers: List<PeerItem>,
    discoveredDevices: List<DeviceItem>,
    logs: List<String>,
    onConnectDevice: (DeviceItem) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenFiles: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroStatusCard(deviceName, serviceStatus) }
        item { OverviewModules(connectedPeers.size, discoveredDevices.size, logs.size) }

        item { SectionHeader("运行日志", "实时显示扫描、连接与传输记录") }
        item { LogTerminalCard(logs) }
    }
}
