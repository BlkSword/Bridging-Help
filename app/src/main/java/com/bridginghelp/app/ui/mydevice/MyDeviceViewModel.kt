package com.bridginghelp.app.ui.mydevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.LocalDeviceInfo
import com.bridginghelp.core.permissions.AccessibilityPermissionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 我的设备UI状态
 */
data class MyDeviceUiState(
    val deviceName: String = "",
    val deviceId: String = "",
    val deviceType: com.bridginghelp.core.model.DeviceType = com.bridginghelp.core.model.DeviceType.PHONE,
    val osVersion: String = "",
    val appVersion: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val screenDensity: Int = 0,
    val capabilities: Set<com.bridginghelp.core.model.DeviceCapability> = emptySet(),
    val isAccessibilityEnabled: Boolean = false
)

/**
 * 我的设备ViewModel
 */
@HiltViewModel
class MyDeviceViewModel @Inject constructor(
    private val localDeviceInfo: LocalDeviceInfo,
    private val accessibilityPermissionHandler: AccessibilityPermissionHandler
) : ViewModel() {

    companion object {
        private const val TAG = "MyDeviceViewModel"
    }

    private val _uiState = MutableStateFlow(
        MyDeviceUiState(
            deviceName = localDeviceInfo.deviceName,
            deviceId = localDeviceInfo.deviceId,
            deviceType = localDeviceInfo.deviceType,
            osVersion = localDeviceInfo.osVersion,
            appVersion = localDeviceInfo.appVersion,
            screenWidth = localDeviceInfo.screenWidth,
            screenHeight = localDeviceInfo.screenHeight,
            screenDensity = localDeviceInfo.screenDensity,
            capabilities = localDeviceInfo.capabilities,
            isAccessibilityEnabled = accessibilityPermissionHandler.isServiceEnabled()
        )
    )
    val uiState: StateFlow<MyDeviceUiState> = _uiState.asStateFlow()

    init {
        // 监听无障碍服务状态变化
        viewModelScope.launch {
            accessibilityPermissionHandler.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isAccessibilityEnabled = state is com.bridginghelp.core.permissions.AccessibilityPermissionState.Enabled
                )
            }
        }
    }

    /**
     * 刷新设备状态
     */
    fun refreshStatus() {
        LogWrapper.d(TAG, "Refreshing device status")
        accessibilityPermissionHandler.refreshState()
    }
}
