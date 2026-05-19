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
import byd.cxkcxkckx.clink.ui.components.DeviceCard
import byd.cxkcxkckx.clink.ui.components.EmptyStateCard
import byd.cxkcxkckx.clink.ui.components.PeerCard
import byd.cxkcxkckx.clink.ui.components.SectionHeader

@Composable
fun DevicesScreen(
    connectedPeers: List<PeerItem>,
    discoveredDevices: List<DeviceItem>,
    onConnectDevice: (DeviceItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionHeader("已连接设备", "稳定连接列表") }
        if (connectedPeers.isEmpty()) {
            item { EmptyStateCard("当前还没有已连接设备") }
        } else {
            items(connectedPeers, key = { it.id }) { peer ->
                PeerCard(peer)
            }
        }

        item { SectionHeader("局域网在线设备", "点击即可发起连接") }
        if (discoveredDevices.isEmpty()) {
            item { EmptyStateCard("正在扫描同局域网设备…") }
        } else {
            items(discoveredDevices, key = { it.id }) { device ->
                DeviceCard(device, onConnect = { onConnectDevice(device) })
            }
        }
    }
}
