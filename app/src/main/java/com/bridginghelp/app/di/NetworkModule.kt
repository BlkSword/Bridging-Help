package com.bridginghelp.app.di

import com.bridginghelp.core.network.SignalingClient
import com.bridginghelp.signaling.client.WebSocketSignalingClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 网络模块
 * 提供网络相关接口的实现绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindSignalingClient(
        impl: WebSocketSignalingClient
    ): SignalingClient
}
