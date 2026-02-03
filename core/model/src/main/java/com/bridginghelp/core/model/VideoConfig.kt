package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 视频配置
 * 定义屏幕捕获和编码的参数
 */
@Serializable
data class VideoConfig(
    val width: Int,
    val height: Int,
    val frameRate: Int = 30,
    val bitrate: Int = 2_000_000,  // 2 Mbps
    val codec: VideoCodec = VideoCodec.H264,
    val profile: Int? = null,
    val level: Int? = null
) {
    companion object {
        // 预设配置
        val LOW = VideoConfig(
            width = 480,
            height = 800,
            frameRate = 15,
            bitrate = 500_000
        )

        val MEDIUM = VideoConfig(
            width = 720,
            height = 1280,
            frameRate = 30,
            bitrate = 1_500_000
        )

        val HIGH = VideoConfig(
            width = 1080,
            height = 1920,
            frameRate = 30,
            bitrate = 2_500_000
        )

        val ULTRA = VideoConfig(
            width = 1440,
            height = 2560,
            frameRate = 60,
            bitrate = 4_000_000
        )

        fun getAspectRatio(width: Int, height: Int): Float {
            return width.toFloat() / height.toFloat()
        }

        fun scaleForBandwidth(current: VideoConfig, bandwidthBps: Long): VideoConfig {
            return when {
                bandwidthBps < 500_000 -> LOW
                bandwidthBps < 1_500_000 -> MEDIUM
                bandwidthBps < 3_000_000 -> HIGH
                else -> ULTRA
            }
        }
    }
}

/**
 * 视频编解码器
 */
@Serializable
enum class VideoCodec {
    H264,
    H265,
    VP8,
    VP9,
    AV1
}

/**
 * 编码状态
 */
@Serializable
enum class EncoderState {
    IDLE,
    INITIALIZING,
    RUNNING,
    PAUSED,
    ERROR,
    RELEASED
}

/**
 * 编码帧
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
