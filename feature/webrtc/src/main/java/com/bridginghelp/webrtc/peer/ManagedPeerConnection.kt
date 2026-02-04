package com.bridginghelp.webrtc.peer

import com.bridginghelp.core.common.result.Result
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.IceConnectionState
import com.bridginghelp.core.model.NetworkMetrics
import com.bridginghelp.core.model.PeerConnectionState
import com.bridginghelp.webrtc.factory.WebRtcPeerConnectionFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 托管的PeerConnection
 * 提供协程友好的API和状态管理
 */
@Singleton
class ManagedPeerConnection @Inject constructor(
    private val factory: WebRtcPeerConnectionFactory
) {

    companion object {
        private const val TAG = "ManagedPeerConnection"
    }

    private var peerConnection: PeerConnection? = null

    private val _connectionState = MutableStateFlow(PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnectionState> = _connectionState.asStateFlow()

    private val _iceConnectionState = MutableStateFlow(IceConnectionState.NEW)
    val iceConnectionState: StateFlow<IceConnectionState> = _iceConnectionState.asStateFlow()

    private val _remoteStream = MutableStateFlow<MediaStream?>(null)
    val remoteStream: StateFlow<MediaStream?> = _remoteStream.asStateFlow()

    private val iceCandidateQueue = mutableListOf<IceCandidate>()
    private var localDescriptionSet = false
    private var remoteDescriptionSet = false

    /**
     * 创建Offer
     */
    suspend fun createOffer(constraints: MediaConstraints? = null): Result<SessionDescription> {
        LogWrapper.d(TAG, "Creating offer")

        return suspendCancellableCoroutine { continuation ->
            val sdpObserver = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    LogWrapper.i(TAG, "Offer created successfully")
                    continuation.resume(Result.Success(sdp))
                }

                override fun onSetSuccess() {
                    // Not used for createOffer
                }

                override fun onCreateFailure(error: String) {
                    LogWrapper.e(TAG, "Failed to create offer: $error")
                    continuation.resume(Result.Error(com.bridginghelp.core.common.result.ErrorEntity.Unknown(error)))
                }

                override fun onSetFailure(error: String) {
                    // Not used for createOffer
                }
            }

            peerConnection?.createOffer(sdpObserver, constraints ?: createDefaultConstraints())
        }
    }

    /**
     * 创建Answer
     */
    suspend fun createAnswer(constraints: MediaConstraints? = null): Result<SessionDescription> {
        LogWrapper.d(TAG, "Creating answer")

        return suspendCancellableCoroutine { continuation ->
            val sdpObserver = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    LogWrapper.i(TAG, "Answer created successfully")
                    continuation.resume(Result.Success(sdp))
                }

                override fun onSetSuccess() {
                    // Not used for createAnswer
                }

                override fun onCreateFailure(error: String) {
                    LogWrapper.e(TAG, "Failed to create answer: $error")
                    continuation.resume(Result.Error(com.bridginghelp.core.common.result.ErrorEntity.Unknown(error)))
                }

                override fun onSetFailure(error: String) {
                    // Not used for createAnswer
                }
            }

            peerConnection?.createAnswer(sdpObserver, constraints ?: createDefaultConstraints())
        }
    }

    /**
     * 设置本地描述
     */
    suspend fun setLocalDescription(description: SessionDescription): Result<Unit> {
        LogWrapper.d(TAG, "Setting local description: ${description.type}")

        return suspendCancellableCoroutine { continuation ->
            val sdpObserver = object : SdpObserver {
                override fun onSetSuccess() {
                    LogWrapper.i(TAG, "Local description set successfully")
                    localDescriptionSet = true
                    flushIceCandidates()
                    continuation.resume(Result.Success(Unit))
                }

                override fun onCreateSuccess(sdp: SessionDescription) {
                    // Not used
                }

                override fun onSetFailure(error: String) {
                    LogWrapper.e(TAG, "Failed to set local description: $error")
                    continuation.resume(Result.Error(com.bridginghelp.core.common.result.ErrorEntity.Unknown(error)))
                }

                override fun onCreateFailure(error: String) {
                    // Not used
                }
            }

            peerConnection?.setLocalDescription(sdpObserver, description)
        }
    }

    /**
     * 设置远程描述
     */
    suspend fun setRemoteDescription(description: SessionDescription): Result<Unit> {
        LogWrapper.d(TAG, "Setting remote description: ${description.type}")

        return suspendCancellableCoroutine { continuation ->
            val sdpObserver = object : SdpObserver {
                override fun onSetSuccess() {
                    LogWrapper.i(TAG, "Remote description set successfully")
                    remoteDescriptionSet = true
                    flushIceCandidates()
                    continuation.resume(Result.Success(Unit))
                }

                override fun onCreateSuccess(sdp: SessionDescription) {
                    // Not used
                }

                override fun onSetFailure(error: String) {
                    LogWrapper.e(TAG, "Failed to set remote description: $error")
                    continuation.resume(Result.Error(com.bridginghelp.core.common.result.ErrorEntity.Unknown(error)))
                }

                override fun onCreateFailure(error: String) {
                    // Not used
                }
            }

            peerConnection?.setRemoteDescription(sdpObserver, description)
        }
    }

    /**
     * 添加ICE候选
     */
    fun addIceCandidate(candidate: IceCandidate): Result<Unit> {
        return try {
            if (remoteDescriptionSet) {
                peerConnection?.addIceCandidate(candidate)
                LogWrapper.d(TAG, "ICE candidate added")
                Result.Success(Unit)
            } else {
                // 远程描述还没设置，先缓存
                iceCandidateQueue.add(candidate)
                LogWrapper.d(TAG, "ICE candidate queued")
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to add ICE candidate", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 刷新缓存的ICE候选
     */
    private fun flushIceCandidates() {
        if (remoteDescriptionSet && iceCandidateQueue.isNotEmpty()) {
            LogWrapper.d(TAG, "Flushing ${iceCandidateQueue.size} queued ICE candidates")
            iceCandidateQueue.forEach { candidate ->
                peerConnection?.addIceCandidate(candidate)
            }
            iceCandidateQueue.clear()
        }
    }

    /**
     * 添加轨道
     */
    fun addTrack(track: MediaStreamTrack) {
        peerConnection?.addTransceiver(track)?.let { sender ->
            LogWrapper.d(TAG, "Track added: ${track.kind()}")
        }
    }

    /**
     * 移除轨道
     */
    fun removeTrack(sender: RtpSender) {
        peerConnection?.removeTrack(sender)
        LogWrapper.d(TAG, "Track removed")
    }

    /**
     * 创建数据通道
     */
    fun createDataChannel(
        label: String,
        ordered: Boolean = true,
        protocol: String = "json"
    ): DataChannel? {
        val init = DataChannel.Init().apply {
            this.ordered = ordered
            this.protocol = protocol
        }

        return peerConnection?.createDataChannel(label, init)
    }

    /**
     * 初始化连接
     */
    fun initialize(observer: PeerConnection.Observer): Result<Unit> {
        LogWrapper.d(TAG, "Initializing peer connection")

        return try {
            peerConnection = factory.createPeerConnection(observer)

            if (peerConnection == null) {
                Result.Error(com.bridginghelp.core.common.result.ErrorEntity.Unknown("Failed to create peer connection"))
            } else {
                _connectionState.value = PeerConnectionState.NEW
                LogWrapper.i(TAG, "Peer connection initialized")
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Failed to initialize peer connection", e)
            Result.Error(com.bridginghelp.core.common.result.ErrorEntity.fromException(e))
        }
    }

    /**
     * 关闭连接
     */
    fun close() {
        LogWrapper.d(TAG, "Closing peer connection")

        iceCandidateQueue.clear()
        peerConnection?.close()
        peerConnection = null

        localDescriptionSet = false
        remoteDescriptionSet = false

        _connectionState.value = PeerConnectionState.NEW
        _iceConnectionState.value = IceConnectionState.NEW
        _remoteStream.value = null

        LogWrapper.i(TAG, "Peer connection closed")
    }

    /**
     * 创建默认约束
     */
    private fun createDefaultConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
    }

    /**
     * 获取远程媒体流
     */
    fun getRemoteStreams(): List<org.webrtc.MediaStream> {
        return remoteStream.value?.let { listOf(it) } ?: emptyList()
    }
}
