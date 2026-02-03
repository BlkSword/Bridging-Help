package com.bridginghelp.core.audio

import android.Manifest
import android.content.Context
import android.media.*
import androidx.annotation.RequiresPermission
import com.bridginghelp.core.model.*
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.ErrorEntity
import com.bridginghelp.core.common.util.LogWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频捕获状态
 */
sealed class AudioCaptureState {
    data object Idle : AudioCaptureState()
    data object Initializing : AudioCaptureState()
    data class Capturing(
        val source: AudioSource,
        val config: AudioConfig
    ) : AudioCaptureState()
    data class Error(
        val error: AudioCaptureError,
        val message: String
    ) : AudioCaptureState()
}

/**
 * 音频捕获错误
 */
sealed class AudioCaptureError {
    data object PermissionDenied : AudioCaptureError()
    data object InitializationFailed : AudioCaptureError()
    data object EncodingFailed : AudioCaptureError()
    data object Unknown : AudioCaptureError()
}

/**
 * 音频播放状态
 */
sealed class AudioPlaybackState {
    data object Idle : AudioPlaybackState()
    data class Playing(val config: AudioConfig) : AudioPlaybackState()
    data class Paused(val config: AudioConfig) : AudioPlaybackState()
    data class Error(
        val error: AudioPlaybackError,
        val message: String
    ) : AudioPlaybackState()
}

/**
 * 音频播放错误
 */
sealed class AudioPlaybackError {
    data object InitializationFailed : AudioPlaybackError()
    data object DecodingFailed : AudioPlaybackError()
    data object Unknown : AudioPlaybackError()
}

/**
 * 音频捕获管理器
 * 负责捕获麦克风和系统音频
 */
