package com.bridginghelp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Polymorphic
import org.webrtc.SessionDescription
import org.webrtc.IceCandidate

/**
 * 信令消息
 * 用于WebRTC信令服务器通信
 */
@Serializable
sealed class SignalingMessage {
    abstract val sessionId: String
    abstract val timestamp: Long

    /**
     * 连接请求 - 控制端发起连接
     */
    @Serializable
    @SerialName("connection_request")
    data class ConnectionRequest(
        override val sessionId: String,
        val requesterId: String,
        val requesterName: String,
        val deviceInfo: DeviceInfo,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * 连接响应 - 受控端接受/拒绝
     */
    @Serializable
    @SerialName("connection_response")
    data class ConnectionResponse(
        override val sessionId: String,
        val accepted: Boolean,
        val reason: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * SDP Offer
     */
    @Serializable
    @SerialName("offer")
    data class Offer(
        override val sessionId: String,
        val sdp: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * SDP Answer
     */
    @Serializable
    @SerialName("answer")
    data class Answer(
        override val sessionId: String,
        val sdp: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * ICE Candidate
     */
    @Serializable
    @SerialName("ice_candidate")
    data class IceCandidateMsg(
        override val sessionId: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * 会话结束
     */
    @Serializable
    @SerialName("session_end")
    data class SessionEnd(
        override val sessionId: String,
        val reason: DisconnectReason,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * 心跳消息
     */
    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        override val sessionId: String,
        val sequence: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()

    /**
     * 质量调整请求
     */
    @Serializable
    @SerialName("quality_adjustment")
    data class QualityAdjustment(
        override val sessionId: String,
        val targetConfig: VideoConfig,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SignalingMessage()
}

/**
 * 信令连接状态
 */
enum class SignalingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
