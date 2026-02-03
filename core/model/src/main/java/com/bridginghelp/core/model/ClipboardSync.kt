package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 剪贴板内容类型
 */
@Serializable
enum class ClipboardType {
    TEXT,
    IMAGE,
    HTML,
    URI
}

/**
 * 剪贴板数据
 */
@Serializable
sealed class ClipboardData {
    /**
     * 文本内容
     */
    @Serializable
    data class Text(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ClipboardData()

    /**
     * 图片内容
     */
    @Serializable
    data class Image(
        val data: ByteArray,
        val width: Int = 0,
        val height: Int = 0,
        val format: String = "png",
        val timestamp: Long = System.currentTimeMillis()
    ) : ClipboardData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (width != other.width) return false
            if (height != other.height) return false
            if (format != other.format) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + format.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * HTML 内容
     */
    @Serializable
    data class Html(
        val html: String,
        val text: String, // 纯文本备份
        val timestamp: Long = System.currentTimeMillis()
    ) : ClipboardData()

    /**
     * URI 内容
     */
    @Serializable
    data class Uri(
        val uri: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ClipboardData()
}

/**
 * 剪贴板同步事件
 */
@Serializable
data class ClipboardSyncEvent(
    val deviceId: String,
    val data: ClipboardData,
    val sequence: Long = System.currentTimeMillis()
)
