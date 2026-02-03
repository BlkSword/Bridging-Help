package com.bridginghelp.webrtc.di

import com.bridginghelp.webrtc.datachannel.DataChannelManager
import com.bridginghelp.webrtc.factory.WebRtcPeerConnectionFactory
import com.bridginghelp.webrtc.factory.WebRtcConfig
import com.bridginghelp.webrtc.peer.ManagedPeerConnection
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WebRTC模块
 */
@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    @Provides
    @Singleton
    fun provideWebRtcConfig(): WebRtcConfig {
        return WebRtcConfig(
            audioEnabled = false,
            videoEnabled = true,
            preferredVideoCodec = com.bridginghelp.core.model.VideoCodec.H264
        )
    }
}

/**
 * WebRTC绑定模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WebRtcBindsModule {

    // 如需绑定接口到实现，在这里添加
}
