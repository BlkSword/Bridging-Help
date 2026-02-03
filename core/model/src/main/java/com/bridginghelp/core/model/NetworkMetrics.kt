package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 网络指标
 * 用于监控和自适应调整
 */
@Serializable
data class NetworkMetrics(
    val rtt: Int,              // 往返时间 (ms)
    val packetLoss: Float,     // 丢包率 (0.0 - 1.0)
    val bandwidth: Long,       // 带宽 (bps)
    val jitter: Int,           // 抖动 (ms)
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT = NetworkMetrics(
            rtt = 100,
            packetLoss = 0.01f,
            bandwidth = 2_000_000L,
            jitter = 10
        )

        fun calculateQuality(metrics: NetworkMetrics): ConnectionQuality {
            return when {
                metrics.rtt < 50 && metrics.packetLoss < 0.01f -> ConnectionQuality.EXCELLENT
                metrics.rtt < 150 && metrics.packetLoss < 0.05f -> ConnectionQuality.GOOD
                metrics.rtt < 300 && metrics.packetLoss < 0.10f -> ConnectionQuality.FAIR
                else -> ConnectionQuality.POOR
            }
        }
    }
}

/**
 * 连接状态
 */
@Serializable
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

/**
 * WebRTC对等连接状态
 */
@Serializable
enum class PeerConnectionState {
    NEW,
    CHECKING,
    CONNECTED,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    CLOSED
}

/**
 * ICE连接状态
 */
@Serializable
enum class IceConnectionState {
    NEW,
    CHECKING,
    CONNECTED,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    CLOSED,
    COUNT
}

/**
 * ICE收集状态
 */
@Serializable
enum class IceGatheringState {
    NEW,
    GATHERING,
    COMPLETE
}
