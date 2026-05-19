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
import byd.cxkcxkckx.clink.core.TransferItem
import byd.cxkcxkckx.clink.core.TransferStatus

class ClinkForegroundService : Service(), ClinkEngine.Callback, ClinkSession.Controller {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var engine: ClinkEngine

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
            val index = ClinkSession.connectedPeers.indexOfFirst { it.id == id }
            if (index >= 0) {
                ClinkSession.connectedPeers[index] = ClinkSession.connectedPeers[index].copy(status = reason)
            }
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
            val index = ClinkSession.transfers.indexOfFirst { it.id == transfer.id }
            if (index >= 0) {
                ClinkSession.transfers[index] = transfer
            } else {
                ClinkSession.transfers.add(0, transfer)
            }
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
            val channel = NotificationChannel(CHANNEL_ID, "clink 后台服务", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "clink_foreground"
        private const val NOTIFICATION_ID = 1001
    }
}
