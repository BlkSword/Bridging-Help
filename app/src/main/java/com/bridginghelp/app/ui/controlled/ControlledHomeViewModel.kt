package com.bridginghelp.app.ui.controlled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.signaling.session.SessionManager
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
    val errorMessage: String? = null
)

/**
 * 受控端主页ViewModel
 */
@HiltViewModel
class ControlledHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val accessibilityPermissionHandler: com.bridginghelp.core.permissions.AccessibilityPermissionHandler
) : ViewModel() {

    companion object {
        private const val TAG = "ControlledHomeViewModel"
    }

    private val _uiState = MutableStateFlow(ControlledHomeUiState())
    val uiState: StateFlow<ControlledHomeUiState> = _uiState.asStateFlow()

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

        // 监听无障碍服务状态
        viewModelScope.launch {
            accessibilityPermissionHandler.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isAccessibilityEnabled = state is com.bridginghelp.core.permissions.AccessibilityPermissionState.Enabled
                )
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
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to connect: ${result.error}")
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
     * 结束会话
     */
    fun endSession() {
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
