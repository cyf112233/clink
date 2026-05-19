package byd.cxkcxkckx.clink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import byd.cxkcxkckx.clink.core.ClinkSession
import byd.cxkcxkckx.clink.core.FileCategory
import byd.cxkcxkckx.clink.ui.navigation.ClinkNavHost
import byd.cxkcxkckx.clink.ui.theme.ClinkAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClinkSession.ensureServiceRunning(this)
        setContent {
            ClinkAppTheme {
                ClinkApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ClinkSession.ensureServiceRunning(this)
    }

    @Composable
    private fun ClinkApp() {
        val photoPicker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                ClinkSession.sendFiles(uris, FileCategory.PHOTO)
            }
        }
        val videoPicker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                ClinkSession.sendFiles(uris, FileCategory.VIDEO)
            }
        }
        val filePicker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                ClinkSession.sendFiles(uris, FileCategory.FILE)
            }
        }

        val pending = ClinkSession.incomingRequest
        if (pending != null) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("收到连接请求") },
                text = { Text("设备名称：${pending.deviceName}\n是否同意连接？") },
                confirmButton = {
                    TextButton(onClick = {
                        ClinkSession.respondToIncomingRequest(true)
                        ClinkSession.incomingRequest = null
                    }) {
                        Text("同意")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        ClinkSession.respondToIncomingRequest(false)
                        ClinkSession.incomingRequest = null
                    }) {
                        Text("拒绝")
                    }
                }
            )
        }

        ClinkNavHost(
            deviceName = ClinkSession.localDeviceName,
            serviceStatus = ClinkSession.serviceStatus,
            connectedPeers = ClinkSession.connectedPeers,
            discoveredDevices = ClinkSession.discoveredDevices,
            logs = ClinkSession.logs,
            transfers = ClinkSession.transfers,
            selectedPeerIds = ClinkSession.selectedPeerIds,
            onConnectDevice = { ClinkSession.connectTo(it) },
            onToggleTransferPeer = { ClinkSession.togglePeerSelection(it) },
            onCancelTransfer = { ClinkSession.cancelTransfer(it) },
            onOpenPhotos = { photoPicker.launch(arrayOf("image/*")) },
            onOpenVideos = { videoPicker.launch(arrayOf("video/*")) },
            onOpenFiles = { filePicker.launch(arrayOf("*/*")) }
        )
    }
}
