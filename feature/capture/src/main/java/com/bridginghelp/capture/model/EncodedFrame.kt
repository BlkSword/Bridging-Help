package com.bridginghelp.capture.model

/**
 * 编码帧
 * 表示编码后的视频帧数据
 */
data class EncodedFrame(
    val data: ByteArray,
    val timestamp: Long,
    val isKeyFrame: Boolean,
    val rotation: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncodedFrame

        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (isKeyFrame != other.isKeyFrame) return false
        if (rotation != other.rotation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + rotation
        return result
    }
}
