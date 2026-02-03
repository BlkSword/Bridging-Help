package com.bridginghelp.capture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.bridginghelp.capture.model.CaptureState
import com.bridginghelp.capture.model.CaptureError
import com.bridginghelp.capture.manager.ScreenCaptureManager
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 屏幕捕获前台服务
 * 负责维护MediaProjection和VirtualDisplay的生命周期
 */
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject
    lateinit var captureManager: ScreenCaptureManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    companion object {
        const val TAG = "ScreenCaptureService"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_CAPTURE = "com.bridginghelp.action.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.bridginghelp.action.STOP_CAPTURE"
        const val ACTION_PAUSE_CAPTURE = "com.bridginghelp.action.PAUSE_CAPTURE"
        const val ACTION_RESUME_CAPTURE = "com.bridginghelp.action.RESUME_CAPTURE"

        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DENSITY = "density"
        const val EXTRA_CONFIG = "config"
    }

    override fun onCreate() {
        super.onCreate()
        LogWrapper.d(TAG, "ScreenCaptureService created")

        // 创建通知通道
        createNotificationChannel()

        // 监听捕获管理器状态
        serviceScope.launch {
            captureManager.captureState.collect { state ->
                _captureState.value = when (state) {
                    is com.bridginghelp.capture.manager.CaptureManagerState.Idle -> CaptureState.Idle
                    is com.bridginghelp.capture.manager.CaptureManagerState.Initializing -> CaptureState.Initializing
                    is com.bridginghelp.capture.manager.CaptureManagerState.Capturing -> CaptureState.Capturing(
                        width = state.width,
                        height = state.height,
                        frameRate = state.frameRate,
                        config = state.config
                    )
                    is com.bridginghelp.capture.manager.CaptureManagerState.Error -> CaptureState.Error(
                        error = when (state.error) {
                            is com.bridginghelp.capture.manager.CaptureManagerError.PermissionDenied -> CaptureError.PERMISSION_DENIED
                            is com.bridginghelp.capture.manager.CaptureManagerError.EncoderError -> CaptureError.ENCODER_ERROR
                            is com.bridginghelp.capture.manager.CaptureManagerError.SurfaceError -> CaptureError.SURFACE_ERROR
                            else -> CaptureError.UNKNOWN
                        },
                        message = state.message
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val width = intent.getIntExtra(EXTRA_WIDTH, 720)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1280)
                val density = intent.getIntExtra(EXTRA_DENSITY, 320)

                if (resultData != null) {
                    startCapture(resultData, width, height, density)
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopCapture()
            }
            ACTION_PAUSE_CAPTURE -> {
                pauseCapture()
            }
            ACTION_RESUME_CAPTURE -> {
                resumeCapture()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        LogWrapper.d(TAG, "ScreenCaptureService destroyed")

        serviceScope.launch {
            captureManager.release()
        }
        serviceScope.cancel()
    }

    /**
     * 开始屏幕捕获
     */
    private fun startCapture(resultData: Intent, width: Int, height: Int, density: Int) {
        LogWrapper.d(TAG, "Starting capture: ${width}x${height}")

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            val result = captureManager.startCapture(resultData, width, height, density)
            if (!result.isSuccess) {
                LogWrapper.e(TAG, "Failed to start capture: ${result.errorOrNull()?.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * 停止屏幕捕获
     */
    private fun stopCapture() {
        LogWrapper.d(TAG, "Stopping capture")

        serviceScope.launch {
            captureManager.stopCapture()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 暂停捕获
     */
    private fun pauseCapture() {
        LogWrapper.d(TAG, "Pausing capture")
        serviceScope.launch {
            captureManager.pauseCapture()
        }
    }

    /**
     * 恢复捕获
     */
    private fun resumeCapture() {
        LogWrapper.d(TAG, "Resuming capture")
        serviceScope.launch {
            captureManager.resumeCapture()
        }
    }

    /**
     * 获取输入Surface
     */
    fun getInputSurface(): Surface? {
        return captureManager.inputSurface
    }

    /**
     * 本地Binder
     */
    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    /**
     * 创建通知通道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_capture_channel",
                "屏幕捕获",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕捕获进行中"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "screen_capture_channel")
            .setContentTitle("屏幕共享中")
            .setContentText("正在共享您的屏幕")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .build()
    }
}
