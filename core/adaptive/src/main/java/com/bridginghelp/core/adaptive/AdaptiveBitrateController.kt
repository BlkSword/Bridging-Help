package com.bridginghelp.core.adaptive

import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.ConnectionQuality
import com.bridginghelp.core.model.VideoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 自适应码率控制器
 * 根据网络状况动态调整视频质量
 */
@Singleton
class AdaptiveBitrateController @Inject constructor() {

    companion object {
        private const val TAG = "AdaptiveBitrateController"
        private const val ADJUSTMENT_INTERVAL_MS = 2000L
        private const val STABLE_FRAMES_THRESHOLD = 30
        private const val DEGRADED_FRAMES_THRESHOLD = 10
    }

    private val scope = CoroutineScope(SupervisorJob())

    private val _qualityState = MutableStateFlow<QualityState>(QualityState.Initial)
    val qualityState: StateFlow<QualityState> = _qualityState.asStateFlow()

    private val _currentConfig = MutableStateFlow<VideoConfig?>(null)
    val currentConfig: StateFlow<VideoConfig?> = _currentConfig.asStateFlow()

    private var adjustmentJob: Job? = null
    private var isRunning = false

    // 网络质量监控
    private var currentLatency = 0
    private var currentPacketLoss = 0f
    private var currentBandwidth = 0f
    private var stableFrameCount = 0
    private var degradedFrameCount = 0

    // 预定义的视频配置
    private val qualityPresets = listOf(
        VideoConfig(
            width = 1920,
            height = 1080,
            frameRate = 30,
            bitrate = 4000000,
            codec = com.bridginghelp.core.model.VideoCodec.H264
        ),
        VideoConfig(
            width = 1280,
            height = 720,
            frameRate = 30,
            bitrate = 2500000,
            codec = com.bridginghelp.core.model.VideoCodec.H264
        ),
        VideoConfig(
            width = 854,
            height = 480,
            frameRate = 30,
            bitrate = 1500000,
            codec = com.bridginghelp.core.model.VideoCodec.H264
        ),
        VideoConfig(
            width = 640,
            height = 360,
            frameRate = 24,
            bitrate = 800000,
            codec = com.bridginghelp.core.model.VideoCodec.H264
        ),
        VideoConfig(
            width = 480,
            height = 270,
            frameRate = 15,
            bitrate = 400000,
            codec = com.bridginghelp.core.model.VideoCodec.H264
        )
    )

    private var currentPresetIndex = 1 // 默认从 720p 开始

    /**
     * 质量状态
     */
    sealed class QualityState {
        data object Initial : QualityState()
        data object Adjusting : QualityState()
        data class Stable(val config: VideoConfig) : QualityState()
        data class Degraded(val config: VideoConfig, val reason: String) : QualityState()
    }

    /**
     * 启动自适应控制
     */
    fun start(initialConfig: VideoConfig) {
        if (isRunning) return

        LogWrapper.d(TAG, "Starting adaptive bitrate control")
        isRunning = true

        // 找到最接近初始配置的预设
        currentPresetIndex = findClosestPreset(initialConfig)
        _currentConfig.value = qualityPresets[currentPresetIndex]
        _qualityState.value = QualityState.Stable(_currentConfig.value!!)

        // 启动调整循环
        adjustmentJob = scope.launch {
            while (isRunning) {
                delay(ADJUSTMENT_INTERVAL_MS)
                evaluateAndAdjust()
            }
        }
    }

    /**
     * 停止自适应控制
     */
    fun stop() {
        LogWrapper.d(TAG, "Stopping adaptive bitrate control")
        isRunning = false
        adjustmentJob?.cancel()
        adjustmentJob = null
        _qualityState.value = QualityState.Initial
    }

    /**
     * 更新网络指标
     */
    fun updateNetworkMetrics(
        latency: Int,
        packetLoss: Float = 0f,
        bandwidth: Float = 0f
    ) {
        currentLatency = latency
        currentPacketLoss = packetLoss
        currentBandwidth = bandwidth

        LogWrapper.d(TAG, "Network metrics: latency=${latency}ms, loss=$packetLoss, bw=${bandwidth}Kbps")
    }

    /**
     * 报告帧状态
     */
    fun reportFrameStatus(isHealthy: Boolean) {
        if (isHealthy) {
            stableFrameCount++
            degradedFrameCount = max(0, degradedFrameCount - 1)
        } else {
            degradedFrameCount++
            stableFrameCount = max(0, stableFrameCount - 1)
        }
    }

