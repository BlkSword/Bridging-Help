package com.bridginghelp.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * RemoteEvent 序列化测试
 */
class RemoteEventTest {

    private lateinit var json: Json

    @BeforeEach
    fun setup() {
        json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    @Test
    fun `TouchEvent should serialize correctly`() {
        val event = RemoteEvent.TouchEvent(
            x = 0.5f,
            y = 0.7f,
            action = TouchAction.DOWN,
            pointerId = 1
        )

        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains("\"touch\""))
        assertTrue(serialized.contains("\"0.5\""))
        assertTrue(serialized.contains("\"0.7\""))
    }

    @Test
    fun `TouchEvent should deserialize correctly`() {
        val jsonStr = """{"type":"touch","x":0.5,"y":0.7,"action":"DOWN","pointerId":1}"""
        val event = json.decodeFromString<RemoteEvent>(jsonStr)

        assertTrue(event is RemoteEvent.TouchEvent)
        val touchEvent = event as RemoteEvent.TouchEvent
        assertEquals(0.5f, touchEvent.x)
        assertEquals(0.7f, touchEvent.y)
        assertEquals(TouchAction.DOWN, touchEvent.action)
    }

    @Test
    fun `KeyEvent should serialize correctly`() {
        val event = RemoteEvent.KeyEvent(
            keyCode = 4,
            action = KeyAction.DOWN,
            metaState = 0
        )

        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains("\"key\""))
        assertTrue(serialized.contains("4"))
    }

    @Test
    fun `KeyEvent should deserialize correctly`() {
        val jsonStr = """{"type":"key","keyCode":4,"action":"DOWN","metaState":0}"""
        val event = json.decodeFromString<RemoteEvent>(jsonStr)

        assertTrue(event is RemoteEvent.KeyEvent)
        val keyEvent = event as RemoteEvent.KeyEvent
        assertEquals(4, keyEvent.keyCode)
        assertEquals(KeyAction.DOWN, keyEvent.action)
    }

    @Test
    fun `ScrollEvent should serialize correctly`() {
        val event = RemoteEvent.ScrollEvent(
            deltaX = 10.5f,
            deltaY = -5.2f
        )

        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains("\"scroll\""))
        assertTrue(serialized.contains("10.5"))
        assertTrue(serialized.contains("-5.2"))
    }

    @Test
    fun `ScrollEvent should deserialize correctly`() {
        val jsonStr = """{"type":"scroll","deltaX":10.5,"deltaY":-5.2}"""
        val event = json.decodeFromString<RemoteEvent>(jsonStr)

        assertTrue(event is RemoteEvent.ScrollEvent)
        val scrollEvent = event as RemoteEvent.ScrollEvent
        assertEquals(10.5f, scrollEvent.deltaX)
        assertEquals(-5.2f, scrollEvent.deltaY)
    }

    @Test
    fun `GestureEvent with PINCH should serialize correctly`() {
        val event = RemoteEvent.GestureEvent(
            type = GestureType.PINCH,
            data = mapOf("x" to 0.5f, "y" to 0.5f, "scale" to 1.5f)
        )

        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains("\"gesture\""))
        assertTrue(serialized.contains("\"PINCH\""))
    }

    @Test
    fun `GestureEvent should deserialize correctly`() {
        val jsonStr = """{"type":"gesture","gestureType":"PINCH","data":{"x":0.5,"y":0.5,"scale":1.5}}"""
        val event = json.decodeFromString<RemoteEvent>(jsonStr)

        assertTrue(event is RemoteEvent.GestureEvent)
        val gestureEvent = event as RemoteEvent.GestureEvent
        assertEquals(GestureType.PINCH, gestureEvent.type)
        assertEquals(0.5f, gestureEvent.data["x"])
    }

    @Test
    fun `TouchAction enum should have correct values`() {
        assertEquals(TouchAction.DOWN, TouchAction.valueOf("DOWN"))
        assertEquals(TouchAction.UP, TouchAction.valueOf("UP"))
        assertEquals(TouchAction.MOVE, TouchAction.valueOf("MOVE"))
    }

    @Test
    fun `KeyAction enum should have correct values`() {
        assertEquals(KeyAction.DOWN, KeyAction.valueOf("DOWN"))
        assertEquals(KeyAction.UP, KeyAction.valueOf("UP"))
        assertEquals(KeyAction.MULTIPLE, KeyAction.valueOf("MULTIPLE"))
    }

    @Test
    fun `GestureType enum should have correct values`() {
        assertEquals(GestureType.PINCH, GestureType.valueOf("PINCH"))
        assertEquals(GestureType.ROTATION, GestureType.valueOf("ROTATION"))
        assertEquals(GestureType.LONG_PRESS, GestureType.valueOf("LONG_PRESS"))
    }

    @Test
    fun `Invalid TouchEvent should handle gracefully`() {
        val jsonStr = """{"type":"touch"}"""
        val result = runCatching {
            json.decodeFromString<RemoteEvent>(jsonStr)
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `Complete remote control flow should work`() {
        // 创建触摸事件
        val touchEvent = RemoteEvent.TouchEvent(
            x = 0.5f,
            y = 0.5f,
            action = TouchAction.DOWN
        )

        // 序列化
        val serialized = json.encodeToString(touchEvent)

        // 模拟网络传输后反序列化
        val deserialized = json.decodeFromString<RemoteEvent>(serialized)

        // 验证数据一致性
        assertTrue(deserialized is RemoteEvent.TouchEvent)
        val result = deserialized as RemoteEvent.TouchEvent
        assertEquals(touchEvent.x, result.x)
        assertEquals(touchEvent.y, result.y)
        assertEquals(touchEvent.action, result.action)
    }

    @Test
    fun `Multiple events roundtrip should preserve data`() {
        val events = listOf(
            RemoteEvent.TouchEvent(0.1f, 0.2f, TouchAction.DOWN, 0),
            RemoteEvent.KeyEvent(4, KeyAction.DOWN, 0),
            RemoteEvent.ScrollEvent(5.0f, -3.0f),
            RemoteEvent.GestureEvent(GestureType.PINCH, mapOf("scale" to 1.5f))
        )

        events.forEach { originalEvent ->
            val serialized = json.encodeToString(originalEvent)
            val deserialized = json.decodeFromString<RemoteEvent>(serialized)

            when (originalEvent) {
                is RemoteEvent.TouchEvent -> {
                    assertTrue(deserialized is RemoteEvent.TouchEvent)
                    val result = deserialized as RemoteEvent.TouchEvent
                    assertEquals(originalEvent.x, result.x)
                }
                is RemoteEvent.KeyEvent -> {
                    assertTrue(deserialized is RemoteEvent.KeyEvent)
                    val result = deserialized as RemoteEvent.KeyEvent
                    assertEquals(originalEvent.keyCode, result.keyCode)
                }
                is RemoteEvent.ScrollEvent -> {
                    assertTrue(deserialized is RemoteEvent.ScrollEvent)
                    val result = deserialized as RemoteEvent.ScrollEvent
                    assertEquals(originalEvent.deltaX, result.deltaX)
                }
                is RemoteEvent.GestureEvent -> {
                    assertTrue(deserialized is RemoteEvent.GestureEvent)
                    val result = deserialized as RemoteEvent.GestureEvent
                    assertEquals(originalEvent.type, result.type)
                }
            }
        }
    }
}
