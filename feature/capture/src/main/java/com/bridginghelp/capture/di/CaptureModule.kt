package com.bridginghelp.capture.di

import com.bridginghelp.capture.encoder.VideoEncoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 屏幕捕获模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    @Binds
    @Singleton
    abstract fun bindVideoEncoderFactory(
        factory: com.bridginghelp.capture.encoder.HardwareVideoEncoder.Factory
    ): VideoEncoder.Factory
}
