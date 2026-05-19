package byd.cxkcxkckx.clink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import byd.cxkcxkckx.clink.core.PeerItem
import byd.cxkcxkckx.clink.core.TransferItem
import byd.cxkcxkckx.clink.ui.components.EmptyStateCard
import byd.cxkcxkckx.clink.ui.components.PeerSelectionCard
import byd.cxkcxkckx.clink.ui.components.QuickActionCard
import byd.cxkcxkckx.clink.ui.components.SectionHeader
import byd.cxkcxkckx.clink.ui.components.TransferCard

@Composable
fun TransfersScreen(
    connectedPeers: List<PeerItem>,
    selectedPeerIds: List<String>,
    transfers: List<TransferItem>,
    onTogglePeer: (String) -> Unit,
    onCancelTransfer: (String) -> Unit,
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
        item { SectionHeader("发送中心", "先勾选接收设备，再选择照片、视频或文件") }
        item {
            if (connectedPeers.isEmpty()) {
                EmptyStateCard("还没有可发送的已连接设备，请先到设备页建立连接。")
            } else {
                PeerSelectionCard(
                    peers = connectedPeers,
                    selectedPeerIds = selectedPeerIds,
                    onTogglePeer = onTogglePeer
                )
            }
        }
        item { QuickActionCard(onOpenPhotos, onOpenVideos, onOpenFiles) }
        item { SectionHeader("传输列表", "可对排队中或进行中的发送任务执行取消") }
        if (transfers.isEmpty()) {
            item { EmptyStateCard("暂时没有传输任务，选择文件后会在这里显示队列和进度。") }
        } else {
            items(transfers.sortedByDescending { it.createdAt }, key = { it.id }) { transfer ->
                TransferCard(transfer = transfer, onCancel = onCancelTransfer)
            }
        }
    }
}
