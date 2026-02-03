package com.bridginghelp.core.network

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.model.SignalingMessage
import kotlinx.coroutines.flow.Flow
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * 信令客户端接口
 * 用于与信令服务器通信，传递WebRTC信令消息
 */
interface SignalingClient {
    /**
     * 连接到信令服务器
     */
    suspend fun connect(url: String): Result<Unit>

    /**
     * 断开连接
     */
    suspend fun disconnect(): Result<Unit>

    /**
     * 发送Offer
     */
    suspend fun sendOffer(sessionId: String, offer: SessionDescription): Result<Unit>

    /**
     * 发送Answer
     */
    suspend fun sendAnswer(sessionId: String, answer: SessionDescription): Result<Unit>

    /**
     * 发送ICE候选
     */
    suspend fun sendIceCandidate(
        sessionId: String,
        candidate: IceCandidate
    ): Result<Unit>

    /**
     * 发送会话结束消息
     */
    suspend fun sendSessionEnd(
        sessionId: String,
        reason: com.bridginghelp.core.model.DisconnectReason
    ): Result<Unit>

    /**
     * 发送任意信令消息
     */
    suspend fun sendSignalingMessage(message: SignalingMessage): Result<Unit>

    /**
     * 接收消息流
     */
    val incomingMessages: Flow<SignalingMessage>
}
