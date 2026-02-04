package com.bridginghelp.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone

/**
 * 导航目标定义
 */
sealed class Destination(val route: String) {
    /**
     * 开屏页面
     */
    data object Splash : Destination("splash")

    /**
     * 主页面（底部导航栏）
     */
    data object MyDevice : Destination("my_device")
    data object RemoteAssist : Destination("remote_assist")
    data object Profile : Destination("profile")

    /**
     * 统一的主页
     */
    data object Home : Destination("home")

    /**
     * 角色选择
     */
    data object RoleSelection : Destination("role_selection")

    /**
     * 控制端相关
     */
    data object Controller : Destination("controller")
    data object ControllerHome : Destination("controller/home")
    data object ControllerRemote : Destination("controller/remote/{sessionId}")

    /**
     * 受控端相关
     */
    data object Controlled : Destination("controlled")
    data object ControlledPermissionRequest : Destination("controlled/permissions")
    data object ControlledHome : Destination("controlled/home")

    /**
     * 设置
     */
    data object Settings : Destination("settings")

    /**
     * 帮助
     */
    data object Help : Destination("help")

    /**
     * 反馈
     */
    data object Feedback : Destination("feedback")
}

/**
 * 底部导航项
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object MyDevice : BottomNavItem(
        route = Destination.MyDevice.route,
        title = "我的设备",
        icon = Icons.Filled.Phone
    )

    data object RemoteAssist : BottomNavItem(
        route = Destination.RemoteAssist.route,
        title = "远程协助",
        icon = Icons.Filled.Phone
    )

    data object Profile : BottomNavItem(
        route = Destination.Profile.route,
        title = "我的",
        icon = Icons.Filled.Person
    )

    companion object {
        val items = listOf(MyDevice, RemoteAssist, Profile)
    }
}

/**
 * 应用角色
 */
enum class AppRole {
    CONTROLLER,    // 控制端
    CONTROLLED     // 受控端
}
