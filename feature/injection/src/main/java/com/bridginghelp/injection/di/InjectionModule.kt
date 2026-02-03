package com.bridginghelp.injection.di

import com.bridginghelp.injection.injector.KeyboardInjector
import com.bridginghelp.injection.injector.KeyboardInjectorImpl
import com.bridginghelp.injection.injector.TouchInjector
import com.bridginghelp.injection.injector.TouchInjectorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 输入注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InjectionModule {

    @Binds
    @Singleton
    abstract fun bindTouchInjector(
        impl: TouchInjectorImpl
    ): TouchInjector

    @Binds
    @Singleton
    abstract fun bindKeyboardInjector(
        impl: KeyboardInjectorImpl
    ): KeyboardInjector
}