@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val BUFFER_SIZE_MS = 20 // 20ms buffer
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _captureState = MutableStateFlow<AudioCaptureState>(AudioCaptureState.Idle)
    val captureState: StateFlow<AudioCaptureState> = _captureState.asStateFlow()

    private val _playbackState = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _audioFrames = MutableSharedFlow<AudioFrame>()
    val audioFrames: SharedFlow<AudioFrame> = _audioFrames.asSharedFlow()

    private var audioRecorder: AudioRecord? = null
    private var playbackTrack: AudioTrack? = null
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    private var currentConfig: AudioConfig? = null
    private var bufferSize = 0

    /**
     * 开始音频捕获
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startCapture(
        source: AudioSource,
        config: AudioConfig = AudioConfig.DEFAULT
    ): Result<Unit> {
        LogWrapper.d(TAG, "Starting audio capture: source=$source, codec=${config.codec}")

        if (_captureState.value is AudioCaptureState.Capturing) {
            return Result.Error(ErrorEntity.Validation("Audio capture already in progress"))
        }

        _captureState.value = AudioCaptureState.Initializing

        return try {
            currentConfig = config
            bufferSize = AudioRecord.getMinBufferSize(
                config.sampleRate,
                getChannelConfig(config.channels),
                getAudioFormat(config.codec)
            )

            if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw IllegalArgumentException("Invalid audio configuration")
            }

            val audioSource = when (source) {
                AudioSource.MICROPHONE -> MediaRecorder.AudioSource.MIC
                AudioSource.SYSTEM_AUDIO -> MediaRecorder.AudioSource.REMOTE_SUBMIX
                AudioSource.BOTH -> MediaRecorder.AudioSource.DEFAULT
            }

            audioRecorder = AudioRecord(
                audioSource,
                config.sampleRate,
                getChannelConfig(config.channels),
                getAudioFormat(config.codec),
                bufferSize
            )

            if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Failed to initialize AudioRecord")
            }

            audioRecorder?.startRecording()

            _captureState.value = AudioCaptureState.Capturing(source, config)

            // 开始捕获音频帧
            startCaptureLoop(config)

            LogWrapper.i(TAG, "Audio capture started")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to start audio capture", e)
            _captureState.value = AudioCaptureState.Error(
                AudioCaptureError.InitializationFailed,
                e.message ?: "Unknown error"
            )
            cleanup()
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 停止音频捕获
     */
    suspend fun stopCapture(): Result<Unit> {
        LogWrapper.d(TAG, "Stopping audio capture")

        captureJob?.cancel()
        captureJob = null

        audioRecorder?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecorder = null

        _captureState.value = AudioCaptureState.Idle

        return Result.Success(Unit)
    }

    /**
     * 开始音频播放
     */
    suspend fun startPlayback(config: AudioConfig = AudioConfig.DEFAULT): Result<Unit> {
        LogWrapper.d(TAG, "Starting audio playback: codec=${config.codec}")

        if (_playbackState.value is AudioPlaybackState.Playing) {
            return Result.Error(ErrorEntity.Validation("Audio playback already in progress"))
        }

        return try {
            bufferSize = AudioTrack.getMinBufferSize(
                config.sampleRate,
                getChannelConfig(config.channels),
                getAudioFormat(config.codec)
            )

            if (bufferSize == AudioTrack.ERROR_BAD_VALUE) {
                throw IllegalArgumentException("Invalid audio configuration")
            }

            playbackTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                config.sampleRate,
                getChannelConfig(config.channels),
                getAudioFormat(config.codec),
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            if (playbackTrack?.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("Failed to initialize AudioTrack")
            }

            playbackTrack?.play()

            _playbackState.value = AudioPlaybackState.Playing(config)

            // 开始播放循环
            startPlaybackLoop(config)

            LogWrapper.i(TAG, "Audio playback started")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to start audio playback", e)
            _playbackState.value = AudioPlaybackState.Error(
                AudioPlaybackError.InitializationFailed,
                e.message ?: "Unknown error"
            )
            cleanup()
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 停止音频播放
     */
    suspend fun stopPlayback(): Result<Unit> {
        LogWrapper.d(TAG, "Stopping audio playback")

        playbackJob?.cancel()
        playbackJob = null

        playbackTrack?.apply {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            release()
        }
        playbackTrack = null

        _playbackState.value = AudioPlaybackState.Idle

        return Result.Success(Unit)
    }

    /**
     * 写入音频帧用于播放
     */
    suspend fun writeAudioFrame(frame: AudioFrame): Result<Unit> {
        return try {
            playbackTrack?.write(frame.data, 0, frame.data.size)
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to write audio frame", e)
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 开始捕获循环
     */
    private fun startCaptureLoop(config: AudioConfig) {
        captureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            val frameBuffer = ByteBuffer.allocate(bufferSize * 2)

            while (isActive && audioRecorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecorder?.read(buffer, 0, bufferSize) ?: 0

                if (bytesRead > 0) {
                    val frame = AudioFrame(
                        codec = config.codec,
                        sampleRate = config.sampleRate,
                        channels = config.channels,
                        data = buffer.copyOf(bytesRead)
                    )

                    _audioFrames.emit(frame)
                }

                delay(BUFFER_SIZE_MS.toLong())
            }
        }
    }

    /**
     * 开始播放循环
     */
    private fun startPlaybackLoop(config: AudioConfig) {
        playbackJob = scope.launch {
            // 从audioFrames流中读取并播放
            audioFrames.collect { frame ->
                if (playbackTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    playbackTrack?.write(frame.data, 0, frame.data.size)
                }
            }
        }
    }

    /**
     * 获取音频格式
     */
    private fun getAudioFormat(codec: AudioCodec): Int {
        return when (codec) {
            AudioCodec.PCM -> AudioFormat.ENCODING_PCM_16BIT
            AudioCodec.AAC -> AudioFormat.ENCODING_AAC_LC
            AudioCodec.OPUS -> AudioFormat.ENCODING_PCM_16BIT // OPUS需要使用MediaCodec
        }
    }

    /**
     * 获取声道配置
     */
    private fun getChannelConfig(channels: Int): Int {
        return when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
    }

    /**
     * 清理资源
     */
    private suspend fun cleanup() {
        audioRecorder?.release()
        audioRecorder = null

        playbackTrack?.release()
        playbackTrack = null

        captureJob?.cancel()
        playbackJob?.cancel()
    }

    /**
     * 释放资源
     */
    suspend fun release() {
        stopCapture()
        stopPlayback()
        cleanup()
    }
}

/**
 * 音频编码器
 * 负责将原始音频编码为目标格式
 */
class AudioEncoder @Inject constructor() {

    /**
     * 编码音频帧
     */
    fun encode(frame: AudioFrame, targetCodec: AudioCodec): AudioFrame {
        // 如果已经是目标编码，直接返回
        if (frame.codec == targetCodec) {
            return frame
        }

        // TODO: 实现音频转码逻辑
        // 这里需要使用MediaCodec进行实际的编码转换

        return frame.copy(codec = targetCodec)
    }

    /**
     * 解码音频帧
     */
    fun decode(frame: AudioFrame, targetCodec: AudioCodec): AudioFrame {
        // 如果已经是目标编码，直接返回
        if (frame.codec == targetCodec) {
            return frame
        }

        // TODO: 实现音频解码逻辑

        return frame.copy(codec = targetCodec)
    }
}
