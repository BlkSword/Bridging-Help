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
    val videoTrack: Any? = null, // WebRTC VideoTrack 的包装类型
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
            // 获取视频轨道
            val videoTrack = sessionManager.getRemoteVideoTrack()
            _uiState.value = _uiState.value.copy(videoTrack = videoTrack)
            LogWrapper.d(TAG, "Video track: ${videoTrack?.toString()}")

            // 获取会话的数据通道管理器
            val dataChannel = sessionManager.getDataChannelManager()
            if (dataChannel != null) {
                this@RemoteControlViewModel.dataChannelManager = dataChannel
                LogWrapper.i(TAG, "Data channel initialized for session: $sessionId")

                // 监听远程事件
                dataChannel.getRemoteEvents().collect { event ->
                    handleRemoteEvent(event)
                }
            } else {
                LogWrapper.w(TAG, "No data channel available for session: $sessionId")
            }
        }
    }

    /**
     * 获取远程视频轨道
     */
    fun getRemoteVideoTrack(): Any? {
        return sessionManager.getRemoteVideoTrack()
    }

    /**
     * 更新分辨率信息
     */
    fun updateResolution(width: Int, height: Int) {
        _uiState.value = _uiState.value.copy(
            resolution = "${width}x${height}"
        )
        LogWrapper.d(TAG, "Resolution updated: ${width}x${height}")
    }

    /**
     * 更新帧率信息
     */
    fun updateFps(fps: Int) {
        _uiState.value = _uiState.value.copy(fps = fps)
    }

    /**
     * 更新延迟信息
     */
    fun updateLatency(latency: Int) {
        _uiState.value = _uiState.value.copy(latency = latency)
    }

    /**
     * 处理接收到的远程事件
     */
    private fun handleRemoteEvent(event: RemoteEvent) {
        LogWrapper.d(TAG, "Received remote event: ${event::class.simpleName}")
        // 根据需要处理远程事件
        // 例如：剪贴板同步、文件传输等
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
        val dataChannel = dataChannelManager ?: sessionManager.getDataChannelManager()

        if (dataChannel == null) {
            LogWrapper.w(TAG, "Data channel not available, cannot send event: $event")
            _uiState.value = _uiState.value.copy(
                errorMessage = "数据通道未连接，无法发送事件"
            )
            return
        }

        when (val result = dataChannel.sendRemoteEvent(event)) {
            is Result.Success -> {
                LogWrapper.d(TAG, "Remote event sent successfully: ${event::class.simpleName}")
            }
            is Result.Error -> {
                LogWrapper.e(TAG, "Failed to send remote event: ${result.error.message}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "发送事件失败: ${result.error.message}"
                )
            }
            is Result.Loading -> {}
        }
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

            // 获取之前的会话信息
            val previousSessionId = currentSessionId
            val previousRemoteDeviceId = sessionManager.getCurrentRemoteDeviceId()

            if (previousSessionId == null || previousRemoteDeviceId == null) {
                LogWrapper.w(TAG, "No previous session to reconnect")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "没有可重连的会话"
                )
                return@launch
            }

            LogWrapper.d(TAG, "Reconnecting to session: $previousSessionId")

            // 显示连接状态
            _uiState.value = _uiState.value.copy(
                sessionState = SessionState.Connecting(
                    previousSessionId,
                    previousRemoteDeviceId
                )
            )

            // 重新连接会话
            when (val result = sessionManager.reconnectSession()) {
                is Result.Success -> {
                    LogWrapper.i(TAG, "Reconnected successfully: ${result.data}")
                    _uiState.value = _uiState.value.copy(errorMessage = null)

                    // 重新初始化数据通道
                    val dataChannel = sessionManager.getDataChannelManager()
                    if (dataChannel != null) {
                        this@RemoteControlViewModel.dataChannelManager = dataChannel

                        // 监听远程事件
                        dataChannel.getRemoteEvents().collect { event ->
                            handleRemoteEvent(event)
                        }
                    }
                }
                is Result.Error -> {
                    LogWrapper.e(TAG, "Failed to reconnect: ${result.error.message}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "重新连接失败: ${result.error.message}"
                    )
                }
                is Result.Loading -> {}
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
        dataChannelManager = null
    }

    /**
     * 设置数据通道
     */
    fun setDataChannel(manager: DataChannelManager) {
        this.dataChannelManager = manager
        LogWrapper.d(TAG, "Data channel set")
    }
}
