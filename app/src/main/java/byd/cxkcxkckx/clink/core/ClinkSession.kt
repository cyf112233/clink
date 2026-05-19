package byd.cxkcxkckx.clink.core

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import byd.cxkcxkckx.clink.service.ClinkForegroundService

object ClinkSession {

    interface Controller {
        fun connectTo(device: DeviceItem)
        fun respondToIncomingRequest(accepted: Boolean)
        fun sendFilesToPeers(uris: List<android.net.Uri>, category: FileCategory, peerIds: List<String>)
        fun cancelTransfer(transferId: String)
    }

    val discoveredDevices = mutableStateListOf<DeviceItem>()
    val connectedPeers = mutableStateListOf<PeerItem>()
    val logs = mutableStateListOf<String>()
    val transfers = mutableStateListOf<TransferItem>()
    val selectedPeerIds = mutableStateListOf<String>()

    var incomingRequest by mutableStateOf<IncomingRequest?>(null)
    var serviceStatus by mutableStateOf("正在启动服务…")
    var localDeviceName by mutableStateOf("clink")

    private var controller: Controller? = null

    fun ensureServiceRunning(context: Context) {
        val intent = Intent(context, ClinkForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun attachController(controller: Controller, deviceName: String) {
        this.controller = controller
        localDeviceName = deviceName
    }

    fun detachController(controller: Controller) {
        if (this.controller === controller) {
            this.controller = null
        }
    }

    fun connectTo(device: DeviceItem) {
        controller?.connectTo(device)
    }

    fun respondToIncomingRequest(accepted: Boolean) {
        controller?.respondToIncomingRequest(accepted)
    }

    fun togglePeerSelection(peerId: String) {
        if (selectedPeerIds.contains(peerId)) {
            selectedPeerIds.remove(peerId)
        } else {
            selectedPeerIds.add(peerId)
        }
    }

    fun clearPeerSelection() {
        selectedPeerIds.clear()
    }

    fun sendFiles(uris: List<android.net.Uri>, category: FileCategory) {
        controller?.sendFilesToPeers(uris, category, selectedPeerIds.toList())
    }

    fun cancelTransfer(transferId: String) {
        controller?.cancelTransfer(transferId)
    }
}
