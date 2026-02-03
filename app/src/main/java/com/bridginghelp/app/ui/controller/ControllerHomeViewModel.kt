package com.bridginghelp.app.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.DeviceInfo
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.signaling.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 控制端主页UI状态
 */
data class ControllerHomeUiState(
    val isLoading: Boolean = false,
    val sessionState: SessionState = SessionState.Idle,
    val availableDevices: List<DeviceInfo> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 控制端主页ViewModel
 */
@HiltViewModel
class ControllerHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "ControllerHomeViewModel"
    }

    private val _uiState = MutableStateFlow(ControllerHomeUiState())
    val uiState: StateFlow<ControllerHomeUiState> = _uiState.asStateFlow()

    init {
        // 监听会话状态
        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    sessionState = state,
                    isLoading = false
                )
            }
        }
    }

    /**
     * 连接到信令服务器
     */
    fun connectToServer(serverUrl: String = "ws://localhost:8080/signaling") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = sessionManager.connectToServer(serverUrl)) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Connected to signaling server")
                    // TODO: 获取可用设备列表
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to connect to signaling server: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "连接失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {
                    // Already loading, do nothing
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 连接到设备
     */
    fun connectToDevice(deviceId: String, deviceName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = sessionManager.createSession(deviceId)) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Session created: ${result.data}")
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to create session: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "创建会话失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {
                    // Already loading, do nothing
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = sessionManager.endSession()) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Session ended")
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to end session: ${result.error.message}")
                }
                is Result.Loading -> {
                    // Already loading, do nothing
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
