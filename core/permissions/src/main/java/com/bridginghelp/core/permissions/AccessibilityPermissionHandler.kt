package com.bridginghelp.core.permissions

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 无障碍服务权限状态
 */
sealed class AccessibilityPermissionState {
    data object NotEnabled : AccessibilityPermissionState()
    data object Enabled : AccessibilityPermissionState()
    data object Unknown : AccessibilityPermissionState()
}

/**
 * 无障碍服务权限处理器
 */
interface AccessibilityPermissionHandler {
    val state: kotlinx.coroutines.flow.StateFlow<AccessibilityPermissionState>

    /**
     * 检查无障碍服务是否已启用
     */
    fun isServiceEnabled(): Boolean

    /**
     * 检查特定无障碍服务是否已启用
     */
    fun isServiceEnabled(serviceComponentName: String): Boolean

    /**
     * 获取无障碍设置 Intent
     */
    fun getAccessibilitySettingsIntent(): android.content.Intent

    /**
     * 刷新状态
     */
    fun refreshState()
}

/**
 * 无障碍服务权限处理器实现
 */
@Singleton
class AccessibilityPermissionHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AccessibilityPermissionHandler {

    companion object {
        private const val ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val TEXT_SERVICES = "text_services"
    }

    private val _state = MutableStateFlow<AccessibilityPermissionState>(
        AccessibilityPermissionState.Unknown
    )
    override val state = _state.asStateFlow()

    override fun isServiceEnabled(): Boolean {
        // 默认实现：检查包名下是否有任何无障碍服务启用
        val packageName = context.packageName
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            ACCESSIBILITY_ENABLED
        )

        if (enabledServices.isNullOrEmpty()) {
            return false
        }

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.startsWith("$packageName/")) {
                return true
            }
        }

        return false
    }

    override fun isServiceEnabled(serviceComponentName: String): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            ACCESSIBILITY_ENABLED
        )

        if (enabledServices.isNullOrEmpty()) {
            return false
        }

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(serviceComponentName, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    override fun getAccessibilitySettingsIntent(): android.content.Intent {
        return android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    override fun refreshState() {
        _state.value = if (isServiceEnabled()) {
            AccessibilityPermissionState.Enabled
        } else {
            AccessibilityPermissionState.NotEnabled
        }
    }

    init {
        refreshState()
    }
}

/**
 * 无障碍服务组件名称工具
 */
object AccessibilityServiceUtils {
    /**
     * 构建无障碍服务组件名称
     */
    fun buildComponentName(
        packageName: String,
        serviceName: String
    ): String {
        return "$packageName/$serviceName"
    }

    /**
     * 解析组件名称
     */
    fun parseComponentName(componentName: String): Pair<String, String>? {
        val parts = componentName.split("/")
        return if (parts.size == 2) {
            Pair(parts[0], parts[1])
        } else {
            null
        }
    }
}
