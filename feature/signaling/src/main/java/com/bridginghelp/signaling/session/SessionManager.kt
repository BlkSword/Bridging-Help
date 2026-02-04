package com.bridginghelp.signaling.session

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.message
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.DeviceInfo
import com.bridginghelp.core.model.DeviceCapability
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.core.model.SignalingMessage
import com.bridginghelp.core.network.SignalingClient
import com.bridginghelp.webrtc.datachannel.DataChannelManager
import com.bridginghelp.webrtc.peer.ManagedPeerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription
import org.webrtc.MediaStreamTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程会话信息
 */
data class RemoteSession(
    val sessionId: String,
    val remoteDevice: DeviceInfo,
    val peerConnection: ManagedPeerConnection,
    val dataChannel: DataChannelManager
)

/**
 * 会话管理器
 * 负责管理远程协助会话的完整生命周期
 */
@Singleton
class SessionManager @Inject constructor(
    private val signalingClient: SignalingClient,
    private val peerConnectionFactory: com.bridginghelp.webrtc.factory.WebRtcPeerConnectionFactory
) {

    companion object {
        private const val TAG = "SessionManager"
        private const val SESSION_TIMEOUT_MS = 30000L
        private const val HEARTBEAT_INTERVAL_MS = 10000L
    }

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var activeSession: RemoteSession? = null
    private var heartbeatJob: Job? = null
    private var isInitialized = false

    init {
        // 不在 init 块中初始化 WebRTC，延迟到首次使用时
        // 监听信令消息
        scope.launch {
            signalingClient.incomingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
    }

    /**
     * 确保已初始化
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            val result = peerConnectionFactory.initialize()
            isInitialized = result is Result.Success
        }
    }

    /**
     * 连接到信令服务器
     */
    suspend fun connectToServer(serverUrl: String): Result<Unit> {
        LogWrapper.d(TAG, "Connecting to signaling server: $serverUrl")

        return signalingClient.connect(serverUrl)
    }

    /**
     * 创建新会话（控制端发起）
     */
    suspend fun createSession(remoteDeviceId: String): Result<String> {
        LogWrapper.d(TAG, "Creating session with device: $remoteDeviceId")

        // 确保 WebRTC 已初始化
        ensureInitialized()

        return try {
            val sessionId = generateSessionId()

            // 创建 PeerConnection 和 DataChannel
            val peerConnection = createAndConfigurePeerConnection()
            val dataChannel = com.bridginghelp.webrtc.datachannel.DataChannelManager()

            // 初始化数据通道
            dataChannel.initialize(peerConnection, "remote_events")

            // 创建 Offer
            val offerResult = peerConnection.createOffer()
            if (offerResult !is Result.Success) {
                return Result.Error(offerResult.errorOrNull()!!)
            }

            // 设置本地描述
            val setDescriptionResult = peerConnection.setLocalDescription(offerResult.data)
            if (setDescriptionResult !is Result.Success) {
                return Result.Error(setDescriptionResult.errorOrNull()!!)
            }

            // 发送 Offer
            signalingClient.sendOffer(sessionId, offerResult.data)

            // 创建临时会话信息
            val deviceInfo = DeviceInfo(
                deviceId = remoteDeviceId,
                deviceName = "Remote Device",
                deviceType = com.bridginghelp.core.model.DeviceType.PHONE,
                osVersion = "",
                appVersion = "",
                capabilities = setOf(
                    DeviceCapability.SCREEN_CAPTURE,
                    DeviceCapability.TOUCH_INPUT,
                    DeviceCapability.MULTI_TOUCH
                )
            )
            activeSession = RemoteSession(sessionId, deviceInfo, peerConnection, dataChannel)

            _sessionState.value = SessionState.Connecting(sessionId, remoteDeviceId)

            LogWrapper.i(TAG, "Session creation initiated: $sessionId")
            Result.Success(sessionId)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to create session", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 加入会话（受控端接受）
     * 等待控制端的 Offer 消息，然后创建 Answer
     */
    suspend fun joinSession(sessionId: String, remoteDevice: DeviceInfo): Result<Unit> {
        LogWrapper.d(TAG, "Joining session: $sessionId")

        // 确保 WebRTC 已初始化
        ensureInitialized()

        return try {
            // 创建 PeerConnection
            val peerConnection = createAndConfigurePeerConnection()
            val dataChannel = com.bridginghelp.webrtc.datachannel.DataChannelManager()

            // 暂存会话信息，等待 Offer 消息
            val pendingSession = PendingSession(sessionId, remoteDevice, peerConnection, dataChannel)
            pendingSessions[sessionId] = pendingSession

            _sessionState.value = SessionState.Connecting(sessionId, remoteDevice.deviceId)

            LogWrapper.i(TAG, "Joining session: $sessionId, waiting for offer")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to join session", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 等待中的会话信息
     */
    private data class PendingSession(
        val sessionId: String,
        val remoteDevice: DeviceInfo,
        val peerConnection: ManagedPeerConnection,
        val dataChannel: com.bridginghelp.webrtc.datachannel.DataChannelManager
    )

    /**
     * 等待中的会话映射
     * sessionId -> PendingSession
     */
    private val pendingSessions = mutableMapOf<String, PendingSession>()

    /**
     * 结束会话
     */
    suspend fun endSession(): Result<Unit> {
        LogWrapper.d(TAG, "Ending session")

        return try {
            activeSession?.let { session ->
                // 发送会话结束消息
                signalingClient.sendSessionEnd(
                    session.sessionId,
                    com.bridginghelp.core.model.DisconnectReason.USER_INITIATED
                )

                // 关闭数据通道
                session.dataChannel.close()

                // 关闭PeerConnection
                session.peerConnection.close()
            }

            // 停止心跳
            heartbeatJob?.cancel()
            heartbeatJob = null

            activeSession = null
            _sessionState.value = SessionState.Idle

            LogWrapper.i(TAG, "Session ended")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to end session", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 处理信令消息
     */
    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        when (message) {
            is SignalingMessage.Offer -> {
                handleOffer(message)
            }
            is SignalingMessage.Answer -> {
                handleAnswer(message)
            }
            is SignalingMessage.IceCandidateMsg -> {
                handleIceCandidate(message)
            }
            is SignalingMessage.SessionEnd -> {
                handleSessionEnd(message)
            }
            is SignalingMessage.Heartbeat -> {
                LogWrapper.d(TAG, "Heartbeat received: ${message.sequence}")
            }
            else -> {
                LogWrapper.d(TAG, "Unhandled message type: ${message::class.simpleName}")
            }
        }
    }

    /**
     * 处理 Offer（受控端）
     */
    private suspend fun handleOffer(message: SignalingMessage.Offer) {
        LogWrapper.d(TAG, "Handling offer for session: ${message.sessionId}")

        // 检查是否有等待中的会话
        val pendingSession = pendingSessions[message.sessionId]
        if (pendingSession == null) {
            LogWrapper.w(TAG, "No pending session found for: ${message.sessionId}")
            return
        }

        val peerConnection = pendingSession.peerConnection
        val sdp = SessionDescription(SessionDescription.Type.OFFER, message.sdp)

        // 设置远程描述
        val remoteDescResult = peerConnection.setRemoteDescription(sdp)
        if (remoteDescResult !is Result.Success) {
            val errorMsg = when (remoteDescResult) {
                is Result.Error -> remoteDescResult.error.message
                else -> "Loading state"
            }
            LogWrapper.e(TAG, "Failed to set remote description: $errorMsg")
            return
        }

        // 创建 Answer
        val answerResult = peerConnection.createAnswer()
        if (answerResult is Result.Success) {
            // 设置本地描述
            val localDescResult = peerConnection.setLocalDescription(answerResult.data)
            if (localDescResult !is Result.Success) {
                val errorMsg = when (localDescResult) {
                    is Result.Error -> localDescResult.error.message
                    else -> "Loading state"
                }
                LogWrapper.e(TAG, "Failed to set local description: $errorMsg")
                return
            }

            // 发送 Answer
            signalingClient.sendAnswer(message.sessionId, answerResult.data)

            // 将等待中的会话转为活跃会话
            activeSession = RemoteSession(
                sessionId = pendingSession.sessionId,
                remoteDevice = pendingSession.remoteDevice,
                peerConnection = peerConnection,
                dataChannel = pendingSession.dataChannel
            )
            pendingSessions.remove(message.sessionId)

            // 更新状态
            _sessionState.value = SessionState.Connected(
                sessionId = message.sessionId,
                remoteDevice = pendingSession.remoteDevice,
                quality = com.bridginghelp.core.model.ConnectionQuality.GOOD
            )

            // 启动心跳
            startHeartbeat(message.sessionId)

            LogWrapper.i(TAG, "Answer sent and session established: ${message.sessionId}")
        }
    }

    /**
     * 处理 Answer（控制端）
     */
    private suspend fun handleAnswer(message: SignalingMessage.Answer) {
        LogWrapper.d(TAG, "Handling answer for session: ${message.sessionId}")

        activeSession?.let { session ->
            val peerConnection = session.peerConnection
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, message.sdp)

            // 设置远程描述
            val result = peerConnection.setRemoteDescription(sdp)
            if (result is Result.Success) {
                // 更新会话状态为已连接
                _sessionState.value = SessionState.Connected(
                    sessionId = message.sessionId,
                    remoteDevice = session.remoteDevice,
                    quality = com.bridginghelp.core.model.ConnectionQuality.GOOD
                )

                // 启动心跳
                startHeartbeat(message.sessionId)

                LogWrapper.i(TAG, "Session established: ${message.sessionId}")
            } else {
                val errorMsg = when (result) {
                    is Result.Error -> result.error.message
                    else -> "Loading state"
                }
                LogWrapper.e(TAG, "Failed to set remote description: $errorMsg")
            }
        } ?: LogWrapper.w(TAG, "No active session found for answer: ${message.sessionId}")
    }

    /**
     * 处理ICE候选
     */
    private fun handleIceCandidate(message: SignalingMessage.IceCandidateMsg) {
        LogWrapper.d(TAG, "Handling ICE candidate for session: ${message.sessionId}")

        activeSession?.peerConnection?.let { peerConnection ->
            val candidate = org.webrtc.IceCandidate(
                message.sdpMid,
                message.sdpMLineIndex,
                message.candidate
            )

            peerConnection.addIceCandidate(candidate)
        }
    }

    /**
     * 处理会话结束
     */
    private fun handleSessionEnd(message: SignalingMessage.SessionEnd) {
        LogWrapper.d(TAG, "Handling session end: ${message.sessionId}")

        scope.launch {
            endSession()
        }
    }

    /**
     * 创建并配置 PeerConnection，包括 ICE 候选收集
     */
    private fun createAndConfigurePeerConnection(): ManagedPeerConnection {
        val peerConnection = ManagedPeerConnection(peerConnectionFactory)

        // 创建观察者来收集 ICE 候选
        val observer = object : org.webrtc.PeerConnection.Observer {
            override fun onSignalingChange(newState: org.webrtc.PeerConnection.SignalingState?) {
                LogWrapper.d(TAG, "Signaling state changed: $newState")
            }

            override fun onIceConnectionChange(newState: org.webrtc.PeerConnection.IceConnectionState?) {
                LogWrapper.d(TAG, "ICE connection state changed: $newState")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                LogWrapper.d(TAG, "ICE connection receiving changed: $receiving")
            }

            override fun onIceGatheringChange(newState: org.webrtc.PeerConnection.IceGatheringState?) {
                LogWrapper.d(TAG, "ICE gathering state changed: $newState")
            }

            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {
                candidate?.let {
                    LogWrapper.d(TAG, "ICE candidate gathered: ${it.sdpMid}")

                    // 发送 ICE 候选到信令服务器
                    scope.launch {
                        activeSession?.let { session ->
                            signalingClient.sendIceCandidate(session.sessionId, it)
                        } ?: run {
                            // 如果没有活跃会话，检查等待中的会话
                            pendingSessions.entries.firstOrNull()?.let { (sessionId, _) ->
                                signalingClient.sendIceCandidate(sessionId, it)
                            }
                        }
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {
                LogWrapper.d(TAG, "ICE candidates removed")
            }

            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                LogWrapper.d(TAG, "Media stream added")
            }

            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {
                LogWrapper.d(TAG, "Media stream removed")
            }

            override fun onDataChannel(channel: org.webrtc.DataChannel?) {
                LogWrapper.d(TAG, "Data channel created by remote peer")

                // 设置远程创建的数据通道
                channel?.let {
                    activeSession?.dataChannel?.setRemoteDataChannel(it)
                        ?: pendingSessions.values.firstOrNull()?.dataChannel?.setRemoteDataChannel(it)
                }
            }

            override fun onRenegotiationNeeded() {
                LogWrapper.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                LogWrapper.d(TAG, "Track added")
            }
        }

        peerConnection.initialize(observer)
        return peerConnection
    }

    /**
     * 启动心跳
     */
    private fun startHeartbeat(sessionId: String) {
        heartbeatJob?.cancel()

        heartbeatJob = scope.launch {
            var sequence = 0
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                signalingClient.sendSignalingMessage(
                    SignalingMessage.Heartbeat(
                        sessionId = sessionId,
                        sequence = sequence++,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * 生成会话ID
     */
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    /**
     * 获取活跃会话的数据通道管理器
     */
    fun getDataChannelManager(): com.bridginghelp.webrtc.datachannel.DataChannelManager? {
        return activeSession?.dataChannel
    }

    /**
     * 获取活跃会话的 PeerConnection
     */
    fun getPeerConnection(): ManagedPeerConnection? {
        return activeSession?.peerConnection
    }

    /**
     * 检查是否有活跃会话
     */
    fun hasActiveSession(): Boolean {
        return activeSession != null
    }

    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? {
        return activeSession?.sessionId
    }

    /**
     * 获取当前远程设备ID
     */
    fun getCurrentRemoteDeviceId(): String? {
        return activeSession?.remoteDevice?.deviceId
    }

    /**
     * 重新连接会话
     */
    suspend fun reconnectSession(): Result<String> {
        val sessionId = activeSession?.sessionId
        val remoteDeviceId = activeSession?.remoteDevice?.deviceId

        if (sessionId == null || remoteDeviceId == null) {
            return Result.Error(
                com.bridginghelp.core.common.result.ErrorEntity.Unknown("No active session to reconnect")
            )
        }

        LogWrapper.d(TAG, "Reconnecting to session: $sessionId")

        // 结束当前会话
        endSession()

        // 重新创建会话
        return createSession(remoteDeviceId)
    }

    /**
     * 获取远程视频轨道
     */
    fun getRemoteVideoTrack(): Any? {
        val peerConnection = activeSession?.peerConnection ?: return null
        val streams = peerConnection.getRemoteStreams()

        for (stream in streams) {
            for (track in stream.videoTracks) {
                if (track.kind() == "video") {
                    return track
                }
            }
        }
        return null
    }

    /**
     * 获取远程音频轨道
     */
    fun getRemoteAudioTrack(): org.webrtc.MediaStreamTrack? {
        val peerConnection = activeSession?.peerConnection ?: return null
        val streams = peerConnection.getRemoteStreams()

        for (stream in streams) {
            for (track in stream.audioTracks) {
                if (track.kind() == "audio") {
                    return track
                }
            }
        }
        return null
    }

    /**
     * 获取所有远程媒体流
     */
    fun getRemoteStreams(): List<org.webrtc.MediaStream> {
        return activeSession?.peerConnection?.getRemoteStreams() ?: emptyList()
    }
}
