package com.bridginghelp.core.model

import kotlinx.serialization.Serializable

/**
 * 设备发现信息
 */
@Serializable
data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val ipAddress: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true
) {
    /**
     * 获取完整地址
     */
    fun getFullAddress(): String = "$ipAddress:$port"

    /**
     * 检查是否过期（超过30秒未更新）
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - lastSeen > 30000
    }
}

/**
 * 设备广播消息
 */
@Serializable
data class DeviceBroadcast(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val port: Int,
    val capabilities: Set<DeviceCapability>,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val BROADCAST_PORT = 52345
        const val BROADCAST_INTERVAL_MS = 5000L
    }
}
