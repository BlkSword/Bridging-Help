package com.bridginghelp.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.LocalDeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心UI状态
 */
data class ProfileUiState(
    val deviceName: String = "",
    val deviceId: String = "",
    val appVersion: String = "",
    val showAboutDialog: Boolean = false
)

/**
 * 个人中心ViewModel
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val localDeviceInfo: LocalDeviceInfo
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            deviceName = localDeviceInfo.deviceName,
            deviceId = localDeviceInfo.deviceId,
            appVersion = localDeviceInfo.appVersion
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * 显示关于对话框
     */
    fun showAbout() {
        LogWrapper.d(TAG, "Show about dialog")
        _uiState.value = _uiState.value.copy(showAboutDialog = true)
    }

    /**
     * 隐藏对话框
     */
    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = false)
    }

    /**
     * 显示帮助
     */
    fun showHelp() {
        LogWrapper.d(TAG, "Navigate to help")
        // TODO: 实现帮助页面
    }

    /**
     * 显示反馈
     */
    fun showFeedback() {
        LogWrapper.d(TAG, "Navigate to feedback")
        // TODO: 实现反馈页面
    }
}
