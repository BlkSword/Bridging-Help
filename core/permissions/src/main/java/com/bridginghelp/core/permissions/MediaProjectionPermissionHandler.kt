package com.bridginghelp.core.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaProjection权限状态
 */
sealed class MediaProjectionPermissionState {
    data object NotRequested : MediaProjectionPermissionState()
    data object Denied : MediaProjectionPermissionState()
    data class Granted(val resultCode: Int, val data: Intent) : MediaProjectionPermissionState()
}

/**
 * MediaProjection权限处理器
 * 处理屏幕捕获权限的特殊请求流程
 */
interface MediaProjectionPermissionHandler {
    val state: kotlinx.coroutines.flow.StateFlow<MediaProjectionPermissionState>

    /**
     * 请求屏幕捕获权限
     */
    fun requestPermission(launcher: ActivityResultLauncher<Intent>)

    /**
     * 重置状态
     */
    fun reset()

    /**
     * 检查是否有权限
     */
    fun hasPermission(): Boolean
}

/**
 * MediaProjection权限处理器实现
 */
@Singleton
class MediaProjectionPermissionHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaProjectionPermissionHandler {

    private val _state = MutableStateFlow<MediaProjectionPermissionState>(
        MediaProjectionPermissionState.NotRequested
    )
    override val state = _state.asStateFlow()

    private val projectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun requestPermission(launcher: ActivityResultLauncher<Intent>) {
        val intent = projectionManager.createScreenCaptureIntent()
        launcher.launch(intent)
    }

    override fun reset() {
        _state.value = MediaProjectionPermissionState.NotRequested
    }

    override fun hasPermission(): Boolean {
        return _state.value is MediaProjectionPermissionState.Granted
    }

    /**
     * 处理权限请求结果
     */
    fun handleResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            _state.value = MediaProjectionPermissionState.Granted(resultCode, data)
        } else {
            _state.value = MediaProjectionPermissionState.Denied
        }
    }
}

/**
 * MediaProjection权限契约
 * 用于Activity Result API
 */
class MediaProjectionPermissionContract : ActivityResultContract<Intent, ActivityResult>() {
    override fun createIntent(context: Context, input: Intent): Intent {
        return input
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult {
        return ActivityResult(resultCode, intent)
    }
}
