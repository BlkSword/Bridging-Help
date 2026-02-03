package com.bridginghelp.capture.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.bridginghelp.capture.model.EncodedFrame
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.VideoConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * 视频编码器接口
 */
interface VideoEncoder {
    /**
     * 初始化编码器
     */
    suspend fun initialize(): Result<Unit>

    /**
     * 开始编码
     */
    suspend fun startEncoding(): Result<Unit>

    /**
     * 停止编码
     */
    suspend fun stopEncoding(): Result<Unit>

    /**
     * 释放编码器
     */
    suspend fun release()

    /**
     * 编码状态流
     */
    val state: Flow<EncoderState>

    /**
     * 编码帧流
     */
    val encodedFrames: Flow<EncodedFrame>

    /**
     * 输入Surface
     */
    val inputSurface: Surface?

    /**
     * 编码器工厂
     */
    interface Factory {
        fun createEncoder(config: VideoConfig): VideoEncoder
    }
}

/**
 * 编码器状态
 */
sealed class EncoderState {
    data object Idle : EncoderState()
    data object Initialized : EncoderState()
    data object Running : EncoderState()
    data object Stopped : EncoderState()
    data class Error(val error: String) : EncoderState()
}

/**
 * 硬件视频编码器实现
 */
class HardwareVideoEncoder(
    private val config: VideoConfig
) : VideoEncoder {

    companion object {
        private const val TAG = "HardwareVideoEncoder"
        private const val MIME_TYPE = "video/avc" // H.264
        private const val COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        private const val I_FRAME_INTERVAL = 1 // 秒
        private const val FRAME_OUTPUT_TIMEOUT_US = 10000L // 微秒
    }

    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var isRunning = false

    private val frameChannel = Channel<EncodedFrame>(capacity = Channel.UNLIMITED)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<EncoderState>(EncoderState.Idle)
    override val state: kotlinx.coroutines.flow.Flow<EncoderState> = _state

    override val encodedFrames: kotlinx.coroutines.flow.Flow<EncodedFrame> = flow {
        for (frame in frameChannel) {
            emit(frame)
        }
    }

    override val inputSurface: Surface?
        get() = surface

    private val bufferInfo = MediaCodec.BufferInfo()

    override suspend fun initialize(): Result<Unit> {
        LogWrapper.d(TAG, "Initializing encoder: ${config.width}x${config.height} @ ${config.bitrate} bps")

        return try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FORMAT)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)

                // 设置编码器配置文件和级别（可选）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(
                        MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                    )
                }

                // 设置复杂度
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_COMPLEXITY, 0) // 低复杂度以降低延迟
                }
            }

            codec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface = createInputSurface()
            }

            _state.value = EncoderState.Initialized
            LogWrapper.i(TAG, "Encoder initialized successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to initialize encoder", e)
            _state.value = EncoderState.Error(e.message ?: "Unknown error")
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    override suspend fun startEncoding(): Result<Unit> {
        LogWrapper.d(TAG, "Starting encoding")

        return try {
            codec?.start()
            isRunning = true
            _state.value = EncoderState.Running

            // 启动编码器输出处理线程
            startOutputProcessing()

            LogWrapper.i(TAG, "Encoding started")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to start encoding", e)
            _state.value = EncoderState.Error(e.message ?: "Unknown error")
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    override suspend fun stopEncoding(): Result<Unit> {
        LogWrapper.d(TAG, "Stopping encoding")

        return try {
            isRunning = false
            codec?.stop()
            _state.value = EncoderState.Stopped

            LogWrapper.i(TAG, "Encoding stopped")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to stop encoding", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    override suspend fun release() {
        LogWrapper.d(TAG, "Releasing encoder")

        isRunning = false
        surface?.release()
        surface = null
        codec?.release()
        codec = null
        frameChannel.close()

        _state.value = EncoderState.Idle
    }

    /**
     * 启动输出处理
     */
    private fun startOutputProcessing() {
        Thread {
            while (isRunning) {
                try {
                    val outputBufferIndex = codec?.dequeueOutputBuffer(
                        bufferInfo,
                        FRAME_OUTPUT_TIMEOUT_US
                    ) ?: -1

                    when {
                        outputBufferIndex >= 0 -> {
                            // 获取编码后的数据
                            val outputBuffer = codec?.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null) {
                                val data = ByteArray(bufferInfo.size)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.get(data)
                                outputBuffer.clear()

                                val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                                val frame = EncodedFrame(
                                    data = data,
                                    timestamp = bufferInfo.presentationTimeUs,
                                    isKeyFrame = isKeyFrame,
                                    rotation = 0
                                )

                                frameChannel.trySend(frame)
                            }

                            codec?.releaseOutputBuffer(outputBufferIndex, false)
                        }
                        outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // 暂时没有可用输出，继续等待
                        }
                        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // 输出格式变化，忽略
                        }
                        else -> {
                            // 错误
                            LogWrapper.e(TAG, "Unexpected output buffer index: $outputBufferIndex")
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogWrapper.e(TAG, "Error processing output", e)
                    break
                }
            }
        }.start()
    }

    /**
     * 硬件视频编码器工厂
     */
    class Factory @Inject constructor() : VideoEncoder.Factory {
        override fun createEncoder(config: VideoConfig): VideoEncoder {
            return HardwareVideoEncoder(config)
        }
    }
}
