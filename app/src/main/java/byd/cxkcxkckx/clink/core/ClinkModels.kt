package byd.cxkcxkckx.clink.core

enum class FileCategory { PHOTO, VIDEO, FILE }

data class DeviceItem(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val serviceName: String
)

data class PeerItem(
    val id: String,
    val name: String,
    val address: String,
    val status: String
)

data class IncomingRequest(
    val connectionId: String,
    val deviceName: String
)

enum class TransferDirection { SEND, RECEIVE }

enum class TransferStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELED }

data class TransferItem(
    val id: String,
    val peerId: String,
    val peerName: String,
    val fileName: String,
    val category: FileCategory,
    val direction: TransferDirection,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
