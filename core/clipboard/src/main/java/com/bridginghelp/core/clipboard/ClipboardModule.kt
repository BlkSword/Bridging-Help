package com.bridginghelp.core.clipboard

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Clipboard 模块的依赖注入配置
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClipboardModule {

    /**
     * 绑定 LocalDeviceInfoProvider 接口到其默认实现
     */
    @Binds
    @Singleton
    abstract fun bindLocalDeviceInfoProvider(
        impl: DefaultLocalDeviceInfoProvider
    ): LocalDeviceInfoProvider
}
