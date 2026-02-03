package com.bridginghelp.app.ui.remoteassist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.app.navigation.AppRole
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
 * 远程协助UI状态
 */
data class RemoteAssistUiState(
    val currentRole: AppRole? = null,
    val sessionState: SessionState = SessionState.Idle,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 远程协助ViewModel
 */
@HiltViewModel
class RemoteAssistViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "RemoteAssistViewModel"
    }

    private val _uiState = MutableStateFlow(RemoteAssistUiState())
    val uiState: StateFlow<RemoteAssistUiState> = _uiState.asStateFlow()

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
     * 设置为控制端
     */
    fun setAsController() {
        LogWrapper.d(TAG, "Set as controller")
        _uiState.value = _uiState.value.copy(currentRole = AppRole.CONTROLLER)
    }

    /**
     * 设置为受控端
     */
    fun setAsControlled() {
        LogWrapper.d(TAG, "Set as controlled")
        _uiState.value = _uiState.value.copy(currentRole = AppRole.CONTROLLED)
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
                    _uiState.value = _uiState.value.copy(
                        currentRole = null,
                        sessionState = SessionState.Idle
                    )
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to end session: ${result.error.message}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "结束会话失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {
                    // Already loading
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

    /**
     * 刷新状态
     */
    fun refreshStatus() {
        LogWrapper.d(TAG, "Refreshing status")
        // 触发状态刷新
    }
}
