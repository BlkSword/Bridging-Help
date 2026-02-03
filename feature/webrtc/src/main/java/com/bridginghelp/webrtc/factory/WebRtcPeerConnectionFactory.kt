package com.bridginghelp.webrtc.factory

import android.content.Context
import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.ErrorEntity
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebRTC配置
 */
data class WebRtcConfig(
    val audioEnabled: Boolean = false,
    val videoEnabled: Boolean = true,
    val preferredVideoCodec: VideoCodec = VideoCodec.H264,
    val iceServers: List<String> = getDefaultIceServers()
) {
    companion object {
        fun getDefaultIceServers(): List<String> {
            return listOf(
                "stun:stun.l.google.com:19302",
                "stun:stun1.l.google.com:19302"
            )
        }
    }
}

/**
 * PeerConnectionFactory工厂
 * 负责创建和配置WebRTC PeerConnectionFactory
 */
@Singleton
class WebRtcPeerConnectionFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: WebRtcConfig
) {

    companion object {
        private const val TAG = "WebRtcPeerConnectionFactory"
    }

    private var factory: PeerConnectionFactory? = null
    private var eglContext: EglBase.Context? = null

    /**
     * 初始化工厂
     */
    fun initialize(): Result<Unit> {
        LogWrapper.d(TAG, "Initializing PeerConnectionFactory")

        return try {
            // 初始化 WebRTC 库
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )

            // 初始化 EGL 上下文（用于硬件加速）
            initializeEglContext()

            // 创建PeerConnectionFactory
            factory = createPeerConnectionFactoryInternal()

            LogWrapper.i(TAG, "PeerConnectionFactory initialized successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to initialize PeerConnectionFactory", e)
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 获取PeerConnectionFactory实例
     */
    fun getFactory(): PeerConnectionFactory? {
        return factory
    }

    /**
     * 创建PeerConnection
     */
    fun createPeerConnection(
        observer: PeerConnection.Observer,
        constraints: MediaConstraints? = null
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(config.iceServers.map { url ->
            PeerConnection.IceServer.builder(url).createIceServer()
        }).apply {
            // 启用DTLS/SRTP
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            // 配置ICE传输策略
            iceTransportsType = PeerConnection.IceTransportsType.ALL

            // 启用连续的ICE连接
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

            // 配置媒体传输
            enableCpuOveruseDetection = true
        }

        return factory?.createPeerConnection(rtcConfig, constraints, observer)
    }

    /**
     * 创建视频轨道
     */
    fun createVideoTrack(): VideoTrack? {
        val videoSource = factory?.createVideoSource(false)
        return videoSource?.let { factory?.createVideoTrack("video_track", it) }
    }

    /**
     * 创建音频轨道
     */
    fun createAudioTrack(): AudioTrack? {
        val audioSource = factory?.createAudioSource(MediaConstraints())
        return audioSource?.let { factory?.createAudioTrack("audio_track", it) }
    }

    /**
     * 初始化EGL上下文
     */
    private fun initializeEglContext() {
        if (eglContext == null) {
            eglContext = EglBase.create().eglBaseContext
            LogWrapper.d(TAG, "EGL context initialized")
        }
    }

    /**
     * 内部方法：创建PeerConnectionFactory
     */
    private fun createPeerConnectionFactoryInternal(): PeerConnectionFactory {
        // 创建视频编码器和解码器工厂
        val videoEncoderFactory = DefaultVideoEncoderFactory(
            eglContext,
            true,   // shareContext
            true    // enableIntelVp8Encoder
        )
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglContext)

        // 使用简化的 API 创建 PeerConnectionFactory
        // 注意：如果此方法失败，可能需要使用更复杂的初始化流程
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    /**
     * 获取EGL上下文
     */
    fun getEglContext(): EglBase.Context? {
        return eglContext
    }

    /**
     * 释放资源
     */
    fun dispose() {
        LogWrapper.d(TAG, "Disposing PeerConnectionFactory")

        factory?.dispose()
        factory = null

        eglContext = null

        LogWrapper.i(TAG, "PeerConnectionFactory disposed")
    }
}
