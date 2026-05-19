package byd.cxkcxkckx.clink.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class ClinkEngine(
    private val context: Context,
    private val callback: Callback
) {
    interface Callback {
        fun onDeviceDiscovered(device: DeviceItem)
        fun onDeviceLost(id: String)
        fun onPeerConnected(peer: PeerItem)
        fun onPeerDisconnected(id: String, reason: String)
        fun onIncomingConnectRequest(request: IncomingRequest)
        fun onServiceStatusChanged(status: String)
        fun onLog(message: String)
        fun onToast(message: String)
        fun onTransferUpdated(transfer: TransferItem)
    }

    val localDeviceName: String = buildLocalDeviceName()
    var localServiceStatus: String = "正在启动服务…"
        private set

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val ioPool = Executors.newCachedThreadPool()
    private val transferPool = Executors.newFixedThreadPool(3)
    private val running = AtomicBoolean(false)
    private val activePeers = ConcurrentHashMap<String, PeerConnection>()
    private val transferJobs = ConcurrentHashMap<String, Future<*>>()
    private val canceledTransfers = ConcurrentHashMap<String, AtomicBoolean>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serverSocket: ServerSocket? = null
    private var pendingIncoming: PeerConnection? = null

    fun start() {
        if (running.getAndSet(true)) return
        startServer()
    }

    fun stop() {
        running.set(false)
        stopDiscovery()
        unregisterService()
        closeServer()
        pendingIncoming?.close()
        pendingIncoming = null
        updateServiceStatus("服务已停止")
    }

    fun shutdown() {
        stop()
        activePeers.values.forEach { it.close() }
        activePeers.clear()
        ioPool.shutdownNow()
        transferPool.shutdownNow()
    }

    fun connectTo(device: DeviceItem) {
        ioPool.execute {
            val already = activePeers.values.any { it.name == device.name && it.host == device.host }
            if (already) {
                callback.onToast("已经连接过该设备")
                return@execute
            }
            try {
                val socket = Socket(device.host, device.port)
                val peer = PeerConnection(socket)
                val request = JSONObject().apply {
                    put("type", "connect_request")
                    put("deviceName", localDeviceName)
                    put("peerId", peer.id)
                }
                peer.sendJson(request)
                val response = peer.readJson() ?: throw IOException("未收到连接响应")
                if (response.optString("type") != "connect_response") {
                    throw IOException("响应格式错误")
                }
                if (!response.optBoolean("accepted", false)) {
                    peer.close()
                    callback.onLog("${device.name} 拒绝了连接")
                    callback.onToast("对方拒绝了连接")
                    return@execute
                }
                peer.name = response.optString("deviceName", device.name)
                registerConnectedPeer(peer)
                peer.startReadLoop()
                callback.onLog("已连接 ${peer.name}")
            } catch (e: Exception) {
                callback.onLog("连接失败：${device.name} - ${e.message}")
                callback.onToast("连接失败：${device.name}")
            }
        }
    }

    fun respondToIncomingRequest(accepted: Boolean) {
        val peer = pendingIncoming ?: return
        pendingIncoming = null
        ioPool.execute {
            try {
                val response = JSONObject().apply {
                    put("type", "connect_response")
                    put("accepted", accepted)
                    put("deviceName", localDeviceName)
                }
                peer.sendJson(response)
                if (accepted) {
                    registerConnectedPeer(peer)
                    peer.startReadLoop()
                    callback.onLog("已接受 ${peer.name} 的连接")
                } else {
                    callback.onLog("已拒绝 ${peer.name} 的连接")
                    peer.close()
                }
            } catch (e: Exception) {
                peer.close()
                callback.onLog("处理连接请求失败：${e.message}")
            }
        }
    }

    fun sendFiles(uris: List<Uri>, category: FileCategory, peerIds: List<String>) {
        if (activePeers.isEmpty()) {
            callback.onToast("请先连接设备")
            return
        }
        if (peerIds.isEmpty()) {
            callback.onToast("请先选择要发送到的设备")
            return
        }
        val peerMap = activePeers.values.associateBy { it.id }
        val peers = peerIds.mapNotNull { peerMap[it] }
        if (peers.isEmpty()) {
            callback.onToast("所选设备当前不可用")
            return
        }
        uris.forEach { uri ->
            val name = displayName(uri)
            val size = querySize(uri).coerceAtLeast(0L)
            peers.forEach { peer ->
                val task = TransferItem(
                    id = UUID.randomUUID().toString(),
                    peerId = peer.id,
                    peerName = peer.name,
                    fileName = name,
                    category = category,
                    direction = TransferDirection.SEND,
                    totalBytes = size,
                    transferredBytes = 0L,
                    status = TransferStatus.QUEUED,
                    message = "等待队列"
                )
                callback.onTransferUpdated(task)
                canceledTransfers[task.id] = AtomicBoolean(false)
                val future = transferPool.submit {
                    try {
                        ensureNotCanceled(task)
                        callback.onTransferUpdated(task.copy(status = TransferStatus.RUNNING, message = "发送中"))
                        sendOneFile(peer, uri, category, task.id, size) { sent ->
                            ensureNotCanceled(task)
                            callback.onTransferUpdated(
                                task.copy(
                                    transferredBytes = sent,
                                    status = TransferStatus.RUNNING,
                                    message = "发送中"
                                )
                            )
                        }
                        ensureNotCanceled(task)
                        callback.onTransferUpdated(
                            task.copy(
                                transferredBytes = size,
                                status = TransferStatus.COMPLETED,
                                message = "发送完成"
                            )
                        )
                        callback.onLog("已发送：$name -> ${peer.name}")
                    } catch (_: CancellationException) {
                        callback.onTransferUpdated(
                            task.copy(
                                status = TransferStatus.CANCELED,
                                message = "已取消"
                            )
                        )
                        callback.onLog("已取消：$name -> ${peer.name}")
                    } catch (e: Exception) {
                        callback.onTransferUpdated(
                            task.copy(
                                status = TransferStatus.FAILED,
                                message = e.message ?: "发送失败"
                            )
                        )
                        callback.onLog("发送失败：$name -> ${peer.name}，${e.message}")
                    } finally {
                        transferJobs.remove(task.id)
                        canceledTransfers.remove(task.id)
                    }
                }
                transferJobs[task.id] = future
            }
        }
    }

    fun cancelTransfer(transferId: String) {
        canceledTransfers[transferId]?.set(true)
        transferJobs[transferId]?.cancel(false)
    }

    private fun startServer() {
        Thread {
            try {
                val socket = ServerSocket(0)
                serverSocket = socket
                updateServiceStatus("服务端口 ${socket.localPort}，正在广播")
                registerService(socket.localPort)
                startDiscovery()
                while (running.get()) {
                    val client = socket.accept()
                    ioPool.execute { handleIncomingSocket(client) }
                }
            } catch (_: IOException) {
            }
        }.start()
    }

    private fun handleIncomingSocket(socket: Socket) {
        val peer = PeerConnection(socket)
        try {
            val first = peer.readJson() ?: throw IOException("空请求")
            when (first.optString("type")) {
                "connect_request" -> {
                    peer.name = first.optString("deviceName", "未知设备")
                    pendingIncoming = peer
                    callback.onIncomingConnectRequest(IncomingRequest(peer.id, peer.name))
                }
                else -> peer.close()
            }
        } catch (e: Exception) {
            peer.close()
            callback.onLog("收到连接时出错：${e.message}")
        }
    }

    private fun registerConnectedPeer(peer: PeerConnection) {
        activePeers[peer.id] = peer
        callback.onPeerConnected(PeerItem(peer.id, peer.name, peer.host, "已连接"))
    }

    private fun sendOneFile(
        peer: PeerConnection,
        uri: Uri,
        category: FileCategory,
        transferId: String,
        knownSize: Long,
        onProgress: (Long) -> Unit
    ) {
        val resolver = context.contentResolver
        val name = displayName(uri)
        val mime = resolver.getType(uri) ?: guessMime(name, category)
        val size = if (knownSize > 0L) knownSize else querySize(uri)
        if (size <= 0L) throw IOException("无法获取文件大小")
        val meta = JSONObject().apply {
            put("type", "file")
            put("transferId", transferId)
            put("fileName", name)
            put("mimeType", mime)
            put("category", category.name)
            put("fileSize", size)
        }
        synchronized(peer.lock) {
            peer.sendJson(meta)
            resolver.openInputStream(uri).use { input ->
                if (input == null) throw IOException("无法打开文件")
                val buffered = BufferedInputStream(input)
                val output = peer.output
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var sent = 0L
                while (buffered.read(buffer).also { read = it } != -1) {
                    if (canceledTransfers[transferId]?.get() == true) {
                        throw CancellationException("任务已取消")
                    }
                    output.write(buffer, 0, read)
                    sent += read.toLong()
                    onProgress(sent)
                }
                output.flush()
            }
        }
    }

    private fun ensureNotCanceled(task: TransferItem) {
        if (canceledTransfers[task.id]?.get() == true) {
            throw CancellationException("任务已取消")
        }
    }

    private fun startDiscovery() {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                callback.onLog("发现服务启动失败：$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                callback.onLog("发现服务停止失败：$errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                callback.onLog("开始扫描局域网设备")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                callback.onLog("已停止扫描")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                if (serviceInfo.serviceName.startsWith(localDeviceName)) return
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        callback.onLog("解析设备失败：$errorCode")
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host ?: return
                        val item = DeviceItem(
                            id = resolved.serviceName,
                            name = parseDisplayName(resolved.serviceName),
                            host = host.hostAddress ?: host.toString(),
                            port = resolved.port,
                            serviceName = resolved.serviceName
                        )
                        callback.onDeviceDiscovered(item)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                callback.onDeviceLost(serviceInfo.serviceName)
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun stopDiscovery() {
        val listener = discoveryListener ?: return
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }
        discoveryListener = null
    }

    private fun registerService(port: Int) {
        unregisterService()
        val info = NsdServiceInfo().apply {
            serviceName = "$localDeviceName-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                updateServiceStatus("已广播：${serviceInfo.serviceName}")
                callback.onLog(localServiceStatus)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                updateServiceStatus("广播失败：$errorCode")
                callback.onLog(localServiceStatus)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                callback.onLog("已停止广播")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                callback.onLog("停止广播失败：$errorCode")
            }
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun unregisterService() {
        val listener = registrationListener ?: return
        try {
            nsdManager.unregisterService(listener)
        } catch (_: Exception) {
        }
        registrationListener = null
    }

    private fun closeServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun updateServiceStatus(status: String) {
        localServiceStatus = status
        callback.onServiceStatusChanged(status)
    }

    private fun displayName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index)
            }
        }
        return "file_${System.currentTimeMillis()}"
    }

    private fun querySize(uri: Uri): Long {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) return it.getLong(index)
            }
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
    }

    private fun parseDisplayName(serviceName: String): String {
        return serviceName.substringBeforeLast("-")
    }

    private fun guessMime(name: String, category: FileCategory): String {
        val ext = name.substringAfterLast('.', "")
        val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.getDefault()))
        if (!guessed.isNullOrBlank()) return guessed
        return when (category) {
            FileCategory.PHOTO -> "image/*"
            FileCategory.VIDEO -> "video/*"
            FileCategory.FILE -> "application/octet-stream"
        }
    }

    private fun buildLocalDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty().ifBlank { "Android" }
        return "clink-$model"
    }

    private inner class PeerConnection(socket: Socket) {
        val id: String = UUID.randomUUID().toString()
        val lock = Any()
        val socket: Socket = socket
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        var name: String = socket.inetAddress.hostAddress ?: "未知设备"
        val host: String = socket.inetAddress.hostAddress ?: "unknown"

        fun sendJson(json: JSONObject) {
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }

        fun readJson(): JSONObject? {
            return try {
                val length = input.readInt()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                JSONObject(String(bytes, Charsets.UTF_8))
            } catch (_: EOFException) {
                null
            }
        }

        fun startReadLoop() {
            ioPool.execute {
                try {
                    while (running.get() && !socket.isClosed) {
                        val msg = readJson() ?: break
                        if (msg.optString("type") == "file") {
                            receiveFile(msg)
                        }
                    }
                } catch (e: Exception) {
                    callback.onLog("${name} 连接异常：${e.message}")
                } finally {
                    activePeers.remove(id)
                    close()
                    callback.onPeerDisconnected(id, "已断开")
                }
            }
        }

        private fun receiveFile(meta: JSONObject) {
            val fileName = meta.optString("fileName", "received_${System.currentTimeMillis()}")
            val mime = meta.optString("mimeType", "application/octet-stream")
            val fileSize = meta.optLong("fileSize", -1L)
            val transferId = "recv-${meta.optString("transferId", UUID.randomUUID().toString())}"
            val category = runCatching {
                FileCategory.valueOf(meta.optString("category", FileCategory.FILE.name))
            }.getOrDefault(FileCategory.FILE)
            val base = TransferItem(
                id = transferId,
                peerId = id,
                peerName = name,
                fileName = fileName,
                category = category,
                direction = TransferDirection.RECEIVE,
                totalBytes = fileSize.coerceAtLeast(0L),
                transferredBytes = 0L,
                status = TransferStatus.RUNNING,
                message = "接收中"
            )
            callback.onTransferUpdated(base)
            val saved = saveIncomingFile(context, fileName, mime, fileSize, input) { received ->
                callback.onTransferUpdated(
                    base.copy(
                        transferredBytes = received,
                        status = TransferStatus.RUNNING,
                        message = "接收中"
                    )
                )
            }
            callback.onTransferUpdated(
                base.copy(
                    transferredBytes = fileSize.coerceAtLeast(0L),
                    status = TransferStatus.COMPLETED,
                    message = "已保存到下载目录"
                )
            )
            callback.onLog("收到来自 $name 的文件：${saved.absolutePath}")
            callback.onToast("已接收：${saved.name}")
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_clink._tcp."
    }
}

private fun saveIncomingFile(
    context: Context,
    fileName: String,
    mimeType: String,
    size: Long,
    input: DataInputStream,
    onProgress: (Long) -> Unit
): File {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/clink")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: throw IOException("无法创建目标文件")
        resolver.openOutputStream(uri)?.use { out ->
            copyExactly(input, out, size, onProgress)
        } ?: throw IOException("无法写入目标文件")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "clink/$fileName")
    }
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "clink")
    if (!dir.exists()) dir.mkdirs()
    val outFile = File(dir, fileName)
    outFile.outputStream().use { out ->
        copyExactly(input, out, size, onProgress)
    }
    return outFile
}

private fun copyExactly(
    input: DataInputStream,
    out: java.io.OutputStream,
    size: Long,
    onProgress: (Long) -> Unit
) {
    if (size <= 0L) throw IOException("无效的文件大小")
    val buffer = ByteArray(64 * 1024)
    var remaining = size
    var copied = 0L
    while (remaining > 0) {
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read == -1) throw EOFException("文件流提前结束")
        out.write(buffer, 0, read)
        copied += read.toLong()
        remaining -= read.toLong()
        onProgress(copied)
    }
    out.flush()
}
