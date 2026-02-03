package com.bridginghelp.webrtc.datachannel

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.result.ErrorEntity
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.RemoteEvent
import com.bridginghelp.webrtc.peer.ManagedPeerConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据通道管理器
 * 负责管理WebRTC数据通道，发送和接收远程事件
 */
@Singleton
class DataChannelManager @Inject constructor() {

    companion object {
        private const val TAG = "DataChannelManager"
        private const val DEFAULT_CHANNEL_LABEL = "remote_events"
    }

    private var dataChannel: DataChannel? = null
    private val messageChannel = Channel<DataChannel.Buffer>(capacity = Channel.UNLIMITED)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 初始化数据通道
     */
    fun initialize(
        peerConnection: ManagedPeerConnection,
        label: String = DEFAULT_CHANNEL_LABEL
    ): Result<Unit> {
        LogWrapper.d(TAG, "Initializing data channel: $label")

        return try {
            dataChannel = peerConnection.createDataChannel(
                label = label,
                ordered = true,
                protocol = "json"
            )

            dataChannel?.registerObserver(createObserver())

            LogWrapper.i(TAG, "Data channel initialized: ${dataChannel?.state()}")
            Result.Success(Unit)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to initialize data channel", e)
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 设置远程数据通道
     * 当对等方创建数据通道时调用
     */
    fun setRemoteDataChannel(channel: DataChannel) {
        LogWrapper.d(TAG, "Setting remote data channel")

        dataChannel = channel
        dataChannel?.registerObserver(createObserver())

        LogWrapper.i(TAG, "Remote data channel set: ${dataChannel?.state()}")
    }

    /**
     * 发送远程事件
     */
    suspend fun sendRemoteEvent(event: RemoteEvent): Result<Unit> {
        if (dataChannel?.state() != DataChannel.State.OPEN) {
            LogWrapper.w(TAG, "Data channel not open, state: ${dataChannel?.state()}")
            return Result.Error(ErrorEntity.Unknown("Data channel not open"))
        }

        return try {
            val jsonStr = json.encodeToString(event)
            val data = jsonStr.toByteArray(Charsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(data), true)

            val success = dataChannel?.send(buffer) ?: false

            if (success) {
                LogWrapper.d(TAG, "Remote event sent: ${event::class.simpleName}")
                Result.Success(Unit)
            } else {
                LogWrapper.e(TAG, "Failed to send remote event")
                Result.Error(ErrorEntity.Unknown("Failed to send data"))
            }
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error sending remote event", e)
            Result.Error(ErrorEntity.fromException(e))
        }
    }

    /**
     * 获取消息流
     */
    fun getMessages(): Flow<DataChannel.Buffer> {
        return messageChannel.receiveAsFlow()
    }

    /**
     * 获取远程事件流
     */
    fun getRemoteEvents(): Flow<RemoteEvent> {
        return messageChannel.receiveAsFlow()
            .mapNotNull { buffer ->
                parseRemoteEvent(buffer)
            }
    }

    /**
     * 解析远程事件
     */
    private fun parseRemoteEvent(buffer: DataChannel.Buffer): RemoteEvent? {
        return try {
            val data = ByteArray(buffer.data.remaining())
            buffer.data.get(data)
            val jsonStr = String(data, Charsets.UTF_8)
            json.decodeFromString<RemoteEvent>(jsonStr)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to parse remote event", e)
            null
        }
    }

    /**
     * 创建数据通道观察者
     */
    private fun createObserver(): DataChannel.Observer {
        return object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                // 缓冲区大小变化，可以用于流控
                LogWrapper.d(TAG, "Buffered amount: ${previousAmount} -> ${dataChannel?.bufferedAmount()}")
            }

            override fun onStateChange() {
                val state = dataChannel?.state()
                LogWrapper.d(TAG, "Data channel state changed: $state")

                when (state) {
                    DataChannel.State.OPEN -> {
                        LogWrapper.i(TAG, "Data channel opened")
                    }
                    DataChannel.State.CLOSED,
                    DataChannel.State.CLOSING -> {
                        LogWrapper.w(TAG, "Data channel closed/closing")
                    }
                    else -> {
                        // 其他状态
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                LogWrapper.d(TAG, "Message received")
                messageChannel.trySend(buffer)
            }
        }
    }

    /**
     * 关闭数据通道
     */
    fun close() {
        LogWrapper.d(TAG, "Closing data channel")

        dataChannel?.close()
        dataChannel = null
        messageChannel.close()

        LogWrapper.i(TAG, "Data channel closed")
    }
}
