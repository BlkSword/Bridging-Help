package com.bridginghelp.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName

/**
 * 远程事件基类
 * 表示从控制端发送到受控端的所有输入事件
 */
@Serializable
sealed class RemoteEvent {

    /**
     * 触摸事件
     * @param x X坐标 (0-1归一化)
     * @param y Y坐标 (0-1归一化)
     * @param action 动作类型 (DOWN, UP, MOVE, CANCEL)
     * @param pointerId 指针ID
     */
    @Serializable
    @SerialName("touch")
    data class TouchEvent(
        val x: Float,
        val y: Float,
        val action: TouchAction,
        val pointerId: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteEvent()

    /**
     * 键盘事件
     * @param keyCode 按键码
     * @param action 动作类型 (DOWN, UP, MULTIPLE)
     * @param metaState 元状态 (SHIFT, ALT, CTRL等)
     */
    @Serializable
    @SerialName("key")
    data class KeyEvent(
        val keyCode: Int,
        val action: KeyAction,
        val metaState: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteEvent()

    /**
     * 滚动事件
     * @param deltaX X轴滚动距离
     * @param deltaY Y轴滚动距离
     */
    @Serializable
    @SerialName("scroll")
    data class ScrollEvent(
        val deltaX: Float,
        val deltaY: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteEvent()

    /**
     * 手势事件（用于复杂手势如捏合、旋转等）
     * @param type 手势类型
     * @param data 手势数据
     */
    @Serializable
    @SerialName("gesture")
    data class GestureEvent(
        val type: GestureType,
        val data: Map<String, Float>,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteEvent()
}

/**
 * 触摸动作类型
 */
@Serializable
enum class TouchAction {
    DOWN,
    UP,
    MOVE,
    CANCEL,
    OUTSIDE,
    POINTER_DOWN,
    POINTER_UP
}

/**
 * 键盘动作类型
 */
@Serializable
enum class KeyAction {
    DOWN,
    UP,
    MULTIPLE
}

/**
 * 手势类型
 */
@Serializable
enum class GestureType {
    PINCH,          // 捏合缩放
    ROTATION,       // 旋转
    LONG_PRESS,     // 长按
    DOUBLE_TAP,     // 双击
    TWO_FINGER_TAP  // 双指点击
}