    /**
     * 评估并调整质量
     */
    private fun evaluateAndAdjust() {
        val connectionQuality = evaluateConnectionQuality()

        when (connectionQuality) {
            ConnectionQuality.EXCELLENT -> {
                // 网络优秀，尝试提高质量
                if (currentPresetIndex > 0 && stableFrameCount > STABLE_FRAMES_THRESHOLD) {
                    increaseQuality()
                    stableFrameCount = 0
                }
            }
            ConnectionQuality.GOOD -> {
                // 网络良好，保持当前质量
                if (stableFrameCount > STABLE_FRAMES_THRESHOLD * 2) {
                    // 非常稳定，可以尝试提高
                    if (currentPresetIndex > 0) {
                        increaseQuality()
                        stableFrameCount = STABLE_FRAMES_THRESHOLD
                    }
                }
            }
            ConnectionQuality.FAIR -> {
                // 网络一般，可能需要降低质量
                if (degradedFrameCount > DEGRADED_FRAMES_THRESHOLD && currentPresetIndex < qualityPresets.size - 1) {
                    decreaseQuality("Network quality degraded to FAIR")
                    degradedFrameCount = 0
                }
            }
            ConnectionQuality.POOR -> {
                // 网络较差，降低质量
                if (currentPresetIndex < qualityPresets.size - 1) {
                    decreaseQuality("Network quality degraded to POOR")
                }
            }
        }
    }

    /**
     * 评估连接质量
     */
    private fun evaluateConnectionQuality(): ConnectionQuality {
        // 综合延迟、丢包率和带宽评估
        return when {
            currentPacketLoss > 0.05f -> ConnectionQuality.POOR
            currentLatency > 500 -> ConnectionQuality.POOR
            currentPacketLoss > 0.02f || currentLatency > 300 -> ConnectionQuality.FAIR
            currentPacketLoss > 0.01f || currentLatency > 150 -> ConnectionQuality.GOOD
            else -> ConnectionQuality.EXCELLENT
        }
    }

    /**
     * 提高质量
     */
    private fun increaseQuality() {
        if (currentPresetIndex > 0) {
            currentPresetIndex--
            val newConfig = qualityPresets[currentPresetIndex]
            _currentConfig.value = newConfig
            _qualityState.value = QualityState.Stable(newConfig)
            LogWrapper.i(TAG, "Quality increased: ${newConfig.width}x${newConfig.height} @ ${newConfig.bitrate / 1000000}Mbps")
        }
    }

    /**
     * 降低质量
     */
    private fun decreaseQuality(reason: String) {
        if (currentPresetIndex < qualityPresets.size - 1) {
            currentPresetIndex++
            val newConfig = qualityPresets[currentPresetIndex]
            _currentConfig.value = newConfig
            _qualityState.value = QualityState.Degraded(newConfig, reason)
            LogWrapper.i(TAG, "Quality decreased: ${newConfig.width}x${newConfig.height} @ ${newConfig.bitrate / 1000000}Mbps - $reason")
        }
    }

    /**
     * 手动设置配置
     */
    fun setManualConfig(config: VideoConfig) {
        stop()
        _currentConfig.value = config
        _qualityState.value = QualityState.Stable(config)
        currentPresetIndex = findClosestPreset(config)
    }

    /**
     * 获取推荐配置
     */
    fun getRecommendedConfig(targetBandwidth: Float): VideoConfig {
        // 根据目标带宽选择最合适的配置
        val bitrate = (targetBandwidth * 1000000 * 0.8f).toInt() // 使用80%的带宽

        for (preset in qualityPresets) {
            if (preset.bitrate <= bitrate) {
                return preset
            }
        }

        return qualityPresets.last()
    }

    /**
     * 找到最接近的预设
     */
    private fun findClosestPreset(config: VideoConfig): Int {
        var closestIndex = 1
        var minDiff = Int.MAX_VALUE

        qualityPresets.forEachIndexed { index, preset ->
            val diff = kotlin.math.abs(preset.bitrate - config.bitrate)
            if (diff < minDiff) {
                minDiff = diff
                closestIndex = index
            }
        }

        return closestIndex
    }

    /**
     * 重置状态
     */
    fun reset() {
        stableFrameCount = 0
        degradedFrameCount = 0
        currentLatency = 0
        currentPacketLoss = 0f
        currentBandwidth = 0f
        currentPresetIndex = 1
        _currentConfig.value = qualityPresets[currentPresetIndex]
        _qualityState.value = QualityState.Stable(_currentConfig.value!!)
    }

    /**
     * 获取当前质量等级
     */
    fun getCurrentQualityLevel(): Int {
        return qualityPresets.size - currentPresetIndex
    }

    /**
     * 获取最大质量等级
     */
    fun getMaxQualityLevel(): Int {
        return qualityPresets.size
    }
}
