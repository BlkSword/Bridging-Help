package com.bridginghelp.injection.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.RemoteEvent
import com.bridginghelp.injection.injector.TouchInjector
import com.bridginghelp.injection.injector.KeyboardInjector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 远程输入无障碍服务
 * 负责接收远程输入事件并注入到系统
 */
@AndroidEntryPoint
class RemoteInputService : AccessibilityService() {

    @Inject
    lateinit var touchInjector: TouchInjector

    @Inject
    lateinit var keyboardInjector: KeyboardInjector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _eventFlow = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 100)
    val eventFlow: SharedFlow<RemoteEvent> = _eventFlow.asSharedFlow()

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Disabled)
    val serviceState: SharedFlow<ServiceState> = _serviceState.asStateFlow()

    companion object {
        const val TAG = "RemoteInputService"
        const val ACTION_START = "com.bridginghelp.injection.action.START"
        const val ACTION_STOP = "com.bridginghelp.injection.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        LogWrapper.d(TAG, "RemoteInputService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogWrapper.d(TAG, "RemoteInputService connected")
        _serviceState.value = ServiceState.Connected

        // 启动事件处理
        serviceScope.launch {
            eventFlow.collect { event ->
                handleRemoteEvent(event)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                LogWrapper.d(TAG, "Starting input injection")
                _serviceState.value = ServiceState.Active
            }
            ACTION_STOP -> {
                LogWrapper.d(TAG, "Stopping input injection")
                _serviceState.value = ServiceState.Connected
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理事件，只需要注入能力
    }

    override fun onInterrupt() {
        LogWrapper.w(TAG, "RemoteInputService interrupted")
        _serviceState.value = ServiceState.Interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        LogWrapper.d(TAG, "RemoteInputService destroyed")
        serviceScope.cancel()
    }

    /**
     * 处理远程事件
     */
    private fun handleRemoteEvent(event: RemoteEvent) {
        if (_serviceState.value !is ServiceState.Active) {
            LogWrapper.w(TAG, "Service not active, ignoring event")
            return
        }

        when (event) {
            is RemoteEvent.TouchEvent -> {
                handleTouchEvent(event)
            }
            is RemoteEvent.KeyEvent -> {
                handleKeyEvent(event)
            }
            is RemoteEvent.ScrollEvent -> {
                handleScrollEvent(event)
            }
            is RemoteEvent.GestureEvent -> {
                handleGestureEvent(event)
            }
        }
    }

    /**
     * 处理触摸事件
     */
    private fun handleTouchEvent(event: RemoteEvent.TouchEvent) {
        serviceScope.launch {
            val result = touchInjector.injectTouchEvent(
                x = event.x,
                y = event.y,
                action = event.action,
                pointerId = event.pointerId
            )
            if (!result) {
                LogWrapper.w(TAG, "Failed to inject touch event at (${event.x}, ${event.y})")
            }
        }
    }

    /**
     * 处理键盘事件
     */
    private fun handleKeyEvent(event: RemoteEvent.KeyEvent) {
        serviceScope.launch {
            val result = keyboardInjector.injectKeyEvent(
                keyCode = event.keyCode,
                action = event.action,
                metaState = event.metaState
            )
            if (!result) {
                LogWrapper.w(TAG, "Failed to inject key event: ${event.keyCode}")
            }
        }
    }

    /**
     * 处理滚动事件
     */
    private fun handleScrollEvent(event: RemoteEvent.ScrollEvent) {
        serviceScope.launch {
            val result = touchInjector.injectScrollEvent(
                deltaX = event.deltaX,
                deltaY = event.deltaY
            )
            if (!result) {
                LogWrapper.w(TAG, "Failed to inject scroll event")
            }
        }
    }

    /**
     * 处理手势事件
     */
    private fun handleGestureEvent(event: RemoteEvent.GestureEvent) {
        serviceScope.launch {
            val result = when (event.type) {
                com.bridginghelp.core.model.GestureType.PINCH -> {
                    // 捏合缩放手势
                    touchInjector.injectPinchGesture(
                        scaleFactor = event.data["scaleFactor"] ?: 1f,
                        centerX = event.data["centerX"] ?: 0f,
                        centerY = event.data["centerY"] ?: 0f
                    )
                }
                com.bridginghelp.core.model.GestureType.ROTATION -> {
                    // 旋转手势
                    touchInjector.injectRotationGesture(
                        rotationDegrees = event.data["rotation"] ?: 0f,
                        centerX = event.data["centerX"] ?: 0f,
                        centerY = event.data["centerY"] ?: 0f
                    )
                }
                com.bridginghelp.core.model.GestureType.LONG_PRESS -> {
                    // 长按手势
                    touchInjector.injectLongPress(
                        x = event.data["x"] ?: 0f,
                        y = event.data["y"] ?: 0f
                    )
                }
                com.bridginghelp.core.model.GestureType.DOUBLE_TAP -> {
                    // 双击手势
                    touchInjector.injectDoubleTap(
                        x = event.data["x"] ?: 0f,
                        y = event.data["y"] ?: 0f
                    )
                }
                com.bridginghelp.core.model.GestureType.TWO_FINGER_TAP -> {
                    // 双指点击手势
                    touchInjector.injectTwoFingerTap(
                        x = event.data["x"] ?: 0f,
                        y = event.data["y"] ?: 0f
                    )
                }
            }

            if (!result) {
                LogWrapper.w(TAG, "Failed to inject gesture: ${event.type}")
            }
        }
    }

    /**
     * 服务状态
     */
    sealed class ServiceState {
        data object Disabled : ServiceState()
        data object Connected : ServiceState()
        data object Active : ServiceState()
        data object Interrupted : ServiceState()
    }
}
