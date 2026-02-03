package com.bridginghelp.capture.model

import com.bridginghelp.core.model.VideoConfig

/**
 * 屏幕捕获状态
 */
sealed class CaptureState {
    /**
     * 空闲状态
     */
    data object Idle : CaptureState()

    /**
     * 正在初始化
     */
    data object Initializing : CaptureState()

    /**
     * 正在捕获
     */
    data class Capturing(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val config: VideoConfig
    ) : CaptureState()

    /**
     * 已暂停
     */
    data class Paused(
        val reason: PauseReason
    ) : CaptureState()

    /**
     * 错误状态
     */
    data class Error(
        val error: CaptureError,
        val message: String
    ) : CaptureState()

    /**
     * 已释放
     */
    data object Released : CaptureState()
}

/**
 * 暂停原因
 */
enum class PauseReason {
    USER_REQUEST,
    NETWORK_LOST,
    BATTERY_LOW,
    ERROR
}

/**
 * 捕获错误类型
 */
enum class CaptureError {
    PERMISSION_DENIED,
    SERVICE_DISCONNECTED,
    ENCODER_ERROR,
    SURFACE_ERROR,
    CONFIGURATION_ERROR,
    UNKNOWN
}
