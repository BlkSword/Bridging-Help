package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 音频编解码器
 */
@Serializable
enum class AudioCodec {
    OPUS,
    AAC,
    PCM
}

/**
 * 音频配置
 */
@Serializable
data class AudioConfig(
    val codec: AudioCodec = AudioCodec.OPUS,
    val sampleRate: Int = 48000,
    val channels: Int = 1,
    val bitrate: Int = 64000,
    val enabled: Boolean = true
) {
    companion object {
        val DEFAULT = AudioConfig()
    }
}

/**
 * 音频帧
 */
@Serializable
data class AudioFrame(
    val codec: AudioCodec,
    val sampleRate: Int,
    val channels: Int,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFrame

        if (codec != other.codec) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = codec.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * 音频源类型
 */
@Serializable
enum class AudioSource {
    MICROPHONE,     // 麦克风
    SYSTEM_AUDIO,   // 系统音频
    BOTH            // 混合
}
