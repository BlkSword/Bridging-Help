package com.bridginghelp.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bridginghelp.app.ui.controlled.ControlledHomeScreen
import com.bridginghelp.app.ui.controller.ControllerHomeScreen
import com.bridginghelp.app.ui.role.RoleSelectionScreen

/**
 * 主导航图
 */
@Composable
fun BridgingHelpNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = Destination.RoleSelection.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 角色选择
        composable(Destination.RoleSelection.route) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    val destination = when (role) {
                        AppRole.CONTROLLER -> Destination.ControllerHome.route
                        AppRole.CONTROLLED -> Destination.ControlledHome.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Destination.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        // 控制端主页
        composable(Destination.ControllerHome.route) {
            ControllerHomeScreen(
                onNavigateToRemote = { sessionId ->
                    // 导航到远程控制界面（待实现）
                }
            )
        }

        // 控制端远程控制
        composable(
            route = Destination.ControllerRemote.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            // TODO: 实现远程控制界面
            ControllerRemoteScreenPlaceholder()
        }

        // 受控端主页
        composable(Destination.ControlledHome.route) {
            ControlledHomeScreen()
        }
    }
}

// 占位符屏幕，待实现完整功能

@Composable
private fun ControllerRemoteScreenPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.Text("远程控制界面 - 开发中")
    }
}
