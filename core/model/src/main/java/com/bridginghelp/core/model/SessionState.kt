package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 会话状态
 * 表示远程协助会话的当前状态
 */
@Serializable
sealed class SessionState {
    /**
     * 空闲状态 - 未建立连接
     */
    @Serializable
    data object Idle : SessionState()

    /**
     * 正在初始化
     */
    @Serializable
    data object Initializing : SessionState()

    /**
     * 正在连接
     */
    @Serializable
    data class Connecting(
        val sessionId: String,
        val remoteDeviceId: String? = null
    ) : SessionState()

    /**
     * 等待连接确认
     */
    @Serializable
    data class WaitingForApproval(
        val sessionId: String,
        val requesterDeviceId: String,
        val requesterDeviceName: String
    ) : SessionState()

    /**
     * 已连接 - 活跃的远程协助会话
     */
    @Serializable
    data class Connected(
        val sessionId: String,
        val remoteDevice: DeviceInfo,
        val connectedAt: Long = System.currentTimeMillis(),
        val quality: ConnectionQuality = ConnectionQuality.GOOD
    ) : SessionState()

    /**
     * 连接暂停中（网络问题等）
     */
    @Serializable
    data class Paused(
        val sessionId: String,
        val reason: PauseReason
    ) : SessionState()

    /**
     * 断开连接
     */
    @Serializable
    data class Disconnected(
        val sessionId: String? = null,
        val reason: DisconnectReason,
        val errorMessage: String? = null
    ) : SessionState()

    /**
     * 错误状态
     */
    @Serializable
    data class Error(
        val error: SessionError,
        val message: String,
        val recoverable: Boolean = false
    ) : SessionState()
}

/**
 * 连接质量
 */
@Serializable
enum class ConnectionQuality {
    EXCELLENT,  // 延迟 < 50ms, 丢包率 < 1%
    GOOD,       // 延迟 < 150ms, 丢包率 < 5%
    FAIR,       // 延迟 < 300ms, 丢包率 < 10%
    POOR        // 延迟 >= 300ms 或 丢包率 >= 10%
}

/**
 * 暂停原因
 */
@Serializable
enum class PauseReason {
    NETWORK_LOST,
    REMOTE_PAUSE,
    BATTERY_LOW,
    USER_REQUEST
}

/**
 * 断开原因
 */
@Serializable
enum class DisconnectReason {
    USER_INITIATED,
    REMOTE_DISCONNECTED,
    NETWORK_ERROR,
    TIMEOUT,
    PERMISSION_DENIED,
    SESSION_EXPIRED,
    APP_BACKGROUND,
    ERROR
}

/**
 * 会话错误类型
 */
@Serializable
enum class SessionError {
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    WEBRTC_INIT_FAILED,
    CAPTURE_SERVICE_FAILED,
    INJECTION_SERVICE_FAILED,
    SIGNALING_ERROR,
    AUTHENTICATION_FAILED,
    SESSION_LIMIT_EXCEEDED,
    UNKNOWN
}
