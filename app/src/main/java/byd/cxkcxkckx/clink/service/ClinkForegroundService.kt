package byd.cxkcxkckx.clink.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import byd.cxkcxkckx.clink.core.ClinkEngine
import byd.cxkcxkckx.clink.core.ClinkSession
import byd.cxkcxkckx.clink.core.DeviceItem
import byd.cxkcxkckx.clink.core.FileCategory
import byd.cxkcxkckx.clink.core.IncomingRequest
import byd.cxkcxkckx.clink.core.PeerItem
import byd.cxkcxkckx.clink.core.TransferDirection
import byd.cxkcxkckx.clink.core.TransferItem
import byd.cxkcxkckx.clink.core.TransferStatus
import java.util.concurrent.ConcurrentHashMap

class ClinkForegroundService : Service(), ClinkEngine.Callback, ClinkSession.Controller {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var engine: ClinkEngine
    private val transferStatusCache = ConcurrentHashMap<String, TransferStatus>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("clink 正在后台运行"))
        engine = ClinkEngine(applicationContext, this)
        ClinkSession.attachController(this, engine.localDeviceName)
        engine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        ClinkSession.detachController(this)
        engine.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun connectTo(device: DeviceItem) {
        engine.connectTo(device)
    }

    override fun respondToIncomingRequest(accepted: Boolean) {
        engine.respondToIncomingRequest(accepted)
    }

    override fun sendFilesToPeers(uris: List<android.net.Uri>, category: FileCategory, peerIds: List<String>) {
        engine.sendFiles(uris, category, peerIds)
    }

    override fun cancelTransfer(transferId: String) {
        engine.cancelTransfer(transferId)
    }

    override fun onDeviceDiscovered(device: DeviceItem) {
        mainHandler.post {
            val index = ClinkSession.discoveredDevices.indexOfFirst { it.id == device.id }
            if (index >= 0) {
                ClinkSession.discoveredDevices[index] = device
            } else {
                ClinkSession.discoveredDevices.add(device)
            }
            updateNotification()
        }
    }

    override fun onDeviceLost(id: String) {
        mainHandler.post {
            ClinkSession.discoveredDevices.removeAll { it.id == id }
            updateNotification()
        }
    }

    override fun onPeerConnected(peer: PeerItem) {
        mainHandler.post {
            val index = ClinkSession.connectedPeers.indexOfFirst { it.id == peer.id }
            if (index >= 0) {
                ClinkSession.connectedPeers[index] = peer
            } else {
                ClinkSession.connectedPeers.add(peer)
            }
            updateNotification()
        }
    }

    override fun onPeerDisconnected(id: String, reason: String) {
        mainHandler.post {
            ClinkSession.connectedPeers.removeAll { it.id == id }
            ClinkSession.logs.add("连接断开：$id - $reason")
            updateNotification()
        }
    }

    override fun onIncomingConnectRequest(request: IncomingRequest) {
        mainHandler.post {
            ClinkSession.incomingRequest = request
            updateNotification()
        }
    }

    override fun onServiceStatusChanged(status: String) {
        mainHandler.post {
            ClinkSession.serviceStatus = status
            updateNotification()
        }
    }

    override fun onLog(message: String) {
        mainHandler.post {
            ClinkSession.logs.add(message)
            updateNotification()
        }
    }

    override fun onToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTransferUpdated(transfer: TransferItem) {
        mainHandler.post {
            val previousStatus = transferStatusCache[transfer.id]
            val index = ClinkSession.transfers.indexOfFirst { it.id == transfer.id }
            if (index >= 0) {
                ClinkSession.transfers[index] = transfer
            } else {
                ClinkSession.transfers.add(0, transfer)
            }
            transferStatusCache[transfer.id] = transfer.status
            maybeNotifyReceiveEvent(transfer, previousStatus)
            updateNotification()
        }
    }

    private fun updateNotification() {
        val runningCount = ClinkSession.transfers.count { it.status == TransferStatus.RUNNING }
        val text = when {
            runningCount > 0 -> "正在传输 $runningCount 个任务"
            ClinkSession.connectedPeers.isNotEmpty() -> "已连接 ${ClinkSession.connectedPeers.size} 台设备"
            else -> ClinkSession.serviceStatus
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun maybeNotifyReceiveEvent(transfer: TransferItem, previousStatus: TransferStatus?) {
        if (transfer.direction != TransferDirection.RECEIVE) return
        when {
            transfer.status == TransferStatus.RUNNING && previousStatus == null -> {
                showEventNotification(
                    notificationId = transfer.id.hashCode(),
                    title = "正在接收文件",
                    text = "来自 ${transfer.peerName}：${transfer.fileName}"
                )
                Toast.makeText(applicationContext, "正在接收：${transfer.fileName}", Toast.LENGTH_SHORT).show()
            }
            transfer.status == TransferStatus.COMPLETED && previousStatus != TransferStatus.COMPLETED -> {
                showEventNotification(
                    notificationId = transfer.id.hashCode(),
                    title = "文件接收完成",
                    text = "${transfer.fileName} 已保存到下载目录"
                )
                Toast.makeText(applicationContext, "接收完成：${transfer.fileName}", Toast.LENGTH_SHORT).show()
                transferStatusCache.remove(transfer.id)
            }
            transfer.status == TransferStatus.FAILED && previousStatus != TransferStatus.FAILED -> {
                showEventNotification(
                    notificationId = transfer.id.hashCode(),
                    title = "文件接收失败",
                    text = transfer.message.ifBlank { transfer.fileName }
                )
                transferStatusCache.remove(transfer.id)
            }
            transfer.status == TransferStatus.CANCELED && previousStatus != TransferStatus.CANCELED -> {
                transferStatusCache.remove(transfer.id)
            }
        }
    }

    private fun showEventNotification(notificationId: Int, title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            notificationId,
            NotificationCompat.Builder(this, EVENT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("clink 后台服务")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(CHANNEL_ID, "clink 后台服务", NotificationManager.IMPORTANCE_LOW)
            val eventChannel = NotificationChannel(EVENT_CHANNEL_ID, "clink 传输通知", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(eventChannel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "clink_foreground"
        private const val EVENT_CHANNEL_ID = "clink_events"
        private const val NOTIFICATION_ID = 1001
    }
}
