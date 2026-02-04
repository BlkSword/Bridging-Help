package com.bridginghelp.core.clipboard

import android.content.Context
import com.bridginghelp.core.model.DeviceCapability
import com.bridginghelp.core.model.DeviceInfo
import com.bridginghelp.core.model.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地设备信息提供者
 * 提供当前设备的标识和能力信息
 */
interface LocalDeviceInfoProvider {
    /**
     * 获取本地设备ID
     */
    fun getDeviceId(): String

    /**
     * 获取本地设备信息
     */
    fun getDeviceInfo(): DeviceInfo
}

/**
 * 默认的本地设备信息提供者实现
 */
@Singleton
class DefaultLocalDeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalDeviceInfoProvider {

    private var cachedDeviceId: String? = null
    private var cachedDeviceInfo: DeviceInfo? = null

    override fun getDeviceId(): String {
        return cachedDeviceId ?: generateDeviceId().also {
            cachedDeviceId = it
        }
    }

    override fun getDeviceInfo(): DeviceInfo {
        return cachedDeviceInfo ?: DeviceInfo(
            deviceId = getDeviceId(),
            deviceName = getDeviceName(),
            deviceType = DeviceType.PHONE,
            osVersion = android.os.Build.VERSION.RELEASE,
            appVersion = getAppVersion(),
            capabilities = getDefaultCapabilities()
        ).also {
            cachedDeviceInfo = it
        }
    }

    /**
     * 获取默认设备能力
     */
    private fun getDefaultCapabilities(): Set<DeviceCapability> = setOf(
        DeviceCapability.TOUCH_INPUT,
        DeviceCapability.MULTI_TOUCH,
        DeviceCapability.SCREEN_CAPTURE,
        DeviceCapability.AUDIO_CAPTURE,
        DeviceCapability.P2P_CONNECTION,
        DeviceCapability.H264_ENCODE,
        DeviceCapability.FILE_TRANSFER,
        DeviceCapability.CLIPBOARD_SYNC
    )

    /**
     * 生成设备ID
     * 使用Android ID作为唯一标识符
     */
    private fun generateDeviceId(): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        // 如果Android ID为null，使用回退方案
        return androidId ?: "android_${System.currentTimeMillis()}"
    }

    /**
     * 获取设备名称
     */
    private fun getDeviceName(): String {
        // 尝试获取设备名称
        val name = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER
        return if (manufacturer.equals(name, ignoreCase = true)) {
            name
        } else {
            "$manufacturer $name"
        }
    }

    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * 设置设备信息（可从外部设置）
     */
    fun setDeviceInfo(deviceInfo: DeviceInfo) {
        cachedDeviceInfo = deviceInfo
        cachedDeviceId = deviceInfo.deviceId
    }

    /**
     * 刷新设备信息缓存
     */
    fun refresh() {
        cachedDeviceId = null
        cachedDeviceInfo = null
    }
}
