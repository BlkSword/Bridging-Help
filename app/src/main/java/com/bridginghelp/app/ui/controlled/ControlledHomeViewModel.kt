package com.bridginghelp.app.ui.controlled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.core.model.LocalDeviceInfo
import com.bridginghelp.capture.manager.ScreenCaptureManager
import com.bridginghelp.signaling.session.SessionManager
import com.bridginghelp.core.discovery.DeviceDiscoveryManager
import com.bridginghelp.core.clipboard.ClipboardSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 受控端主页UI状态
 */
data class ControlledHomeUiState(
    val isLoading: Boolean = false,
    val sessionState: SessionState = SessionState.Idle,
    val isAccessibilityEnabled: Boolean = false,
    val isScreenSharing: Boolean = false,
    val isClipboardSyncEnabled: Boolean = false,
    val localDeviceInfo: LocalDeviceInfo? = null,
    val localIpAddress: String = "",
    val showPairingDialog: Boolean = false,
    val errorMessage: String? = null,
    val connectedDeviceCount: Int = 0
)

/**
 * 受控端主页ViewModel
 */
@HiltViewModel
class ControlledHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val accessibilityPermissionHandler: com.bridginghelp.core.permissions.AccessibilityPermissionHandler,
    private val screenCaptureManager: ScreenCaptureManager,
    private val deviceDiscoveryManager: DeviceDiscoveryManager,
    private val clipboardSyncManager: com.bridginghelp.core.clipboard.ClipboardSyncManager,
    private val localDeviceInfo: LocalDeviceInfo
) : ViewModel() {

    companion object {
        private const val TAG = "ControlledHomeViewModel"
    }

    private val _uiState = MutableStateFlow(
        ControlledHomeUiState(
            localDeviceInfo = localDeviceInfo,
            localIpAddress = deviceDiscoveryManager.getLocalIpAddress()
        )
    )
    val uiState: StateFlow<ControlledHomeUiState> = _uiState.asStateFlow()

    init {
        // 监听会话状态
        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    sessionState = state,
                    isLoading = false,
                    isScreenSharing = state is SessionState.Connected
                )
            }
        }

        // 监听无障碍服务状态
        viewModelScope.launch {
            accessibilityPermissionHandler.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isAccessibilityEnabled = state is com.bridginghelp.core.permissions.AccessibilityPermissionState.Enabled
                )
            }
        }

        // 监听剪贴板同步状态
        viewModelScope.launch {
            _uiState.collect { state ->
                if (state.isClipboardSyncEnabled && state.sessionState is SessionState.Connected) {
                    clipboardSyncManager.enable()
                } else {
                    clipboardSyncManager.disable()
                }
            }
        }
    }

    /**
     * 启动会话（等待连接）
     */
    fun startSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 连接到信令服务器
            when (val result = sessionManager.connectToServer("ws://localhost:8080/signaling")) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Connected to signaling server, waiting for session")
                    // 启动设备发现广播
                    deviceDiscoveryManager.startDiscovery()
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to connect: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "连接失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {}
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 结束会话
     */
    fun endSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 停止屏幕捕获
            if (_uiState.value.isScreenSharing) {
                screenCaptureManager.stopCapture()
            }

            // 停止剪贴板同步
            clipboardSyncManager.disable()

            // 停止设备发现
            deviceDiscoveryManager.stopDiscovery()

            when (val result = sessionManager.endSession()) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Session ended")
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to end session: ${result.error.message}")
                }
                is Result.Loading -> {}
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isScreenSharing = false,
                isClipboardSyncEnabled = false
            )
        }
    }

    /**
     * 启动屏幕共享
     */
    fun startScreenSharing(resultData: android.content.Intent) {
        viewModelScope.launch {
            val config = com.bridginghelp.core.model.VideoConfig.MEDIUM
            val width = 1080
            val height = 1920
            val density = 320

            when (val result = screenCaptureManager.startCapture(
                resultData = resultData,
                width = width,
                height = height,
                density = density,
                config = config
            )) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Screen sharing started")
                    _uiState.value = _uiState.value.copy(isScreenSharing = true)
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to start screen sharing: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "屏幕共享启动失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * 停止屏幕共享
     */
    fun stopScreenSharing() {
        viewModelScope.launch {
            when (screenCaptureManager.stopCapture()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isScreenSharing = false)
                }
                else -> {}
            }
        }
    }

    /**
     * 切换剪贴板同步
     */
    fun toggleClipboardSync() {
        val newState = !_uiState.value.isClipboardSyncEnabled
        _uiState.value = _uiState.value.copy(isClipboardSyncEnabled = newState)

        if (newState) {
            clipboardSyncManager.enable()
        } else {
            clipboardSyncManager.disable()
        }
    }

    /**
     * 显示配对对话框
     */
    fun showPairingDialog() {
        _uiState.value = _uiState.value.copy(showPairingDialog = true)
    }

    /**
     * 隐藏配对对话框
     */
    fun hidePairingDialog() {
        _uiState.value = _uiState.value.copy(showPairingDialog = false)
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * 刷新连接状态
     */
    fun refreshStatus() {
        viewModelScope.launch {
            // 更新连接的设备数量
            deviceDiscoveryManager.cleanupExpiredDevices()
            val discoveredDevices = deviceDiscoveryManager.discoveredDevices.value
            _uiState.value = _uiState.value.copy(
                connectedDeviceCount = discoveredDevices.size
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        clipboardSyncManager.release()
    }
}
