package byd.cxkcxkckx.clink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import byd.cxkcxkckx.clink.core.DeviceItem
import byd.cxkcxkckx.clink.core.PeerItem
import byd.cxkcxkckx.clink.core.TransferDirection
import byd.cxkcxkckx.clink.core.TransferItem
import byd.cxkcxkckx.clink.core.TransferStatus
import java.util.Locale

@Composable
fun HeroStatusCard(deviceName: String, serviceStatus: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("clink", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text("发现附近设备，快速传输照片、视频和文件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
                Text("本机：$deviceName", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                Text(serviceStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun QuickActionCard(onPhotos: () -> Unit, onVideos: () -> Unit, onFiles: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("快速发送", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("先连接设备，再选择要发送的内容。", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionModuleButton("照片", Icons.Filled.Image, Modifier.weight(1f), onPhotos)
                ActionModuleButton("视频", Icons.Filled.Movie, Modifier.weight(1f), onVideos)
            }
            ActionModuleButton("文件", Icons.Filled.FolderZip, Modifier.fillMaxWidth(), onFiles)
        }
    }
}

@Composable
private fun ActionModuleButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = label)
        Text(" $label")
    }
}

@Composable
fun OverviewModules(connectedCount: Int, discoveredCount: Int, logCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewCard("已连接", connectedCount.toString(), Icons.Filled.CheckCircle, Modifier.weight(1f))
        OverviewCard("在线设备", discoveredCount.toString(), Icons.Filled.WifiTethering, Modifier.weight(1f))
        OverviewCard("日志", logCount.toString(), Icons.Filled.Devices, Modifier.weight(1f))
    }
}

@Composable
private fun OverviewCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyStateCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PeerCard(peer: PeerItem) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(peer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text("地址：${peer.address}")
            Text("状态：${peer.status}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DeviceCard(device: DeviceItem, onConnect: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text("${device.host}:${device.port}")
            Button(onClick = onConnect) {
                Icon(Icons.Filled.Link, contentDescription = "连接")
                Text(" 发起连接")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerSelectionCard(
    peers: List<PeerItem>,
    selectedPeerIds: List<String>,
    onTogglePeer: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("发送到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("可多选接收设备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                peers.forEach { peer ->
                    FilterChip(
                        selected = selectedPeerIds.contains(peer.id),
                        onClick = { onTogglePeer(peer.id) },
                        label = { Text(peer.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun LogTerminalCard(logs: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "运行日志",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFBFDBFE),
                fontWeight = FontWeight.SemiBold
            )
            if (logs.isEmpty()) {
                Text(
                    text = "> 暂无日志输出",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF93C5FD),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                logs.takeLast(12).forEach { line ->
                    Text(
                        text = "> $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun TransferCard(transfer: TransferItem, onCancel: (String) -> Unit) {
    val statusText = when (transfer.status) {
        TransferStatus.QUEUED -> "排队中"
        TransferStatus.RUNNING -> "传输中"
        TransferStatus.COMPLETED -> "已完成"
        TransferStatus.FAILED -> "失败"
        TransferStatus.CANCELED -> "已取消"
    }
    val directionText = if (transfer.direction == TransferDirection.SEND) "发送" else "接收"
    val progressText = if (transfer.totalBytes > 0L) {
        String.format(Locale.getDefault(), "%.0f%%", transfer.progress * 100f)
    } else {
        "--"
    }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(transfer.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(statusText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Text("$directionText · ${transfer.peerName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = transfer.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.tertiary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(transfer.message.ifBlank { statusText }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(progressText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            if (transfer.direction == TransferDirection.SEND &&
                (transfer.status == TransferStatus.QUEUED || transfer.status == TransferStatus.RUNNING)
            ) {
                OutlinedButton(onClick = { onCancel(transfer.id) }) {
                    Text("取消任务")
                }
            }
        }
    }
}
