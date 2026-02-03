package com.bridginghelp.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限类型
 */
enum class AppPermission(val permissionString: String, val minSdk: Int = 0) {
    // 网络相关
    INTERNET(Manifest.permission.INTERNET),
    ACCESS_NETWORK_STATE(Manifest.permission.ACCESS_NETWORK_STATE),

    // 前台服务
    FOREGROUND_SERVICE(Manifest.permission.FOREGROUND_SERVICE),
    FOREGROUND_SERVICE_MEDIA_PROJECTION(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
        } else {
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
        },
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    ),

    // 通知
    POST_NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            "android.permission.POST_NOTIFICATIONS"
        },
        Build.VERSION_CODES.TIRAMISU
    ),

    // 唤醒锁
    WAKE_LOCK(Manifest.permission.WAKE_LOCK),

    // 屏幕捕获（特殊权限，不在此处理）
    // MEDIA_PROJECTION - 需要通过MediaProjectionManager请求

    // 无障碍服务（特殊权限，不在此处理）
    // ACCESSIBILITY_SERVICE - 需要用户在设置中手动启用

    // 存储和文件（可选）
    READ_EXTERNAL_STORAGE(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Build.VERSION_CODES.TIRAMISU
    ),
    WRITE_EXTERNAL_STORAGE(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Build.VERSION_CODES.TIRAMISU
    );

    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= minSdk

    companion object {
        fun getAllRequiredPermissions(): List<AppPermission> {
            return values().filter { it.isRequired }
        }

        fun getRuntimePermissions(): List<AppPermission> {
            return values().filter {
                it.isRequired && it.permissionString.startsWith("android.permission.")
            }
        }
    }
}

/**
 * 权限状态
 */
sealed class PermissionStatus {
    data object Granted : PermissionStatus()
    data object Denied : PermissionStatus()
    data class PermanentlyDenied(val shouldShowRationale: Boolean = false) : PermissionStatus()
}

/**
 * 权限结果
 */
sealed class PermissionResult {
    data object Granted : PermissionResult()
    data class Denied(val deniedPermissions: List<AppPermission>) : PermissionResult()
    data class PermanentlyDenied(val deniedPermissions: List<AppPermission>) : PermissionResult()
}

/**
 * 权限管理器接口
 */
interface PermissionManager {
    /**
     * 检查权限是否已授予
     */
    fun checkPermission(permission: AppPermission): Boolean

    /**
     * 检查多个权限是否已授予
     */
    fun checkPermissions(permissions: List<AppPermission>): Map<AppPermission, Boolean>

    /**
     * 检查所有必需权限是否已授予
     */
    fun hasAllRequiredPermissions(): Boolean

    /**
     * 获取缺失的权限
     */
    fun getMissingPermissions(): List<AppPermission>

    /**
     * 权限状态流
     */
    val permissionStates: Flow<Map<AppPermission, PermissionStatus>>

    /**
     * 刷新权限状态
     */
    fun refreshPermissionStates()
}

/**
 * 权限管理器实现
 */
@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {

    private val _permissionStates = MutableStateFlow<Map<AppPermission, PermissionStatus>>(emptyMap())
    override val permissionStates: Flow<Map<AppPermission, PermissionStatus>> = _permissionStates.asStateFlow()

    init {
        refreshPermissionStates()
    }

    override fun checkPermission(permission: AppPermission): Boolean {
        return if (permission.isRequired) {
            ContextCompat.checkSelfPermission(
                context,
                permission.permissionString
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun checkPermissions(permissions: List<AppPermission>): Map<AppPermission, Boolean> {
        return permissions.associateWith { checkPermission(it) }
    }

    override fun hasAllRequiredPermissions(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    override fun getMissingPermissions(): List<AppPermission> {
        return AppPermission.getRuntimePermissions()
            .filter { !checkPermission(it) }
    }

    override fun refreshPermissionStates() {
        val states = AppPermission.getRuntimePermissions().associateWith { permission ->
            if (checkPermission(permission)) {
                PermissionStatus.Granted
            } else {
                PermissionStatus.Denied
            }
        }
        _permissionStates.value = states
    }
}
