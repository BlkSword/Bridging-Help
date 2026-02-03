package com.bridginghelp.signaling.model

import com.bridginghelp.core.model.SignalingMessage as CoreSignalingMessage
import com.bridginghelp.core.model.DisconnectReason
import kotlinx.serialization.Serializable
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * 信令消息包装器
 * 用于网络传输的消息格式
 */
@Serializable
data class SignalingMessageWrapper(
    val type: MessageType,
    val sessionId: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 消息类型
 */
@Serializable
enum class MessageType {
    CONNECTION_REQUEST,
    CONNECTION_RESPONSE,
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    SESSION_END,
    HEARTBEAT,
    QUALITY_ADJUSTMENT
}

/**
 * JSON 序列化器
 */
private val json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 信令消息扩展
 */
fun CoreSignalingMessage.toWrapper(): SignalingMessageWrapper {
    val (type, payload) = when (this) {
        is CoreSignalingMessage.ConnectionRequest -> {
            MessageType.CONNECTION_REQUEST to json.encodeToString(
                ConnectionRequestPayload.serializer(),
                ConnectionRequestPayload(
                    requesterId = requesterId,
                    requesterName = requesterName,
                    deviceInfo = deviceInfo
                )
            )
        }
        is CoreSignalingMessage.ConnectionResponse -> {
            MessageType.CONNECTION_RESPONSE to json.encodeToString(
                ConnectionResponsePayload.serializer(),
                ConnectionResponsePayload(
                    accepted = accepted,
                    reason = reason
                )
            )
        }
        is CoreSignalingMessage.Offer -> {
            MessageType.OFFER to sdp
        }
        is CoreSignalingMessage.Answer -> {
            MessageType.ANSWER to sdp
        }
        is CoreSignalingMessage.IceCandidateMsg -> {
            val icePayload = IceCandidatePayload(
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex,
                candidate = candidate
            )
            MessageType.ICE_CANDIDATE to json.encodeToString(
                IceCandidatePayload.serializer(),
                icePayload
            )
        }
        is CoreSignalingMessage.SessionEnd -> {
            MessageType.SESSION_END to json.encodeToString(
                SessionEndPayload.serializer(),
                SessionEndPayload(reason = reason)
            )
        }
        is CoreSignalingMessage.Heartbeat -> {
            MessageType.HEARTBEAT to sequence.toString()
        }
        is CoreSignalingMessage.QualityAdjustment -> {
            MessageType.QUALITY_ADJUSTMENT to json.encodeToString(
                QualityAdjustmentPayload.serializer(),
                QualityAdjustmentPayload(config = targetConfig)
            )
        }
    }

    return SignalingMessageWrapper(
        type = type,
        sessionId = sessionId,
        payload = payload,
        timestamp = timestamp
    )
}

/**
 * 从包装器解析为核心消息
 */
fun SignalingMessageWrapper.toCore(): CoreSignalingMessage? {
    return when (type) {
        MessageType.OFFER -> CoreSignalingMessage.Offer(sessionId, payload)
        MessageType.ANSWER -> CoreSignalingMessage.Answer(sessionId, payload)
        MessageType.SESSION_END -> {
            val endPayload = try {
                json.decodeFromString<SessionEndPayload>(payload)
            } catch (e: Exception) {
                return null
            }
            CoreSignalingMessage.SessionEnd(sessionId, endPayload.reason)
        }
        MessageType.HEARTBEAT -> {
            val sequence = payload.toIntOrNull() ?: 0
            CoreSignalingMessage.Heartbeat(sessionId, sequence)
        }
        else -> null
    }
}

/**
 * 连接请求负载
 */
@Serializable
data class ConnectionRequestPayload(
    val requesterId: String,
    val requesterName: String,
    val deviceInfo: com.bridginghelp.core.model.DeviceInfo
)

/**
 * 连接响应负载
 */
@Serializable
data class ConnectionResponsePayload(
    val accepted: Boolean,
    val reason: String? = null
)

/**
 * ICE候选负载
 */
@Serializable
data class IceCandidatePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
)

/**
 * 会话结束负载
 */
@Serializable
data class SessionEndPayload(
    val reason: DisconnectReason
)

/**
 * 质量调整负载
 */
@Serializable
data class QualityAdjustmentPayload(
    val config: com.bridginghelp.core.model.VideoConfig
)
