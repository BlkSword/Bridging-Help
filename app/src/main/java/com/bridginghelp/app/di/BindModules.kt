package com.bridginghelp.app.di

import com.bridginghelp.core.permissions.AccessibilityPermissionHandler
import com.bridginghelp.core.permissions.AccessibilityPermissionHandlerImpl
import com.bridginghelp.core.permissions.MediaProjectionPermissionHandler
import com.bridginghelp.core.permissions.MediaProjectionPermissionHandlerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 权限模块绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {

    @Binds
    @Singleton
    abstract fun bindMediaProjectionPermissionHandler(
        impl: MediaProjectionPermissionHandlerImpl
    ): MediaProjectionPermissionHandler

    @Binds
    @Singleton
    abstract fun bindAccessibilityPermissionHandler(
        impl: AccessibilityPermissionHandlerImpl
    ): AccessibilityPermissionHandler
}
