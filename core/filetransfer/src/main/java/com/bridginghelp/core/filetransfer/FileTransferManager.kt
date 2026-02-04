package com.bridginghelp.core.filetransfer

import android.content.Context
import com.bridginghelp.core.model.*
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.ErrorEntity
import com.bridginghelp.core.common.util.LogWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件传输状态
 */
sealed class FileTransferState {
    data object Idle : FileTransferState()
    data class Connecting(val sessionId: String) : FileTransferState()
    data class Transferring(
        val transferId: String,
        val fileName: String,
        val progress: Float,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speed: Long // bytes per second
    ) : FileTransferState()
    data class Paused(
        val transferId: String,
        val progress: Float
    ) : FileTransferState()
    data class Completed(
        val transferId: String,
        val fileName: String,
        val filePath: String
    ) : FileTransferState()
    data class Error(
        val transferId: String,
        val error: FileTransferError
    ) : FileTransferState()
}

/**
 * 文件传输错误
 */
sealed class FileTransferError {
    data object ConnectionFailed : FileTransferError()
    data object FileNotFound : FileTransferError()
    data object PermissionDenied : FileTransferError()
    data object DiskFull : FileTransferError()
    data object NetworkError : FileTransferError()
    data class Unknown(val message: String) : FileTransferError()
}

/**
 * 文件传输任务
 */
data class FileTransferTask(
    val transferId: String,
    val sessionId: String,
    val fileId: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.PENDING,
    val progress: Float = 0f,
    val bytesTransferred: Long = 0
)

/**
 * 传输方向
 */
enum class TransferDirection {
    UPLOAD,   // 上传（本地发送到远程）
    DOWNLOAD  // 下载（从远程接收）
}

/**
 * 传输状态
 */
enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 文件传输管理器
 * 负责管理设备间的文件传输
 */
