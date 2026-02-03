package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 设备信息
 * 描述参与远程协助的设备
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val osVersion: String,
    val appVersion: String,
    val capabilities: Set<DeviceCapability>,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * 设备类型
 */
@Serializable
enum class DeviceType {
    PHONE,
    TABLET,
    FOLDABLE,
    DESKTOP  // 用于未来可能的桌面客户端
}

/**
 * 设备能力
 * 表示设备支持的特性
 */
@Serializable
enum class DeviceCapability {
    // 输入能力
    TOUCH_INPUT,
    MULTI_TOUCH,
    KEYBOARD_INPUT,
    MOUSE_INPUT,

    // 捕获能力
    SCREEN_CAPTURE,
    AUDIO_CAPTURE,
    CAMERA_CAPTURE,

    // 网络能力
    P2P_CONNECTION,
    RELAY_CONNECTION,

    // 编解码能力
    H264_ENCODE,
    H265_ENCODE,
    VP8_ENCODE,
    VP9_ENCODE,

    // 显示能力
    HIGH_RESOLUTION,  // 支持1080p+
    HDR,

    // 其他
    FILE_TRANSFER,
    CLIPBOARD_SYNC
}

/**
 * 设备角色
 * 表示设备在会话中的角色
 */
@Serializable
enum class DeviceRole {
    CONTROLLER,    // 控制端
    CONTROLLED    // 受控端
}

/**
 * 本地设备信息（不可序列化部分）
 * 用于运行时获取本地设备信息
 */
data class LocalDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDensity: Int,
    val osVersion: String,
    val appVersion: String,
    val capabilities: Set<DeviceCapability>
) {
    fun toSerializable(): DeviceInfo {
        return DeviceInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            deviceType = deviceType,
            osVersion = osVersion,
            appVersion = appVersion,
            capabilities = capabilities
        )
    }
}
