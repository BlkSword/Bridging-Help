package com.bridginghelp.injection.injector

import android.accessibilityservice.GestureDescription
import android.view.KeyEvent
import com.bridginghelp.core.common.util.LogWrapper
import com.bridginghelp.core.model.KeyAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 键盘注入器接口
 */
interface KeyboardInjector {
    /**
     * 注入按键事件
     */
    suspend fun injectKeyEvent(
        keyCode: Int,
        action: KeyAction,
        metaState: Int = 0
    ): Boolean

    /**
     * 注入文本
     */
    suspend fun injectText(text: String): Boolean

    /**
     * 注入组合键
     */
    suspend fun injectKeyCombination(
        vararg keyCodes: Int
    ): Boolean
}

/**
 * 键盘注入器实现
 * 注意：AccessibilityService对按键注入的支持有限
 */
@Singleton
class KeyboardInjectorImpl @Inject constructor() : KeyboardInjector {

    companion object {
        private const val TAG = "KeyboardInjector"

        // 需要阻止的系统按键
        private val BLOCKED_KEYS = setOf(
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_CALL,
            KeyEvent.KEYCODE_ENDCALL,
            KeyEvent.KEYCODE_HOME
        )
    }

    override suspend fun injectKeyEvent(
        keyCode: Int,
        action: KeyAction,
        metaState: Int
    ): Boolean {
        // 检查是否为被阻止的按键
        if (keyCode in BLOCKED_KEYS) {
            LogWrapper.w(TAG, "Blocked key event: $keyCode")
            return false
        }

        // AccessibilityService对按键注入的支持非常有限
        // 大多数情况下只能通过模拟输入法来实现
        LogWrapper.d(TAG, "Injecting key event: keyCode=$keyCode, action=$action")

        // 这里返回true表示已处理，实际实现可能需要使用输入法
        return true
    }

    override suspend fun injectText(text: String): Boolean {
        LogWrapper.d(TAG, "Injecting text: $text")

        // 通过输入法注入文本
        // 实际实现需要创建输入法服务
        return true
    }

    override suspend fun injectKeyCombination(vararg keyCodes: Int): Boolean {
        LogWrapper.d(TAG, "Injecting key combination: ${keyCodes.contentToString()}")

        // 模拟组合键
        // 例如 Ctrl+C 需要先按下Ctrl和C，然后依次释放
        keyCodes.forEach { keyCode ->
            injectKeyEvent(keyCode, KeyAction.DOWN)
        }

        // 短暂延迟
        kotlinx.coroutines.delay(50)

        // 反向释放
        keyCodes.reversed().forEach { keyCode ->
            injectKeyEvent(keyCode, KeyAction.UP)
        }

        return true
    }

    /**
     * 检查按键是否被阻止
     */
    fun isKeyBlocked(keyCode: Int): Boolean {
        return keyCode in BLOCKED_KEYS
    }

    /**
     * 添加被阻止的按键
     */
    fun addBlockedKey(keyCode: Int) {
        (BLOCKED_KEYS as MutableSet).add(keyCode)
    }

    /**
     * 移除被阻止的按键
     */
    fun removeBlockedKey(keyCode: Int) {
        (BLOCKED_KEYS as MutableSet).remove(keyCode)
    }
}
