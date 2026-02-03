package com.bridginghelp.app.ui.controller

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.ConnectionQuality
import com.bridginghelp.core.model.RemoteEvent
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.webrtc.datachannel.DataChannelManager
import com.bridginghelp.webrtc.peer.ManagedPeerConnection
import com.bridginghelp.signaling.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 远程控制UI状态
 */
data class RemoteControlUiState(
    val sessionState: SessionState = SessionState.Idle,
    val videoFrame: ImageBitmap? = null,
    val connectionQuality: ConnectionQuality = ConnectionQuality.GOOD,
    val latency: Int = 0,
    val fps: Int = 0,
    val resolution: String = "Unknown",
    val showQualityInfo: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 远程控制ViewModel
 */
@HiltViewModel
class RemoteControlViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "RemoteControlViewModel"
    }

    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null
    // Note: VideoTrack API not available in current WebRTC version, using placeholder
    private var remoteVideoTrack: Any? = null
    private var dataChannelManager: DataChannelManager? = null

    init {
        // 监听会话状态
        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    sessionState = state,
                    connectionQuality = when (state) {
                        is SessionState.Connected -> state.quality
                        else -> ConnectionQuality.GOOD
                    }
                )
            }
        }
    }

    /**
     * 初始化会话
     */
    fun initializeSession(sessionId: String) {
        LogWrapper.d(TAG, "Initialize session: $sessionId")
        currentSessionId = sessionId

        viewModelScope.launch {
            // 如果需要，可以在这里添加额外的初始化逻辑
        }
    }

    /**
     * 发送触摸事件
     */
    fun sendTouchEvent(x: Float, y: Float, action: String) {
        viewModelScope.launch {
            val touchAction = when (action) {
                "tap", "down" -> com.bridginghelp.core.model.TouchAction.DOWN
                "up" -> com.bridginghelp.core.model.TouchAction.UP
                "move" -> com.bridginghelp.core.model.TouchAction.MOVE
                "double_tap" -> com.bridginghelp.core.model.TouchAction.DOWN
                else -> com.bridginghelp.core.model.TouchAction.DOWN
            }

            val event = RemoteEvent.TouchEvent(
                x = x,
                y = y,
                action = touchAction
            )

            sendRemoteEvent(event)
        }
    }

    /**
     * 发送返回键
     */
    fun sendBackKey() {
        viewModelScope.launch {
            val event = RemoteEvent.KeyEvent(
                keyCode = android.view.KeyEvent.KEYCODE_BACK,
                action = com.bridginghelp.core.model.KeyAction.DOWN
            )
            sendRemoteEvent(event)

            kotlinx.coroutines.delay(50)
            sendRemoteEvent(event.copy(action = com.bridginghelp.core.model.KeyAction.UP))
        }
    }

    /**
     * 发送主页键
     */
    fun sendHomeKey() {
        viewModelScope.launch {
            val event = RemoteEvent.KeyEvent(
                keyCode = android.view.KeyEvent.KEYCODE_HOME,
                action = com.bridginghelp.core.model.KeyAction.DOWN
            )
            sendRemoteEvent(event)

            kotlinx.coroutines.delay(50)
            sendRemoteEvent(event.copy(action = com.bridginghelp.core.model.KeyAction.UP))
        }
    }

    /**
     * 发送最近键
     */
    fun sendRecentKey() {
        viewModelScope.launch {
            val event = RemoteEvent.KeyEvent(
                keyCode = android.view.KeyEvent.KEYCODE_APP_SWITCH,
                action = com.bridginghelp.core.model.KeyAction.DOWN
            )
            sendRemoteEvent(event)

            kotlinx.coroutines.delay(50)
            sendRemoteEvent(event.copy(action = com.bridginghelp.core.model.KeyAction.UP))
        }
    }

    /**
     * 发送远程事件
     */
    private suspend fun sendRemoteEvent(event: RemoteEvent) {
        // 通过数据通道发送事件
        // TODO: 实现实际的数据通道发送
        LogWrapper.d(TAG, "Sending remote event: $event")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            when (val result = sessionManager.endSession()) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Disconnected successfully")
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to disconnect: ${result.error.message}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "断开连接失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * 重新连接
     */
    fun reconnect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)

            currentSessionId?.let { sessionId ->
                // TODO: 实现重新连接逻辑
                LogWrapper.d(TAG, "Reconnecting to session: $sessionId")
            }
        }
    }

    /**
     * 切换质量信息显示
     */
    fun toggleQualityInfo() {
        _uiState.value = _uiState.value.copy(
            showQualityInfo = !_uiState.value.showQualityInfo
        )
    }

    /**
     * 暂停
     */
    fun onPause() {
        LogWrapper.d(TAG, "onPause")
        // 暂停视频渲染
        // WebRTC VideoTrack doesn't have setEnabled in the standard API
        // We just track the state
    }

    /**
     * 恢复
     */
    fun onResume() {
        LogWrapper.d(TAG, "onResume")
        // 恢复视频渲染
        // WebRTC VideoTrack doesn't have setEnabled in the standard API
        // We just track the state
    }

    /**
     * 销毁
     */
    fun onDestroy() {
        LogWrapper.d(TAG, "onDestroy")
        // 清理资源
        remoteVideoTrack = null
        dataChannelManager = null
    }

    /**
     * 设置远程视频流
     */
    fun setRemoteVideoStream(stream: Any?) {
        // Note: MediaStream API not available in current WebRTC version
        // This is a placeholder implementation
        LogWrapper.d(TAG, "Remote video stream set (placeholder)")

        _uiState.value = _uiState.value.copy(
            resolution = "Unknown"
        )
    }

    /**
     * 设置数据通道
     */
    fun setDataChannel(manager: DataChannelManager) {
        this.dataChannelManager = manager
        LogWrapper.d(TAG, "Data channel set")
    }
}
