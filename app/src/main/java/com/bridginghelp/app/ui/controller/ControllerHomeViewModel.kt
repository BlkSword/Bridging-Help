package com.bridginghelp.app.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.DeviceInfo
import com.bridginghelp.core.model.DiscoveredDevice
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.core.discovery.DeviceDiscoveryManager
import com.bridginghelp.signaling.session.SessionManager
import com.bridginghelp.app.ui.qrcode.parsePairingQRCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 控制端主页UI状态
 */
data class ControllerHomeUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val sessionState: SessionState = SessionState.Idle,
    val availableDevices: List<DeviceInfo> = emptyList(),
    val discoveredDevices: Map<String, DiscoveredDevice> = emptyMap(),
    val localDeviceInfo: com.bridginghelp.core.model.LocalDeviceInfo? = null,
    val localIpAddress: String = "",
    val showScanResultDialog: Boolean = false,
    val scanResult: com.bridginghelp.app.ui.qrcode.PairingInfo? = null,
    val errorMessage: String? = null
)

/**
 * 控制端主页ViewModel
 */
@HiltViewModel
class ControllerHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val deviceDiscoveryManager: DeviceDiscoveryManager,
    private val localDeviceInfo: com.bridginghelp.core.model.LocalDeviceInfo
) : ViewModel() {

    companion object {
        private const val TAG = "ControllerHomeViewModel"
    }

    private val _uiState = MutableStateFlow(
        ControllerHomeUiState(
            localDeviceInfo = localDeviceInfo,
            localIpAddress = deviceDiscoveryManager.getLocalIpAddress()
        )
    )
    val uiState: StateFlow<ControllerHomeUiState> = _uiState.asStateFlow()

    // 用于存储扫描结果
    private var pendingPairingInfo: com.bridginghelp.app.ui.qrcode.PairingInfo? = null

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

        // 监听发现的设备
        viewModelScope.launch {
            deviceDiscoveryManager.discoveredDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(
                    discoveredDevices = devices
                )
            }
        }
    }

    /**
     * 启动设备发现
     */
    fun startDiscovery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)

            when (val result = deviceDiscoveryManager.startDiscovery()) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Device discovery started")
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to start discovery: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        errorMessage = "设备发现启动失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * 停止设备发现
     */
    fun stopDiscovery() {
        viewModelScope.launch {
            deviceDiscoveryManager.stopDiscovery()
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    /**
     * 刷新设备列表
     */
    fun refreshDevices() {
        viewModelScope.launch {
            // 清理过期设备
            deviceDiscoveryManager.cleanupExpiredDevices()
        }
    }

    /**
     * 连接到设备
     */
    fun connectToDevice(deviceId: String, onNavigateToRemote: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = sessionManager.createSession(deviceId)) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Session created: ${result.data}")
                    // 导航到远程控制界面
                    onNavigateToRemote(result.data)
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to create session: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "创建会话失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {}
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 处理二维码扫描结果
     */
    fun handleScanResult(qrContent: String, onNavigateToRemote: (String) -> Unit) {
        val pairingInfo = parsePairingQRCode(qrContent)

        if (pairingInfo != null) {
            _uiState.value = _uiState.value.copy(
                scanResult = pairingInfo,
                showScanResultDialog = true
            )
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "无效的二维码格式"
            )
        }
    }

    /**
     * 确认扫描结果并连接
     */
    fun confirmScanResult(onNavigateToRemote: (String) -> Unit) {
        val pairingInfo = _uiState.value.scanResult

        if (pairingInfo != null) {
            _uiState.value = _uiState.value.copy(
                showScanResultDialog = false
            )

            // 使用扫描到的设备信息连接
            connectToDevice(pairingInfo.deviceId, onNavigateToRemote)
        }
    }

    /**
     * 取消扫描结果
     */
    fun dismissScanResult() {
        _uiState.value = _uiState.value.copy(
            showScanResultDialog = false,
            scanResult = null
        )
    }

    /**
     * 连接到设备（旧版本兼容）
     */
    fun connectToDevice(deviceId: String, deviceName: String) {
        // 空实现，保持兼容性
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
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.error.message
                    )
                }
                is Result.Loading -> {}
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
