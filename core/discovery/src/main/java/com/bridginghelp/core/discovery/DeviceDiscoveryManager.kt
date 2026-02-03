package com.bridginghelp.core.discovery

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.DeviceBroadcast
import com.bridginghelp.core.model.DiscoveredDevice
import com.bridginghelp.core.model.DeviceCapability
import com.bridginghelp.core.model.DeviceType
import com.bridginghelp.core.model.LocalDeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备发现管理器
 * 使用 UDP 广播实现局域网设备发现
 */
@Singleton
class DeviceDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val localDeviceInfo: LocalDeviceInfo
) {
    companion object {
        private const val TAG = "DeviceDiscoveryManager"
        private const val BUFFER_SIZE = 4096
    }

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var discoverySocket: DatagramSocket? = null
    private var isDiscovering = false
    private var broadcastJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    /**
     * 开始设备发现
     */
    suspend fun startDiscovery(): Result<Unit> = withContext(Dispatchers.IO) {
        LogWrapper.d(TAG, "Starting device discovery")

        return@withContext try {
            if (isDiscovering) {
                return@withContext Result.Success(Unit)
            }

            // 创建 UDP socket
            discoverySocket = DatagramSocket(DeviceBroadcast.BROADCAST_PORT).apply {
                broadcast = true
                soTimeout = 1000 // 1秒超时
            }

            isDiscovering = true

            // 启动广播线程
            startBroadcasting()

            // 启动接收线程
            startReceiving()

            LogWrapper.i(TAG, "Device discovery started")
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to start discovery", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 停止设备发现
     */
    suspend fun stopDiscovery(): Result<Unit> = withContext(Dispatchers.IO) {
        LogWrapper.d(TAG, "Stopping device discovery")

        return@withContext try {
            isDiscovering = false
            broadcastJob?.cancel()
            broadcastJob = null
            discoverySocket?.close()
            discoverySocket = null

            // 清空发现的设备
            _discoveredDevices.value = emptyMap()

            LogWrapper.i(TAG, "Device discovery stopped")
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to stop discovery", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 启动广播
     */
    private fun startBroadcasting() {
        broadcastJob = scope.launch {
            val broadcast = createDeviceBroadcast()
            val jsonData = kotlinx.serialization.json.Json.encodeToString(
                DeviceBroadcast.serializer(),
                broadcast
            ).toByteArray()

            while (isDiscovering) {
                try {
                    val broadcastAddress = InetAddress.getByName("255.255.255.255")
                    val packet = DatagramPacket(
                        jsonData,
                        jsonData.size,
                        broadcastAddress,
                        DeviceBroadcast.BROADCAST_PORT
                    )

                    discoverySocket?.send(packet)
                    LogWrapper.d(TAG, "Broadcast sent: ${broadcast.deviceName}")

                    kotlinx.coroutines.delay(DeviceBroadcast.BROADCAST_INTERVAL_MS)
                } catch (e: Exception) {
                    if (isDiscovering) {
                        LogWrapper.w(TAG, "Broadcast failed", e)
                    }
                }
            }
        }
    }

    /**
     * 启动接收
     */
    private fun startReceiving() {
        scope.launch {
            val buffer = ByteArray(BUFFER_SIZE)

            while (isDiscovering) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket?.receive(packet)

                    // 解析广播
                    val jsonData = String(packet.data, 0, packet.length)
                    val receivedBroadcast = kotlinx.serialization.json.Json.decodeFromString(
                        DeviceBroadcast.serializer(),
                        jsonData
                    )

                    // 忽略自己的广播
                    if (receivedBroadcast.deviceId == localDeviceInfo.deviceId) {
                        continue
                    }

                    // 添加到发现的设备列表
                    addDiscoveredDevice(
                        DiscoveredDevice(
                            deviceId = receivedBroadcast.deviceId,
                            deviceName = receivedBroadcast.deviceName,
                            deviceType = receivedBroadcast.deviceType,
                            ipAddress = packet.address.hostAddress ?: "unknown",
                            port = receivedBroadcast.port,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    if (isDiscovering) {
                        // 超时是正常的，不记录日志
                    }
                }
            }
        }
    }

    /**
     * 添加发现的设备
     */
    private fun addDiscoveredDevice(device: DiscoveredDevice) {
        val current = _discoveredDevices.value.toMutableMap()
        current[device.deviceId] = device
        _discoveredDevices.value = current

        LogWrapper.d(TAG, "Device discovered: ${device.deviceName} (${device.ipAddress})")
    }

    /**
     * 清理过期设备
     */
    fun cleanupExpiredDevices() {
        val current = _discoveredDevices.value
        val cleaned = current.filter { !it.value.isExpired() }

        if (cleaned.size < current.size) {
            _discoveredDevices.value = cleaned
            LogWrapper.d(TAG, "Cleaned up ${current.size - cleaned.size} expired devices")
        }
    }

    /**
     * 创建设备广播
     */
    private fun createDeviceBroadcast(): DeviceBroadcast {
        return DeviceBroadcast(
            deviceId = localDeviceInfo.deviceId,
            deviceName = localDeviceInfo.deviceName,
            deviceType = localDeviceInfo.deviceType,
            port = DeviceBroadcast.BROADCAST_PORT,
            capabilities = localDeviceInfo.capabilities
        )
    }

    /**
     * 获取本地IP地址
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                        return address.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to get local IP", e)
        }
        return "unknown"
    }
}