@Singleton
class FileTransferManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FileTransferManager"
        private const val BUFFER_SIZE = 8192
        private const val DEFAULT_PORT = 52346
        private const val CHUNK_SIZE = 64 * 1024 // 64KB chunks
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _transferState = MutableStateFlow<FileTransferState>(FileTransferState.Idle)
    val transferState: StateFlow<FileTransferState> = _transferState.asStateFlow()

    private val activeTransfers = mutableMapOf<String, FileTransferTask>()
    private val transferJobs = mutableMapOf<String, Job>()

    /**
     * 开始上传文件
     */
    suspend fun startUpload(
        sessionId: String,
        filePath: String,
        remoteAddress: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        LogWrapper.d(TAG, "Starting upload: $filePath")

        val file = File(filePath)
        if (!file.exists()) {
            return Result.Error(ErrorEntity.Validation("File not found: $filePath"))
        }

        val transferId = generateTransferId()
        val task = FileTransferTask(
            transferId = transferId,
            sessionId = sessionId,
            fileId = UUID.randomUUID().toString(),
            fileName = file.name,
            filePath = filePath,
            fileSize = file.length(),
            direction = TransferDirection.UPLOAD
        )

        activeTransfers[transferId] = task

        val job = scope.launch {
            try {
                _transferState.value = FileTransferState.Connecting(sessionId)

                // 连接到远程设备
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(remoteAddress, DEFAULT_PORT), 10000)

                val outputStream = socket.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)

                // 发送文件头信息
                val header = FileTransferHeader(
                    transferId = transferId,
                    fileId = task.fileId,
                    fileName = task.fileName,
                    fileSize = task.fileSize,
                    chunkSize = CHUNK_SIZE.toLong()
                )

                // 发送header（JSON格式）
                val headerJson = serializeHeader(header)
                dataOutputStream.writeInt(headerJson.toByteArray().size)
                dataOutputStream.write(headerJson.toByteArray())
                dataOutputStream.flush()

                // 发送文件内容
                val fileInputStream = FileInputStream(file)
                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytesRead = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var bytesInSecond = 0L

                _transferState.value = FileTransferState.Transferring(
                    transferId = transferId,
                    fileName = task.fileName,
                    progress = 0f,
                    bytesTransferred = 0,
                    totalBytes = task.fileSize,
                    speed = 0
                )

                while (true) {
                    val bytesRead = fileInputStream.read(buffer)
                    if (bytesRead == -1) break

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesInSecond += bytesRead

                    // 更新进度
                    val progress = (totalBytesRead.toFloat() / task.fileSize * 100)
                    onProgress(progress)

                    // 每秒更新一次状态
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 1000) {
                        _transferState.value = FileTransferState.Transferring(
                            transferId = transferId,
                            fileName = task.fileName,
                            progress = progress,
                            bytesTransferred = totalBytesRead,
                            totalBytes = task.fileSize,
                            speed = bytesInSecond
                        )
                        bytesInSecond = 0
                        lastUpdateTime = currentTime
                    }
                }

                fileInputStream.close()
                socket.close()

                // 更新任务状态
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.COMPLETED,
                    progress = 100f,
                    bytesTransferred = totalBytesRead
                )

                _transferState.value = FileTransferState.Completed(
                    transferId = transferId,
                    fileName = task.fileName,
                    filePath = filePath
                )

                LogWrapper.i(TAG, "Upload completed: $filePath")

            } catch (e: Exception) {
                LogWrapper.e(TAG, "Upload failed", e)
                _transferState.value = FileTransferState.Error(
                    transferId = transferId,
                    error = FileTransferError.Unknown(e.message ?: "Unknown error")
                )
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.FAILED
                )
            }
        }

        transferJobs[transferId] = job
        return Result.Success(transferId)
    }

    /**
     * 开始下载文件
     */
    suspend fun startDownload(
        sessionId: String,
        fileInfo: FileInfo,
        savePath: String,
        remoteAddress: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        LogWrapper.d(TAG, "Starting download: ${fileInfo.fileName}")

        val transferId = generateTransferId()
        val task = FileTransferTask(
            transferId = transferId,
            sessionId = sessionId,
            fileId = fileInfo.fileId,
            fileName = fileInfo.fileName,
            filePath = savePath,
            fileSize = fileInfo.fileSize,
            direction = TransferDirection.DOWNLOAD
        )

        activeTransfers[transferId] = task

        val job = scope.launch {
            try {
                _transferState.value = FileTransferState.Connecting(sessionId)

                // 连接到远程设备
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(remoteAddress, DEFAULT_PORT), 10000)

                val inputStream = socket.getInputStream()
                val dataInputStream = DataInputStream(inputStream)

                // 发送文件下载请求
                val request = FileTransferRequest(
                    transferId = transferId,
                    fileId = fileInfo.fileId,
                    fileName = fileInfo.fileName,
                    offset = 0 // 从头开始下载
                )
                val requestJson = serializeRequest(request)

                // 发送请求
                val outputStream = socket.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeInt(requestJson.toByteArray().size)
                dataOutputStream.write(requestJson.toByteArray())
                dataOutputStream.flush()

                // 接收文件头
                val headerSize = dataInputStream.readInt()
                val headerBytes = ByteArray(headerSize)
                dataInputStream.readFully(headerBytes)
                val headerJson = String(headerBytes)
                val header = deserializeHeader(headerJson)

                // 创建文件并保存
                val outputFile = File(savePath)
                val fileDir = outputFile.parentFile
                if (fileDir != null && !fileDir.exists()) {
                    fileDir.mkdirs()
                }

                val fileOutputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytesRead = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var bytesInSecond = 0L

                _transferState.value = FileTransferState.Transferring(
                    transferId = transferId,
                    fileName = task.fileName,
                    progress = 0f,
                    bytesTransferred = 0,
                    totalBytes = task.fileSize,
                    speed = 0
                )

                while (totalBytesRead < header.fileSize) {
                    val bytesToRead = minOf(buffer.size.toLong(), (header.fileSize - totalBytesRead)).toInt()
                    val bytesRead = dataInputStream.read(buffer, 0, bytesToRead)
                    if (bytesRead == -1) break

                    fileOutputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesInSecond += bytesRead

                    // 更新进度
                    val progress = (totalBytesRead.toFloat() / header.fileSize * 100)
                    onProgress(progress)

                    // 每秒更新一次状态
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 1000) {
                        _transferState.value = FileTransferState.Transferring(
                            transferId = transferId,
                            fileName = task.fileName,
                            progress = progress,
                            bytesTransferred = totalBytesRead,
                            totalBytes = header.fileSize,
                            speed = bytesInSecond
                        )
                        bytesInSecond = 0
                        lastUpdateTime = currentTime
                    }
                }

                fileOutputStream.close()
                socket.close()

                // 更新任务状态
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.COMPLETED,
                    progress = 100f,
                    bytesTransferred = totalBytesRead
                )

                _transferState.value = FileTransferState.Completed(
                    transferId = transferId,
                    fileName = task.fileName,
                    filePath = savePath
                )

                LogWrapper.i(TAG, "Download completed: ${fileInfo.fileName}")

            } catch (e: Exception) {
                LogWrapper.e(TAG, "Download failed", e)
                _transferState.value = FileTransferState.Error(
                    transferId = transferId,
                    error = FileTransferError.Unknown(e.message ?: "Unknown error")
                )
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.FAILED
                )
            }
        }

        transferJobs[transferId] = job
        return Result.Success(transferId)
    }

    /**
     * 暂停传输
     */
    suspend fun pauseTransfer(transferId: String): Result<Unit> {
        LogWrapper.d(TAG, "Pausing transfer: $transferId")

        transferJobs[transferId]?.cancel()
        activeTransfers[transferId]?.let { task ->
            if (task.status == TransferStatus.IN_PROGRESS) {
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.PAUSED
                )
                _transferState.value = FileTransferState.Paused(
                    transferId = transferId,
                    progress = task.progress
                )
            }
        }

        return Result.Success(Unit)
    }

    /**
     * 恢复传输（支持断点续传）
     */
    suspend fun resumeTransfer(
        transferId: String,
        remoteAddress: String = "",
        fileInfo: FileInfo? = null
    ): Result<Unit> {
        LogWrapper.d(TAG, "Resuming transfer: $transferId")

        activeTransfers[transferId]?.let { task ->
            if (task.status == TransferStatus.PAUSED) {
                // 计算已传输的字节数（用于断点续传）
                val offset = if (task.bytesTransferred > 0) task.bytesTransferred else 0L

                when (task.direction) {
                    TransferDirection.UPLOAD -> {
                        // 断点续传上传
                        startUploadWithOffset(
                            sessionId = task.sessionId,
                            filePath = task.filePath,
                            remoteAddress = remoteAddress,
                            offset = offset,
                            onProgress = {}
                        )
                    }
                    TransferDirection.DOWNLOAD -> {
                        // 断点续传下载
                        val info = fileInfo ?: FileInfo(
                            fileId = task.fileId,
                            fileName = task.fileName,
                            fileSize = task.fileSize,
                            mimeType = null,
                            checksum = null
                        )
                        startDownloadWithOffset(
                            sessionId = task.sessionId,
                            fileInfo = info,
                            savePath = task.filePath,
                            remoteAddress = remoteAddress,
                            offset = offset,
                            onProgress = {}
                        )
                    }
                }
            }
        }

        return Result.Success(Unit)
    }

    /**
     * 带偏移量的上传（断点续传）
     */
    private suspend fun startUploadWithOffset(
        sessionId: String,
        filePath: String,
        remoteAddress: String,
        offset: Long,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        LogWrapper.d(TAG, "Starting upload with offset: $filePath, offset: $offset")

        val file = File(filePath)
        if (!file.exists()) {
            return Result.Error(ErrorEntity.Validation("File not found: $filePath"))
        }

        val transferId = generateTransferId()
        val task = FileTransferTask(
            transferId = transferId,
            sessionId = sessionId,
            fileId = UUID.randomUUID().toString(),
            fileName = file.name,
            filePath = filePath,
            fileSize = file.length(),
            direction = TransferDirection.UPLOAD,
            bytesTransferred = offset
        )

        activeTransfers[transferId] = task

        val job = scope.launch {
            try {
                _transferState.value = FileTransferState.Connecting(sessionId)

                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(remoteAddress, DEFAULT_PORT), 10000)

                val outputStream = socket.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)

                // 发送带偏移量的文件头信息
                val header = FileTransferHeader(
                    transferId = transferId,
                    fileId = task.fileId,
                    fileName = task.fileName,
                    fileSize = task.fileSize,
                    chunkSize = CHUNK_SIZE.toLong(),
                    offset = offset // 断点续传偏移量
                )

                val headerJson = serializeHeaderWithOffset(header)
                dataOutputStream.writeInt(headerJson.toByteArray().size)
                dataOutputStream.write(headerJson.toByteArray())
                dataOutputStream.flush()

                // 从偏移量位置开始读取文件
                val fileInputStream = FileInputStream(file)
                fileInputStream.skip(offset) // 跳过已传输的字节

                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytesRead = offset
                var lastUpdateTime = System.currentTimeMillis()
                var bytesInSecond = 0L

                _transferState.value = FileTransferState.Transferring(
                    transferId = transferId,
                    fileName = task.fileName,
                    progress = (offset.toFloat() / task.fileSize * 100),
                    bytesTransferred = offset,
                    totalBytes = task.fileSize,
                    speed = 0
                )

                while (true) {
                    val bytesRead = fileInputStream.read(buffer)
                    if (bytesRead == -1) break

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesInSecond += bytesRead

                    val progress = (totalBytesRead.toFloat() / task.fileSize * 100)
                    onProgress(progress)

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 1000) {
                        _transferState.value = FileTransferState.Transferring(
                            transferId = transferId,
                            fileName = task.fileName,
                            progress = progress,
                            bytesTransferred = totalBytesRead,
                            totalBytes = task.fileSize,
                            speed = bytesInSecond
                        )
                        bytesInSecond = 0
                        lastUpdateTime = currentTime
                    }
                }

                fileInputStream.close()
                socket.close()

                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.COMPLETED,
                    progress = 100f,
                    bytesTransferred = totalBytesRead
                )

                _transferState.value = FileTransferState.Completed(
                    transferId = transferId,
                    fileName = task.fileName,
                    filePath = filePath
                )

                LogWrapper.i(TAG, "Upload with offset completed: $filePath")

            } catch (e: Exception) {
                LogWrapper.e(TAG, "Upload with offset failed", e)
                _transferState.value = FileTransferState.Error(
                    transferId = transferId,
                    error = FileTransferError.Unknown(e.message ?: "Unknown error")
                )
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.FAILED
                )
            }
        }

        transferJobs[transferId] = job
        return Result.Success(transferId)
    }

    /**
     * 带偏移量的下载（断点续传）
     */
    private suspend fun startDownloadWithOffset(
        sessionId: String,
        fileInfo: FileInfo,
        savePath: String,
        remoteAddress: String,
        offset: Long,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        LogWrapper.d(TAG, "Starting download with offset: ${fileInfo.fileName}, offset: $offset")

        val transferId = generateTransferId()
        val task = FileTransferTask(
            transferId = transferId,
            sessionId = sessionId,
            fileId = fileInfo.fileId,
            fileName = fileInfo.fileName,
            filePath = savePath,
            fileSize = fileInfo.fileSize,
            direction = TransferDirection.DOWNLOAD,
            bytesTransferred = offset
        )

        activeTransfers[transferId] = task

        val job = scope.launch {
            try {
                _transferState.value = FileTransferState.Connecting(sessionId)

                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(remoteAddress, DEFAULT_PORT), 10000)

                val inputStream = socket.getInputStream()
                val dataInputStream = DataInputStream(inputStream)

                // 发送带偏移量的下载请求
                val request = FileTransferRequest(
                    transferId = transferId,
                    fileId = fileInfo.fileId,
                    fileName = fileInfo.fileName,
                    offset = offset // 断点续传偏移量
                )
                val requestJson = serializeRequest(request)

                val outputStream = socket.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeInt(requestJson.toByteArray().size)
                dataOutputStream.write(requestJson.toByteArray())
                dataOutputStream.flush()

                // 接收文件头
                val headerSize = dataInputStream.readInt()
                val headerBytes = ByteArray(headerSize)
                dataInputStream.readFully(headerBytes)
                val headerJson = String(headerBytes)
                val header = deserializeHeader(headerJson)

                // 以追加模式打开文件
                val outputFile = File(savePath)
                val fileOutputStream = FileOutputStream(outputFile, true) // 追加模式

                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytesRead = offset
                var lastUpdateTime = System.currentTimeMillis()
                var bytesInSecond = 0L

                _transferState.value = FileTransferState.Transferring(
                    transferId = transferId,
                    fileName = task.fileName,
                    progress = (offset.toFloat() / header.fileSize * 100),
                    bytesTransferred = offset,
                    totalBytes = header.fileSize,
                    speed = 0
                )

                while (totalBytesRead < header.fileSize) {
                    val bytesToRead = minOf(buffer.size.toLong(), (header.fileSize - totalBytesRead)).toInt()
                    val bytesRead = dataInputStream.read(buffer, 0, bytesToRead)
                    if (bytesRead == -1) break

                    fileOutputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesInSecond += bytesRead

                    val progress = (totalBytesRead.toFloat() / header.fileSize * 100)
                    onProgress(progress)

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 1000) {
                        _transferState.value = FileTransferState.Transferring(
                            transferId = transferId,
                            fileName = task.fileName,
                            progress = progress,
                            bytesTransferred = totalBytesRead,
                            totalBytes = header.fileSize,
                            speed = bytesInSecond
                        )
                        bytesInSecond = 0
                        lastUpdateTime = currentTime
                    }
                }

                fileOutputStream.close()
                socket.close()

                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.COMPLETED,
                    progress = 100f,
                    bytesTransferred = totalBytesRead
                )

                _transferState.value = FileTransferState.Completed(
                    transferId = transferId,
                    fileName = task.fileName,
                    filePath = savePath
                )

                LogWrapper.i(TAG, "Download with offset completed: ${fileInfo.fileName}")

            } catch (e: Exception) {
                LogWrapper.e(TAG, "Download with offset failed", e)
                _transferState.value = FileTransferState.Error(
                    transferId = transferId,
                    error = FileTransferError.Unknown(e.message ?: "Unknown error")
                )
                activeTransfers[transferId] = task.copy(
                    status = TransferStatus.FAILED
                )
            }
        }

        transferJobs[transferId] = job
        return Result.Success(transferId)
    }

    /**
     * 取消传输
     */
    suspend fun cancelTransfer(transferId: String): Result<Unit> {
        LogWrapper.d(TAG, "Canceling transfer: $transferId")

        transferJobs[transferId]?.cancel()
        transferJobs.remove(transferId)

        activeTransfers[transferId]?.let { task ->
            activeTransfers[transferId] = task.copy(
                status = TransferStatus.CANCELLED
            )
        }

        return Result.Success(Unit)
    }

    /**
     * 获取活动传输列表
     */
    fun getActiveTransfers(): List<FileTransferTask> {
        return activeTransfers.values.toList()
    }

    /**
     * 获取传输任务
     */
    fun getTransfer(transferId: String): FileTransferTask? {
        return activeTransfers[transferId]
    }

    /**
     * 清理已完成或失败的传输
     */
    fun cleanup() {
        val toRemove = activeTransfers.filterValues {
            it.status == TransferStatus.COMPLETED ||
            it.status == TransferStatus.FAILED ||
            it.status == TransferStatus.CANCELLED
        }.keys

        toRemove.forEach { transferId ->
            transferJobs.remove(transferId)
            activeTransfers.remove(transferId)
        }
    }

    /**
     * 生成传输ID
     */
    private fun generateTransferId(): String {
        return "transfer_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    /**
     * 序列化文件头
     */
    private fun serializeHeader(header: FileTransferHeader): String {
        return """
        {
            "transferId": "${header.transferId}",
            "fileId": "${header.fileId}",
            "fileName": "${header.fileName}",
            "fileSize": ${header.fileSize},
            "chunkSize": ${header.chunkSize}
        }
        """.trimIndent()
    }

    /**
     * 序列化带偏移量的文件头（断点续传）
     */
    private fun serializeHeaderWithOffset(header: FileTransferHeader): String {
        return """
        {
            "transferId": "${header.transferId}",
            "fileId": "${header.fileId}",
            "fileName": "${header.fileName}",
            "fileSize": ${header.fileSize},
            "chunkSize": ${header.chunkSize},
            "offset": ${header.offset}
        }
        """.trimIndent()
    }

    /**
     * 序列化文件下载请求
     */
    private fun serializeRequest(request: FileTransferRequest): String {
        return """
        {
            "transferId": "${request.transferId}",
            "fileId": "${request.fileId}",
            "fileName": "${request.fileName}",
            "offset": ${request.offset}
        }
        """.trimIndent()
    }

    /**
     * 反序列化文件头
     */
    private fun deserializeHeader(json: String): FileTransferHeader {
        // 简单的JSON解析，生产环境应使用kotlinx.serialization
        val transferId = Regex(""""transferId"\s*:\s*"([^"]+)""").find(json)?.groupValues?.get(1) ?: ""
        val fileId = Regex(""""fileId"\s*:\s*"([^"]+)""").find(json)?.groupValues?.get(1) ?: ""
        val fileName = Regex(""""fileName"\s*:\s*"([^"]+)""").find(json)?.groupValues?.get(1) ?: ""
        val fileSize = Regex(""""fileSize"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong() ?: 0L
        val chunkSize = Regex(""""chunkSize"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong() ?: CHUNK_SIZE.toLong()

        return FileTransferHeader(
            transferId = transferId,
            fileId = fileId,
            fileName = fileName,
            fileSize = fileSize,
            chunkSize = chunkSize,
            offset = 0L
        )
    }
}

/**
 * 文件传输头信息
 */
data class FileTransferHeader(
    val transferId: String,
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Long,
    val offset: Long = 0L // 断点续传偏移量
)

/**
 * 文件下载请求
 */
data class FileTransferRequest(
    val transferId: String,
    val fileId: String,
    val fileName: String,
    val offset: Long = 0L // 断点续传偏移量
)
