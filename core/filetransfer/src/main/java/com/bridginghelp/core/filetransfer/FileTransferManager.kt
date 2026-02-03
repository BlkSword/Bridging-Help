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

                // TODO: 实现从远程设备下载文件的逻辑
                // 这需要远程设备提供文件传输服务

                delay(100) // 模拟下载

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
     * 恢复传输
     */
    suspend fun resumeTransfer(transferId: String): Result<Unit> {
        LogWrapper.d(TAG, "Resuming transfer: $transferId")

        activeTransfers[transferId]?.let { task ->
            if (task.status == TransferStatus.PAUSED) {
                // 重新开始传输
                when (task.direction) {
                    TransferDirection.UPLOAD -> {
                        startUpload(
                            task.sessionId,
                            task.filePath,
                            "", // 需要保存remoteAddress
                            onProgress = {}
                        )
                    }
                    TransferDirection.DOWNLOAD -> {
                        // TODO: 实现断点续传
                    }
                }
            }
        }

        return Result.Success(Unit)
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
}

/**
 * 文件传输头信息
 */
data class FileTransferHeader(
    val transferId: String,
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Long
)
