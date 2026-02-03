package com.bridginghelp.signaling.client

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.SignalingMessage
import com.bridginghelp.core.network.SignalingClient as CoreSignalingClient
import com.bridginghelp.signaling.model.SignalingMessageWrapper
import com.bridginghelp.signaling.model.toCore
import com.bridginghelp.signaling.model.toWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 信令连接状态
 */
sealed class SignalingConnectionState {
    data object Disconnected : SignalingConnectionState()
    data object Connecting : SignalingConnectionState()
    data object Connected : SignalingConnectionState()
    data object Reconnecting : SignalingConnectionState()
    data class Error(val error: String) : SignalingConnectionState()
}

/**
 * WebSocket信令客户端
 */
@Singleton
class WebSocketSignalingClient @Inject constructor() : CoreSignalingClient {

    companion object {
        private const val TAG = "WebSocketSignalingClient"
        private const val RECONNECT_DELAY_MS = 3000L
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow<SignalingConnectionState>(SignalingConnectionState.Disconnected)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableStateFlow<SignalingMessage?>(null)
    override val incomingMessages: Flow<SignalingMessage> = _incomingMessages.map { it }.filterNotNull()

    private var serverUrl: String? = null

    /**
     * 连接到信令服务器
     */
    override suspend fun connect(url: String): Result<Unit> {
        LogWrapper.d(TAG, "Connecting to signaling server: $url")

        serverUrl = url
        _connectionState.value = SignalingConnectionState.Connecting

        return try {
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())

            LogWrapper.i(TAG, "WebSocket connection initiated")
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to connect to signaling server", e)
            _connectionState.value = SignalingConnectionState.Error(e.message ?: "Unknown error")
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 断开连接
     */
    override suspend fun disconnect(): Result<Unit> {
        LogWrapper.d(TAG, "Disconnecting from signaling server")

        return try {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
            _connectionState.value = SignalingConnectionState.Disconnected

            LogWrapper.i(TAG, "Disconnected from signaling server")
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to disconnect", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 发送Offer
     */
    override suspend fun sendOffer(sessionId: String, offer: SessionDescription): Result<Unit> {
        val message = com.bridginghelp.core.model.SignalingMessage.Offer(
            sessionId = sessionId,
            sdp = offer.description
        )
        return sendMessage(message)
    }

    /**
     * 发送Answer
     */
    override suspend fun sendAnswer(sessionId: String, answer: SessionDescription): Result<Unit> {
        val message = com.bridginghelp.core.model.SignalingMessage.Answer(
            sessionId = sessionId,
            sdp = answer.description
        )
        return sendMessage(message)
    }

    /**
     * 发送ICE候选
     */
    override suspend fun sendIceCandidate(
        sessionId: String,
        candidate: IceCandidate
    ): Result<Unit> {
        val message = com.bridginghelp.core.model.SignalingMessage.IceCandidateMsg(
            sessionId = sessionId,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            candidate = candidate.serverUrl
        )
        return sendMessage(message)
    }

    /**
     * 发送会话结束消息
     */
    override suspend fun sendSessionEnd(
        sessionId: String,
        reason: com.bridginghelp.core.model.DisconnectReason
    ): Result<Unit> {
        val message = com.bridginghelp.core.model.SignalingMessage.SessionEnd(
            sessionId = sessionId,
            reason = reason
        )
        return sendMessage(message)
    }

    /**
     * 发送任意信令消息
     */
    override suspend fun sendSignalingMessage(message: com.bridginghelp.core.model.SignalingMessage): Result<Unit> {
        return sendMessage(message)
    }

    /**
     * 发送消息
     */
    private suspend fun sendMessage(message: SignalingMessage): Result<Unit> {
        if (_connectionState.value !is SignalingConnectionState.Connected) {
            LogWrapper.w(TAG, "Cannot send message, not connected")
            return Result.Error(
                com.bridginghelp.core.common.result.ErrorEntity.Unknown("Not connected")
            )
        }

        return try {
            val wrapper = message.toWrapper()
            val jsonStr = json.encodeToString(SignalingMessageWrapper.serializer(), wrapper)

            val sent = webSocket?.send(jsonStr) ?: false

            if (sent) {
                LogWrapper.d(TAG, "Message sent: ${wrapper.type}")
                Result.Success(Unit)
            } else {
                LogWrapper.e(TAG, "Failed to send message")
                Result.Error(
                    com.bridginghelp.core.common.result.ErrorEntity.Unknown("Failed to send")
                )
            }
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error sending message", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 创建WebSocket监听器
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                LogWrapper.i(TAG, "WebSocket connection opened")
                _connectionState.value = SignalingConnectionState.Connected
            }

            override fun onMessage(ws: WebSocket, text: String) {
                LogWrapper.d(TAG, "Message received: $text")

                try {
                    val wrapper = json.decodeFromString(SignalingMessageWrapper.serializer(), text)
                    val coreMessage = wrapper.toCore()

                    coreMessage?.let {
                        _incomingMessages.value = it
                    }
                } catch (e: Exception) {
                    LogWrapper.e(TAG, "Failed to parse message", e)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                LogWrapper.d(TAG, "WebSocket closing: $code - $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                LogWrapper.i(TAG, "WebSocket closed: $code - $reason")
                _connectionState.value = SignalingConnectionState.Disconnected
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                LogWrapper.e(TAG, "WebSocket error", t)
                _connectionState.value = SignalingConnectionState.Error(t.message ?: "Unknown error")
            }
        }
    }

    /**
     * 重新连接
     */
    suspend fun reconnect(): Result<Unit> {
        val url = serverUrl ?: return Result.Error(
            com.bridginghelp.core.common.result.ErrorEntity.Unknown("No server URL")
        )

        _connectionState.value = SignalingConnectionState.Reconnecting

        disconnect()
        kotlinx.coroutines.delay(RECONNECT_DELAY_MS)

        return connect(url)
    }
}
