package com.bridginghelp.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.permissions.AccessibilityPermissionHandler
import com.bridginghelp.core.permissions.MediaProjectionPermissionHandler
import com.bridginghelp.core.permissions.PermissionManager
import com.bridginghelp.core.permissions.AppPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面UI状态
 */
data class SettingsUiState(
    val mediaProjectionGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationGranted: Boolean = false,
    val appVersion: String = "1.0.0"
)

/**
 * 设置页面ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mediaProjectionHandler: MediaProjectionPermissionHandler,
    private val accessibilityHandler: AccessibilityPermissionHandler,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionStatus()
        observeAccessibilityState()
    }

    /**
     * 刷新权限状态
     */
    fun refreshPermissionStatus() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                mediaProjectionGranted = mediaProjectionHandler.hasPermission(),
                accessibilityEnabled = accessibilityHandler.isServiceEnabled(),
                notificationGranted = checkNotificationPermission(),
                appVersion = _uiState.value.appVersion
            )
        }
    }

    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionManager.checkPermission(AppPermission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    /**
     * 监听无障碍服务状态变化
     */
    private fun observeAccessibilityState() {
        viewModelScope.launch {
            accessibilityHandler.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    accessibilityEnabled = state is com.bridginghelp.core.permissions.AccessibilityPermissionState.Enabled
                )
            }
        }
    }
}
