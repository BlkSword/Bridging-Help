package com.bridginghelp.app.ui.remoteassist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bridginghelp.app.navigation.AppRole

/**
 * 远程协助屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteAssistScreen(
    viewModel: RemoteAssistViewModel = hiltViewModel(),
    onNavigateToController: () -> Unit,
    onNavigateToControlled: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程协助") },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 欢迎标题
            WelcomeHeader()

            // 角色选择卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleCard(
                    title = "控制其他设备",
                    description = "连接到其他设备\n提供远程协助",
                    icon = Icons.Default.Phone,
                    onClick = onNavigateToController,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primaryContainer
                )
                RoleCard(
                    title = "接受远程控制",
                    description = "让其他设备\n控制本设备",
                    icon = Icons.Outlined.Phone,
                    onClick = onNavigateToControlled,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondaryContainer
                )
            }

            Divider()

            // 会话状态
            SessionStatusSection(
                currentRole = uiState.currentRole,
                sessionState = uiState.sessionState,
                onEndSession = { viewModel.endSession() }
            )

            Divider()

            // 连接历史（占位）
            ConnectionHistorySection()
        }
    }
}

/**
 * 欢迎头部
 */
@Composable
private fun WelcomeHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "远程协助",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "选择你的角色开始远程协助",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 角色卡片
 */
@Composable
private fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        colors = CardDefaults.cardColors(
            containerColor = color
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 会话状态区域
 */
@Composable
private fun SessionStatusSection(
    currentRole: AppRole?,
    sessionState: com.bridginghelp.core.model.SessionState,
    onEndSession: () -> Unit
) {
    if (currentRole == null) {
        // 未选择角色时显示提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "选择角色开始使用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // 已选择角色时显示状态
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "当前状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                RoleStatusItem(
                    role = currentRole,
                    sessionState = sessionState
                )

                if (sessionState is com.bridginghelp.core.model.SessionState.Connected) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Button(
                        onClick = onEndSession,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("结束会话")
                    }
                }
            }
        }
    }
}

/**
 * 角色状态项
 */
@Composable
private fun RoleStatusItem(
    role: AppRole,
    sessionState: com.bridginghelp.core.model.SessionState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (role) {
                    AppRole.CONTROLLER -> Icons.Default.Phone
                    AppRole.CONTROLLED -> Icons.Outlined.Phone
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = when (role) {
                        AppRole.CONTROLLER -> "控制端"
                        AppRole.CONTROLLED -> "受控端"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = getSessionStateText(sessionState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StatusIndicator(sessionState)
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(sessionState: com.bridginghelp.core.model.SessionState) {
    val (color, text) = when (sessionState) {
        is com.bridginghelp.core.model.SessionState.Connected ->
            MaterialTheme.colorScheme.primary to "已连接"
        is com.bridginghelp.core.model.SessionState.Connecting,
        is com.bridginghelp.core.model.SessionState.Initializing ->
            MaterialTheme.colorScheme.tertiary to "连接中"
        else ->
            MaterialTheme.colorScheme.secondary to "空闲"
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 连接历史区域（占位）
 */
@Composable
private fun ConnectionHistorySection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "最近连接",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "暂无连接记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// 辅助函数

private fun getSessionStateText(state: com.bridginghelp.core.model.SessionState): String {
    return when (state) {
        is com.bridginghelp.core.model.SessionState.Idle -> "空闲"
        is com.bridginghelp.core.model.SessionState.Initializing -> "初始化中"
        is com.bridginghelp.core.model.SessionState.Connecting -> "连接中..."
        is com.bridginghelp.core.model.SessionState.Connected -> "已连接"
        is com.bridginghelp.core.model.SessionState.Disconnected -> "已断开"
        is com.bridginghelp.core.model.SessionState.Error -> "错误"
        else -> "未知"
    }
}
