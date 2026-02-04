package com.bridginghelp.app.ui.splash

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.permissions.AppPermission
import com.bridginghelp.core.permissions.PermissionManager
import com.bridginghelp.core.permissions.MediaProjectionPermissionHandler
import com.bridginghelp.core.permissions.AccessibilityPermissionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开屏页面UI状态
 */
data class SplashUiState(
    val mediaProjectionGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val allPermissionsGranted: Boolean = false
)

/**
 * 开屏页面ViewModel
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager,
    private val mediaProjectionHandler: MediaProjectionPermissionHandler,
    private val accessibilityHandler: AccessibilityPermissionHandler
) : ViewModel() {

    companion object {
        private const val TAG = "SplashViewModel"
    }

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
    }

    /**
     * 检查所有权限状态
     */
    fun checkPermissions() {
        viewModelScope.launch {
            // 检查屏幕录制权限
            val mediaProjectionGranted = mediaProjectionHandler.hasPermission()

            // 检查无障碍服务权限
            val accessibilityGranted = accessibilityHandler.isServiceEnabled()

            // 检查通知权限
            val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionManager.checkPermission(AppPermission.POST_NOTIFICATIONS)
            } else {
                true
            }

            val allGranted = mediaProjectionGranted && accessibilityGranted && notificationGranted

            _uiState.value = SplashUiState(
                mediaProjectionGranted = mediaProjectionGranted,
                accessibilityGranted = accessibilityGranted,
                notificationGranted = notificationGranted,
                allPermissionsGranted = allGranted
            )

            LogWrapper.d(TAG, "Permissions check - MediaProjection: $mediaProjectionGranted, Accessibility: $accessibilityGranted, Notification: $notificationGranted")
        }
    }

    /**
     * 请求屏幕录制权限
     */
    fun requestMediaProjectionPermission() {
        LogWrapper.d(TAG, "Requesting MediaProjection permission")
        // 这个需要在Activity中调用，这里只是标记
        // 实际的请求会在SplashScreen的launcher中处理
    }

    /**
     * 请求通知权限
     */
    fun requestNotificationPermission() {
        LogWrapper.d(TAG, "Requesting notification permission")
        // 标记需要请求通知权限
        // 实际请求在Activity中处理
    }

    /**
     * 刷新权限状态
     */
    fun refreshPermissions() {
        checkPermissions()
    }
}
