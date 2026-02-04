package com.bridginghelp.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bridginghelp.core.permissions.AccessibilityPermissionHandler

/**
 * 应用设置屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 权限管理
            SettingsSection("权限管理") {
                SettingsItem(
                    icon = Icons.Default.Phone,
                    title = "屏幕录制权限",
                    description = if (uiState.mediaProjectionGranted) "已授予" else "未授予",
                    onClick = { /* 跳转到权限请求 */ }
                )

                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "无障碍服务",
                    description = if (uiState.accessibilityEnabled) "已启用" else "未启用",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = "通知权限",
                        description = if (uiState.notificationGranted) "已授予" else "未授予",
                        onClick = { /* 跳转到通知设置 */ }
                    )
                }
            }

            HorizontalDivider()

            // 通用设置
            SettingsSection("通用设置") {
                SettingsItem(
                    icon = Icons.Default.Phone,
                    title = "语言",
                    description = "跟随系统",
                    onClick = { /* TODO */ }
                )

                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "深色模式",
                    description = "跟随系统",
                    onClick = { /* TODO */ }
                )
            }

            HorizontalDivider()

            // 关于
            SettingsSection("关于") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "应用信息",
                    description = "版本 ${uiState.appVersion}",
                    onClick = { /* 显示关于对话框 */ }
                )

                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "用户协议",
                    description = "",
                    onClick = { /* TODO */ }
                )

                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "隐私政策",
                    description = "",
                    onClick = { /* TODO */ }
                )
            }

            HorizontalDivider()

            // 系统设置
            SettingsSection("系统") {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "系统设置",
                    description = "打开系统设置页面",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "应用设置",
                    description = "打开本应用在系统中的设置",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 设置区域
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/**
 * 设置项
 */
@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
