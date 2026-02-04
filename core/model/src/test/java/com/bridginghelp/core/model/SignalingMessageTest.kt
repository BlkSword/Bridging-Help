package com.bridginghelp.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * SignalingMessage 序列化测试
 */
class SignalingMessageTest {

    private lateinit var json: Json

    @BeforeEach
    fun setup() {
        json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    @Test
    fun `Offer message should serialize correctly`() {
        val message = SignalingMessage.Offer(
            sessionId = "session_123",
            sdp = "v=0\r\no=- 123456 2 IN IP4 127.0.0.1..."
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"offer\""))
        assertTrue(serialized.contains("session_123"))
    }

    @Test
    fun `Offer message should deserialize correctly`() {
        val jsonStr = """{"type":"offer","sessionId":"session_123","sdp":"test-sdp","timestamp":123456}"""
        val message = json.decodeFromString<SignalingMessage>(jsonStr)

        assertTrue(message is SignalingMessage.Offer)
        val offerMessage = message as SignalingMessage.Offer
        assertEquals("session_123", offerMessage.sessionId)
        assertEquals("test-sdp", offerMessage.sdp)
    }

    @Test
    fun `Answer message should serialize correctly`() {
        val message = SignalingMessage.Answer(
            sessionId = "session_456",
            sdp = "v=0\r\no=- 789012 2 IN IP4 127.0.0.1..."
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"answer\""))
    }

    @Test
    fun `IceCandidate message should serialize correctly`() {
        val message = SignalingMessage.IceCandidateMsg(
            sessionId = "session_789",
            sdpMid = "0",
            sdpMLineIndex = 0,
            candidate = "candidate:1 1 UDP 2130706431 192.168.1.1 54400 typ host"
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"ice_candidate\""))
        assertTrue(serialized.contains("192.168.1.1"))
    }

    @Test
    fun `IceCandidate message should deserialize correctly`() {
        val jsonStr = """{"type":"ice_candidate","sessionId":"session_789","sdpMid":"0","sdpMLineIndex":0,"candidate":"test-candidate"}"""
        val message = json.decodeFromString<SignalingMessage>(jsonStr)

        assertTrue(message is SignalingMessage.IceCandidateMsg)
        val iceMessage = message as SignalingMessage.IceCandidateMsg
        assertEquals("session_789", iceMessage.sessionId)
        assertEquals("0", iceMessage.sdpMid)
        assertEquals(0, iceMessage.sdpMLineIndex)
    }

    @Test
    fun `SessionEnd message should serialize correctly`() {
        val message = SignalingMessage.SessionEnd(
            sessionId = "session_999",
            reason = DisconnectReason.USER_INITIATED
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"session_end\""))
        assertTrue(serialized.contains("\"USER_INITIATED\""))
    }

    @Test
    fun `Heartbeat message should serialize correctly`() {
        val message = SignalingMessage.Heartbeat(
            sessionId = "session_111",
            sequence = 5
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"heartbeat\""))
        assertEquals(5, message.sequence)
    }

    @Test
    fun `ConnectionRequest message should serialize correctly`() {
        val deviceInfo = DeviceInfo(
            deviceId = "device_123",
            deviceName = "Test Device",
            deviceType = DeviceType.PHONE,
            osVersion = "13",
            appVersion = "1.0.0",
            capabilities = setOf(
                DeviceCapability.TOUCH_INPUT,
                DeviceCapability.SCREEN_CAPTURE
            )
        )

        val message = SignalingMessage.ConnectionRequest(
            sessionId = "session_222",
            requesterId = "user_456",
            requesterName = "John",
            deviceInfo = deviceInfo
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"connection_request\""))
        assertTrue(serialized.contains("Test Device"))
    }

    @Test
    fun `ConnectionResponse message should serialize correctly`() {
        val message = SignalingMessage.ConnectionResponse(
            sessionId = "session_333",
            accepted = true,
            reason = null
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"connection_response\""))
        assertTrue(serialized.contains("\"accepted\":true"))
    }

    @Test
    fun `DisconnectReason enum should have correct values`() {
        assertEquals(DisconnectReason.USER_INITIATED, DisconnectReason.valueOf("USER_INITIATED"))
        assertEquals(DisconnectReason.REMOTE_DISCONNECTED, DisconnectReason.valueOf("REMOTE_DISCONNECTED"))
        assertEquals(DisconnectReason.NETWORK_ERROR, DisconnectReason.valueOf("NETWORK_ERROR"))
    }

    @Test
    fun `Signaling flow roundtrip should work`() {
        // Offer -> Answer 流程
        val offer = SignalingMessage.Offer("session_1", "sdp_offer")
        val offerJson = json.encodeToString(offer)
        val decodedOffer = json.decodeFromString<SignalingMessage>(offerJson)

        assertTrue(decodedOffer is SignalingMessage.Offer)
        val resultOffer = decodedOffer as SignalingMessage.Offer
        assertEquals(offer.sessionId, resultOffer.sessionId)

        // Answer
        val answer = SignalingMessage.Answer("session_1", "sdp_answer")
        val answerJson = json.encodeToString(answer)
        val decodedAnswer = json.decodeFromString<SignalingMessage>(answerJson)

        assertTrue(decodedAnswer is SignalingMessage.Answer)
        val resultAnswer = decodedAnswer as SignalingMessage.Answer
        assertEquals(answer.sessionId, resultAnswer.sessionId)
    }

    @Test
    fun `ICE exchange flow should work`() {
        val candidate = SignalingMessage.IceCandidateMsg(
            sessionId = "session_ice",
            sdpMid = "video",
            sdpMLineIndex = 1,
            candidate = "candidate:1 1 UDP 2130706431 192.168.1.1 54400 typ host"
        )

        val serialized = json.encodeToString(candidate)
        val deserialized = json.decodeFromString<SignalingMessage>(serialized)

        assertTrue(deserialized is SignalingMessage.IceCandidateMsg)
        val result = deserialized as SignalingMessage.IceCandidateMsg
        assertEquals(candidate.candidate, result.candidate)
    }
}
