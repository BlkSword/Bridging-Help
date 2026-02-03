package com.bridginghelp.signaling.session

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.DeviceInfo
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

    init {
        // 初始化WebRTC工厂
        peerConnectionFactory.initialize()

        // 监听信令消息
        scope.launch {
            signalingClient.incomingMessages.collect { message ->
                handleSignalingMessage(message)
            }
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

        return try {
            val sessionId = generateSessionId()

            // 创建PeerConnection
            val peerConnection = createAndConfigurePeerConnection()

            // 创建Offer
            val offerResult = peerConnection.createOffer()
            if (offerResult !is Result.Success) {
                return Result.Error(offerResult.errorOrNull()!!)
            }

            // 设置本地描述
            val setDescriptionResult = peerConnection.setLocalDescription(offerResult.data)
            if (setDescriptionResult !is Result.Success) {
                return Result.Error(setDescriptionResult.errorOrNull()!!)
            }

            // 发送Offer
            signalingClient.sendOffer(sessionId, offerResult.data)

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
     */
    suspend fun joinSession(sessionId: String): Result<Unit> {
        LogWrapper.d(TAG, "Joining session: $sessionId")

        return try {
            // 创建PeerConnection
            val peerConnection = createAndConfigurePeerConnection()

            // TODO: 等待Offer消息并创建Answer
            // 这里需要实现完整的信令流程

            _sessionState.value = SessionState.Connecting(sessionId, null)

            LogWrapper.i(TAG, "Joining session: $sessionId")
            Result.Success(Unit)

        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to join session", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

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
     * 处理Offer
     */
    private suspend fun handleOffer(message: SignalingMessage.Offer) {
        LogWrapper.d(TAG, "Handling offer for session: ${message.sessionId}")

        activeSession?.peerConnection?.let { peerConnection ->
            val sdp = SessionDescription(
                SessionDescription.Type.OFFER,
                message.sdp
            )

            // 设置远程描述
            peerConnection.setRemoteDescription(sdp)

            // 创建Answer
            val answerResult = peerConnection.createAnswer()
            if (answerResult is Result.Success) {
                peerConnection.setLocalDescription(answerResult.data)
                signalingClient.sendAnswer(message.sessionId, answerResult.data)
            }
        }
    }

    /**
     * 处理Answer
     */
    private suspend fun handleAnswer(message: SignalingMessage.Answer) {
        LogWrapper.d(TAG, "Handling answer for session: ${message.sessionId}")

        activeSession?.peerConnection?.let { peerConnection ->
            val sdp = SessionDescription(
                SessionDescription.Type.ANSWER,
                message.sdp
            )

            peerConnection.setRemoteDescription(sdp)
        }
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
     * 创建并配置PeerConnection
     */
    private fun createAndConfigurePeerConnection(): ManagedPeerConnection {
        // 这里应该注入ManagedPeerConnection
        // 为简化示例，返回一个空实现
        return ManagedPeerConnection(peerConnectionFactory)
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
}
