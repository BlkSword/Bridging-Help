package com.bridginghelp.app.navigation

/**
 * 导航目标定义
 */
sealed class Destination(val route: String) {
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
}

/**
 * 应用角色
 */
enum class AppRole {
    CONTROLLER,    // 控制端
    CONTROLLED     // 受控端
}
