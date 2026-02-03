package com.bridginghelp.capture.quality

import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.ConnectionQuality
import com.bridginghelp.core.model.NetworkMetrics
import com.bridginghelp.core.model.VideoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 质量监控器
 * 监控网络和编码质量，自动调整视频参数
 */
@Singleton
class QualityMonitor @Inject constructor() {

    companion object {
        private const val TAG = "QualityMonitor"
        private const val METRICS_WINDOW_SIZE = 10
        private const val ADJUSTMENT_THRESHOLD = 3 // 连续N次低于阈值才调整
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentQuality = MutableStateFlow(ConnectionQuality.GOOD)
    val currentQuality: StateFlow<ConnectionQuality> = _currentQuality.asStateFlow()

    private val _recommendedConfig = MutableStateFlow(VideoConfig.MEDIUM)
    val recommendedConfig: StateFlow<VideoConfig> = _recommendedConfig.asStateFlow()

    private val metricsHistory = mutableListOf<NetworkMetrics>()
    private var adjustmentCounter = 0

    /**
     * 更新网络指标
     */
    fun updateMetrics(metrics: NetworkMetrics) {
        scope.launch {
            metricsHistory.add(metrics)
            if (metricsHistory.size > METRICS_WINDOW_SIZE) {
                metricsHistory.removeAt(0)
            }

            analyzeMetrics()
        }
    }

    /**
     * 分析指标并更新质量等级
     */
    private fun analyzeMetrics() {
        if (metricsHistory.isEmpty()) return

        val avgRtt = metricsHistory.map { it.rtt }.average().toInt()
        val avgPacketLoss = metricsHistory.map { it.packetLoss }.average()
        val avgBandwidth = metricsHistory.map { it.bandwidth }.average().toLong()
        val avgJitter = metricsHistory.map { it.jitter }.average().toInt()

        val quality = NetworkMetrics.calculateQuality(
            NetworkMetrics(
                rtt = avgRtt,
                packetLoss = avgPacketLoss.toFloat(),
                bandwidth = avgBandwidth,
                jitter = avgJitter,
                timestamp = System.currentTimeMillis()
            )
        )

        _currentQuality.value = quality

        // 检查是否需要调整配置
        when (quality) {
            ConnectionQuality.POOR -> {
                adjustmentCounter++
                if (adjustmentCounter >= ADJUSTMENT_THRESHOLD) {
                    _recommendedConfig.value = VideoConfig.LOW
                    LogWrapper.w(TAG, "Quality degraded to POOR, reducing to LOW config")
                    adjustmentCounter = 0
                }
            }
            ConnectionQuality.FAIR -> {
                if (_recommendedConfig.value == VideoConfig.ULTRA) {
                    _recommendedConfig.value = VideoConfig.HIGH
                } else if (_recommendedConfig.value == VideoConfig.HIGH) {
                    _recommendedConfig.value = VideoConfig.MEDIUM
                }
                adjustmentCounter = 0
            }
            ConnectionQuality.GOOD -> {
                if (_recommendedConfig.value == VideoConfig.LOW) {
                    _recommendedConfig.value = VideoConfig.MEDIUM
                }
                adjustmentCounter = 0
            }
            ConnectionQuality.EXCELLENT -> {
                if (avgBandwidth > 3_000_000) {
                    _recommendedConfig.value = VideoConfig.ULTRA
                } else if (_recommendedConfig.value != VideoConfig.HIGH) {
                    _recommendedConfig.value = VideoConfig.HIGH
                }
                adjustmentCounter = 0
            }
        }
    }

    /**
     * 重置监控状态
     */
    fun reset() {
        metricsHistory.clear()
        adjustmentCounter = 0
        _currentQuality.value = ConnectionQuality.GOOD
        _recommendedConfig.value = VideoConfig.MEDIUM
    }

    /**
     * 获取当前建议的配置
     */
    fun getSuggestedConfig(): VideoConfig {
        return _recommendedConfig.value
    }
}
