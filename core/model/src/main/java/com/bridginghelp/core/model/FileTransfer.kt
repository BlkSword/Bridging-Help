package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 文件传输状态
 */
@Serializable
sealed class FileTransferState {
    /** 等待中 */
    @Serializable
    data object Waiting : FileTransferState()

    /** 传输中 */
    @Serializable
    data class Transferring(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val transferredBytes: Long = 0,
        val bytesPerSecond: Long = 0
    ) : FileTransferState() {
        val progress: Float
            get() = if (fileSize > 0) transferredBytes.toFloat() / fileSize.toFloat() else 0f

        val percent: Int
            get() = (progress * 100).toInt()
    }

    /** 已完成 */
    @Serializable
    data class Completed(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val durationMs: Long
    ) : FileTransferState()

    /** 失败 */
    @Serializable
    data class Failed(
        val transferId: String,
        val fileName: String,
        val error: String
    ) : FileTransferState()

    /** 已取消 */
    @Serializable
    data class Cancelled(
        val transferId: String,
        val fileName: String
    ) : FileTransferState()
}

/**
 * 文件传输请求
 */
@Serializable
data class FileTransferRequest(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val checksum: String? = null
) {
    companion object {
        fun createId(): String = "transfer_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
}

/**
 * 文件块（分块传输）
 */
@Serializable
data class FileChunk(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray,
    val offset: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileChunk

        if (transferId != other.transferId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (offset != other.offset) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + offset.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * 文件传输配置
 */
@Serializable
data class FileTransferConfig(
    val chunkSize: Int = 64 * 1024, // 64KB per chunk
    val maxConcurrentTransfers: Int = 3,
    val retryCount: Int = 3,
    val timeoutMs: Long = 30000
) {
    companion object {
        val DEFAULT = FileTransferConfig()
    }
}

/**
 * 文件信息
 */
@Serializable
data class FileInfo(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String? = null,
    val checksum: String? = null
)
