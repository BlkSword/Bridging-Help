package com.bridginghelp.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bridginghelp.app.ui.controller.ControllerHomeScreen
import com.bridginghelp.app.ui.controller.RemoteControlScreen
import com.bridginghelp.app.ui.controlled.ControlledHomeScreen
import com.bridginghelp.app.ui.mydevice.MyDeviceScreen
import com.bridginghelp.app.ui.profile.ProfileScreen
import com.bridginghelp.app.ui.remoteassist.RemoteAssistScreen

/**
 * 主导航图（带底部导航栏）
 */
@Composable
fun BridgingHelpAppNavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val items = BottomNavItem.items

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.route
                    } == true

                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                // 避免重复导航到同一目的地
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.RemoteAssist.route,
            modifier = Modifier.padding(padding)
        ) {
            // 我的设备
            composable(Destination.MyDevice.route) {
                MyDeviceScreen()
            }

            // 远程协助（主页）
            composable(Destination.RemoteAssist.route) {
                RemoteAssistScreen(
                    onNavigateToController = {
                        navController.navigate(Destination.ControllerHome.route)
                    },
                    onNavigateToControlled = {
                        navController.navigate(Destination.ControlledHome.route)
                    }
                )
            }

            // 我的
            composable(Destination.Profile.route) {
                ProfileScreen()
            }

            // 控制端主页
            composable(Destination.ControllerHome.route) {
                ControllerHomeScreen(
                    onNavigateToRemote = { sessionId ->
                        navController.navigate("${Destination.ControllerRemote.route}/$sessionId")
                    }
                )
            }

            // 控制端远程控制
            composable(
                route = Destination.ControllerRemote.route,
                arguments = listOf(
                    navArgument("sessionId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                RemoteControlScreen(
                    sessionId = sessionId,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            // 受控端主页
            composable(Destination.ControlledHome.route) {
                ControlledHomeScreen()
            }
        }
    }
}

/**
 * 旧版导航图（不含底部导航栏，保留用于兼容）
 */
@Composable
fun BridgingHelpNavGraph(
    modifier: Modifier = Modifier,
    navController: androidx.navigation.NavHostController,
    startDestination: String = Destination.RoleSelection.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 角色选择（已弃用，整合到远程协助页面）
        composable(Destination.RoleSelection.route) {
            // 直接导航到远程协助页面
            navController.navigate(Destination.RemoteAssist.route) {
                popUpTo(Destination.RoleSelection.route) { inclusive = true }
            }
        }

        // 控制端主页
        composable(Destination.ControllerHome.route) {
            ControllerHomeScreen(
                onNavigateToRemote = { sessionId ->
                    navController.navigate("${Destination.ControllerRemote.route}/$sessionId")
                }
            )
        }

        // 控制端远程控制
        composable(
            route = Destination.ControllerRemote.route,
            arguments = listOf(navArgument("sessionId") {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            RemoteControlScreen(
                sessionId = sessionId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 受控端主页
        composable(Destination.ControlledHome.route) {
            ControlledHomeScreen()
        }
    }
}
