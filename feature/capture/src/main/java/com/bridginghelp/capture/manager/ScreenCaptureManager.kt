package com.bridginghelp.capture.manager

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import com.bridginghelp.capture.encoder.VideoEncoder
import com.bridginghelp.capture.model.CaptureState as ModelCaptureState
import com.bridginghelp.capture.model.CaptureError as ModelCaptureError
import com.bridginghelp.capture.model.PauseReason as ModelPauseReason
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.ErrorEntity
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.VideoConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 捕获管理器状态
 */
sealed class CaptureManagerState {
    data object Idle : CaptureManagerState()
    data object Initializing : CaptureManagerState()
    data class Capturing(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val config: VideoConfig
    ) : CaptureManagerState()
    data class Error(
        val error: CaptureManagerError,
        val message: String
    ) : CaptureManagerState()
}

/**
 * 捕获管理器错误
 */
sealed class CaptureManagerError {
    data object PermissionDenied : CaptureManagerError()
    data object EncoderError : CaptureManagerError()
    data object SurfaceError : CaptureManagerError()
    data object Unknown : CaptureManagerError()
}

/**
 * 屏幕捕获管理器
 * 负责管理MediaProjection、VirtualDisplay和视频编码器
 */
@Singleton
class ScreenCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoEncoderFactory: VideoEncoder.Factory
) {
    companion object {
        private const val TAG = "ScreenCaptureManager"
        private const val VIRTUAL_DISPLAY_NAME = "RemoteHelp Display"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _captureState = MutableStateFlow<CaptureManagerState>(CaptureManagerState.Idle)
    val captureState: StateFlow<CaptureManagerState> = _captureState.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: VideoEncoder? = null
    private var _inputSurface: Surface? = null

    private var currentWidth = 0
    private var currentHeight = 0
    private var currentDensity = 0
    private var currentConfig: VideoConfig? = null

    /**
     * 开始捕获
     */
    suspend fun startCapture(
        resultData: Intent,
        width: Int,
        height: Int,
        density: Int,
        config: VideoConfig = VideoConfig.MEDIUM
    ): Result<Unit> {
        LogWrapper.d(TAG, "Starting capture: ${width}x${height} @ ${config.frameRate}fps")

        if (_captureState.value is CaptureManagerState.Capturing) {
            return Result.Error(ErrorEntity.Validation("Capture already in progress"))
        }

        _captureState.value = CaptureManagerState.Initializing
        currentWidth = width
        currentHeight = height
        currentDensity = density
        currentConfig = config

        return try {
            // 获取MediaProjection
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(android.app.Activity.RESULT_OK, resultData)

            if (mediaProjection == null) {
                _captureState.value = CaptureManagerState.Error(
                    CaptureManagerError.PermissionDenied,
                    "Failed to get MediaProjection"
                )
                return Result.Error(ErrorEntity.Permission("MediaProjection"))
            }

            // 创建视频编码器
            videoEncoder = videoEncoderFactory.createEncoder(config).also { encoder ->
                encoder.initialize()
            }

            _inputSurface = videoEncoder?.inputSurface

            if (_inputSurface == null) {
                _captureState.value = CaptureManagerState.Error(
                    CaptureManagerError.EncoderError,
                    "Failed to get encoder surface"
                )
                return Result.Error(ErrorEntity.Unknown("Failed to get encoder surface"))
            }

            // 创建VirtualDisplay
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                         DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                         DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width,
                height,
                density,
                flags,
                _inputSurface,
                null,
                null
            )

            // 启动编码器
            videoEncoder?.startEncoding()

            _captureState.value = CaptureManagerState.Capturing(
                width = width,
                height = height,
                frameRate = config.frameRate,
                config = config
            )

            LogWrapper.i(TAG, "Capture started successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to start capture", e)
            _captureState.value = CaptureManagerState.Error(
                CaptureManagerError.Unknown,
                e.message ?: "Unknown error"
            )
            cleanup()
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 停止捕获
     */
    suspend fun stopCapture(): Result<Unit> {
        LogWrapper.d(TAG, "Stopping capture")

        cleanup()

        _captureState.value = CaptureManagerState.Idle

        return Result.Success(Unit)
    }

    /**
     * 暂停捕获
     */
    suspend fun pauseCapture(): Result<Unit> {
        LogWrapper.d(TAG, "Pausing capture")

        videoEncoder?.stopEncoding()

        return Result.Success(Unit)
    }

    /**
     * 恢复捕获
     */
    suspend fun resumeCapture(): Result<Unit> {
        LogWrapper.d(TAG, "Resuming capture")

        videoEncoder?.startEncoding()

        return Result.Success(Unit)
    }

    /**
     * 更新视频配置
     */
    suspend fun updateConfig(config: VideoConfig): Result<Unit> {
        LogWrapper.d(TAG, "Updating config: ${config.width}x${config.height}")

        val currentState = _captureState.value
        if (currentState !is CaptureManagerState.Capturing) {
            return Result.Error(ErrorEntity.Validation("Not currently capturing"))
        }

        // 停止当前捕获
        videoEncoder?.stopEncoding()
        virtualDisplay?.release()

        // 重新创建编码器
        videoEncoder = videoEncoderFactory.createEncoder(config).also { encoder ->
            encoder.initialize()
        }

        _inputSurface = videoEncoder?.inputSurface

        // 重新创建VirtualDisplay
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                     DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                     DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            config.width,
            config.height,
            currentDensity,
            flags,
            _inputSurface,
            null,
            null
        )

        videoEncoder?.startEncoding()

        _captureState.value = CaptureManagerState.Capturing(
            width = config.width,
            height = config.height,
            frameRate = config.frameRate,
            config = config
        )

        return Result.Success(Unit)
    }

    /**
     * 获取编码帧流
     */
    fun getEncodedFrames(): kotlinx.coroutines.flow.Flow<com.bridginghelp.capture.model.EncodedFrame> {
        return videoEncoder?.encodedFrames ?: kotlinx.coroutines.flow.emptyFlow()
    }

    /**
     * 获取输入Surface
     */
    val inputSurface: Surface?
        get() = videoEncoder?.inputSurface

    /**
     * 释放资源
     */
    suspend fun release() {
        LogWrapper.d(TAG, "Releasing resources")
        cleanup()
        _captureState.value = CaptureManagerState.Idle
    }

    /**
     * 清理资源
     */
    private suspend fun cleanup() {
        videoEncoder?.stopEncoding()
        videoEncoder?.release()
        videoEncoder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        _inputSurface = null
    }
}
