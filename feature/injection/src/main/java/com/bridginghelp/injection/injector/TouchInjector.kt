package com.bridginghelp.injection.injector

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Bundle
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.TouchAction
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 触摸注入器接口
 */
interface TouchInjector {
    /**
     * 注入触摸事件
     */
    suspend fun injectTouchEvent(
        x: Float,
        y: Float,
        action: TouchAction,
        pointerId: Int = 0
    ): Boolean

    /**
     * 注入滚动事件
     */
    suspend fun injectScrollEvent(
        deltaX: Float,
        deltaY: Float
    ): Boolean

    /**
     * 注入捏合手势
     */
    suspend fun injectPinchGesture(
        scaleFactor: Float,
        centerX: Float,
        centerY: Float
    ): Boolean

    /**
     * 注入旋转手势
     */
    suspend fun injectRotationGesture(
        rotationDegrees: Float,
        centerX: Float,
        centerY: Float
    ): Boolean

    /**
     * 注入长按手势
     */
    suspend fun injectLongPress(
        x: Float,
        y: Float
    ): Boolean

    /**
     * 注入双击手势
     */
    suspend fun injectDoubleTap(
        x: Float,
        y: Float
    ): Boolean

    /**
     * 注入双指点击手势
     */
    suspend fun injectTwoFingerTap(
        x: Float,
        y: Float
    ): Boolean
}

/**
 * 触摸注入器实现
 */
@Singleton
class TouchInjectorImpl @Inject constructor() : TouchInjector {

    companion object {
        private const val TAG = "TouchInjector"
        private const val GESTURE_DURATION_MS = 10L
        private const val LONG_PRESS_DURATION_MS = 500L
    }

    // 临时存储AccessibilityService引用
    // 实际使用时需要通过其他方式获取service实例
    private var service: android.accessibilityservice.AccessibilityService? = null

    fun setService(service: android.accessibilityservice.AccessibilityService) {
        this.service = service
    }

    override suspend fun injectTouchEvent(
        x: Float,
        y: Float,
        action: TouchAction,
        pointerId: Int
    ): Boolean {
        val svc = service ?: return false

        return try {
            val path = Path().apply {
                moveTo(x, y)
            }

            val gestureBuilder = when (action) {
                TouchAction.DOWN, TouchAction.POINTER_DOWN -> {
                    GestureDescription.Builder().apply {
                        addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS))
                    }
                }
                TouchAction.UP, TouchAction.POINTER_UP -> {
                    // UP不需要单独处理，手势会自动结束
                    return true
                }
                TouchAction.MOVE -> {
                    GestureDescription.Builder().apply {
                        addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS))
                    }
                }
                else -> return false
            }

            val gesture = gestureBuilder.build()
            val result = svc.dispatchGesture(gesture, null, null)

            if (!result) {
                LogWrapper.w(TAG, "Failed to dispatch touch gesture")
            }

            result
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error injecting touch event", e)
            false
        }
    }

    override suspend fun injectScrollEvent(deltaX: Float, deltaY: Float): Boolean {
        // 滚动可以通过多次触摸移动事件模拟
        val steps = 5
        val stepX = deltaX / steps
        val stepY = deltaY / steps

        for (i in 0 until steps) {
            val x = stepX * i
            val y = stepY * i
            injectTouchEvent(x, y, TouchAction.MOVE)
            delay(5)
        }

        return true
    }

    override suspend fun injectPinchGesture(
        scaleFactor: Float,
        centerX: Float,
        centerY: Float
    ): Boolean {
        val svc = service ?: return false

        return try {
            // 创建双指捏合手势
            val initialDistance = 100f
            val finalDistance = initialDistance * scaleFactor

            val path1 = Path().apply {
                moveTo(centerX - initialDistance / 2, centerY)
                lineTo(centerX - finalDistance / 2, centerY)
            }

            val path2 = Path().apply {
                moveTo(centerX + initialDistance / 2, centerY)
                lineTo(centerX + finalDistance / 2, centerY)
            }

            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 300)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, 300)

            val gesture = GestureDescription.Builder().apply {
                addStroke(stroke1)
                addStroke(stroke2)
            }.build()

            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error injecting pinch gesture", e)
            false
        }
    }

    override suspend fun injectRotationGesture(
        rotationDegrees: Float,
        centerX: Float,
        centerY: Float
    ): Boolean {
        val svc = service ?: return false

        return try {
            val radius = 50f
            val startAngle1 = 0f
            val endAngle1 = Math.toRadians(rotationDegrees.toDouble()).toFloat()
            val startAngle2 = Math.PI.toFloat()
            val endAngle2 = startAngle2 + endAngle1

            val path1 = Path().apply {
                val startX = centerX + radius * kotlin.math.cos(startAngle1.toDouble()).toFloat()
                val startY = centerY + radius * kotlin.math.sin(startAngle1.toDouble()).toFloat()
                val endX = centerX + radius * kotlin.math.cos(endAngle1.toDouble()).toFloat()
                val endY = centerY + radius * kotlin.math.sin(endAngle1.toDouble()).toFloat()
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            val path2 = Path().apply {
                val startX = centerX + radius * kotlin.math.cos(startAngle2.toDouble()).toFloat()
                val startY = centerY + radius * kotlin.math.sin(startAngle2.toDouble()).toFloat()
                val endX = centerX + radius * kotlin.math.cos(endAngle2.toDouble()).toFloat()
                val endY = centerY + radius * kotlin.math.sin(endAngle2.toDouble()).toFloat()
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 300)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, 300)

            val gesture = GestureDescription.Builder().apply {
                addStroke(stroke1)
                addStroke(stroke2)
            }.build()

            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error injecting rotation gesture", e)
            false
        }
    }

    override suspend fun injectLongPress(x: Float, y: Float): Boolean {
        val svc = service ?: return false

        return try {
            val path = Path().apply {
                moveTo(x, y)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, LONG_PRESS_DURATION_MS)
            val gesture = GestureDescription.Builder().apply {
                addStroke(stroke)
            }.build()

            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error injecting long press", e)
            false
        }
    }

    override suspend fun injectDoubleTap(x: Float, y: Float): Boolean {
        // 第一次点击
        injectTouchEvent(x, y, TouchAction.DOWN)
        delay(100)
        injectTouchEvent(x, y, TouchAction.UP)
        delay(100)

        // 第二次点击
        injectTouchEvent(x, y, TouchAction.DOWN)
        delay(100)
        injectTouchEvent(x, y, TouchAction.UP)

        return true
    }

    override suspend fun injectTwoFingerTap(x: Float, y: Float): Boolean {
        val svc = service ?: return false

        return try {
            val offset = 20f
            val path1 = Path().apply {
                moveTo(x - offset, y)
            }
            val path2 = Path().apply {
                moveTo(x + offset, y)
            }

            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 50)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, 50)

            val gesture = GestureDescription.Builder().apply {
                addStroke(stroke1)
                addStroke(stroke2)
            }.build()

            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            LogWrapper.e(TAG, "Error injecting two finger tap", e)
            false
        }
    }
}
