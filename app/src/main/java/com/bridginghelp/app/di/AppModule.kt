package com.bridginghelp.app.di

import android.content.Context
import android.os.Build
import android.annotation.SuppressLint
import com.bridginghelp.core.common.dispatcher.DefaultDispatcherProvider
import com.bridginghelp.core.common.dispatcher.DispatcherProvider
import com.bridginghelp.core.common.util.DefaultLogger
import com.bridginghelp.core.common.util.AppLogger
import com.bridginghelp.core.model.DeviceInfo
import com.bridginghelp.core.model.DeviceCapability
import com.bridginghelp.core.model.DeviceType
import com.bridginghelp.core.model.LocalDeviceInfo
import com.bridginghelp.core.permissions.PermissionManager
import com.bridginghelp.core.permissions.PermissionManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用级Hilt模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider {
        return DefaultDispatcherProvider()
    }

    @Provides
    @Singleton
    fun provideLogger(): AppLogger {
        return DefaultLogger().apply {
            setMinLevel(com.bridginghelp.core.common.util.LogLevel.DEBUG)
        }
    }

    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): PermissionManager {
        return PermissionManagerImpl(context)
    }

    @SuppressLint("NewApi")
    @Provides
    @Singleton
    fun provideLocalDeviceInfo(
        @ApplicationContext context: Context
    ): LocalDeviceInfo {
        val packageName = context.packageName
        val packageInfo = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenDensity = displayMetrics.densityDpi

        val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
        val deviceType = if (isTablet) DeviceType.TABLET else DeviceType.PHONE

        val capabilities = buildSet {
            add(DeviceCapability.TOUCH_INPUT)
            add(DeviceCapability.MULTI_TOUCH)
            add(DeviceCapability.KEYBOARD_INPUT)

            // 检查编解码能力
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(DeviceCapability.H264_ENCODE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(DeviceCapability.H265_ENCODE)
            }

            add(DeviceCapability.SCREEN_CAPTURE)
            add(DeviceCapability.P2P_CONNECTION)
            add(DeviceCapability.RELAY_CONNECTION)

            // 高分辨率支持
            if (screenWidth >= 1080 || screenHeight >= 1080) {
                add(DeviceCapability.HIGH_RESOLUTION)
            }
        }

        return LocalDeviceInfo(
            deviceId = getDeviceId(context),
            deviceName = getDeviceName(),
            deviceType = deviceType,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            screenDensity = screenDensity,
            osVersion = Build.VERSION.RELEASE_OR_CODENAME,
            appVersion = packageInfo?.versionName ?: "0.1.0",
            capabilities = capabilities
        )
    }

    private fun getDeviceId(context: Context): String {
        // 使用 Settings.Secure.ANDROID_ID 作为设备ID
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}
