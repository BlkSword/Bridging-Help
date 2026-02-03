package com.bridginghelp.app.ui.controlled

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.TopAppBar

/**
 * 受控端主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledHomeScreen(
    viewModel: ControlledHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("被控制设备") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState.sessionState) {
                is com.bridginghelp.core.model.SessionState.Idle,
                is com.bridginghelp.core.model.SessionState.Disconnected -> {
                    IdleContent(
                        isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                        onStartSession = { viewModel.startSession() }
                    )
                }
                is com.bridginghelp.core.model.SessionState.Connecting,
                is com.bridginghelp.core.model.SessionState.Initializing -> {
                    ConnectingContent()
                }
                is com.bridginghelp.core.model.SessionState.Connected -> {
                    ConnectedContent(
                        sessionState = state,
                        onEndSession = { viewModel.endSession() }
                    )
                }
                else -> {
                    LoadingContent()
                }
            }

            // 错误提示
            if (uiState.errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("错误") },
                    text = { Text(uiState.errorMessage ?: "") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("确定")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 空闲状态内容
 */
@Composable
private fun IdleContent(
    isAccessibilityEnabled: Boolean,
    onStartSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "等待远程连接",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (!isAccessibilityEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "无障碍服务未启用\n请在设置中启用",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
        }

        Button(
            onClick = onStartSession,
            enabled = isAccessibilityEnabled
        ) {
            Text("开始会话")
        }

        Text(
            text = "启用后，控制端可以连接到本设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 连接中内容
 */
@Composable
private fun ConnectingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator()

        Text(
            text = "正在连接...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 已连接内容
 */
@Composable
private fun ConnectedContent(
    sessionState: com.bridginghelp.core.model.SessionState.Connected,
    onEndSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "已连接",
            style = MaterialTheme.typography.headlineMedium,
            color = com.bridginghelp.ui.theme.StatusConnected
        )

        Text(
            text = sessionState.remoteDevice.deviceName,
            style = MaterialTheme.typography.titleLarge
        )

        com.bridginghelp.ui.components.ConnectionQualityIndicator(
            quality = sessionState.quality
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Text(
                text = "您的屏幕正在被控制",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onEndSession,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("结束会话")
        }
    }
}

/**
 * 加载内容
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
