package com.bridginghelp.app.ui.controller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * 设备列表项
 */
@Composable
fun DeviceListItem(
    deviceName: String,
    deviceId: String,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onConnect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = deviceId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onConnect) {
                Text("连接")
            }
        }
    }
}

/**
 * 控制端主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerHomeScreen(
    viewModel: ControllerHomeViewModel = hiltViewModel(),
    onNavigateToRemote: (sessionId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程协助") },
                actions = {
                    com.bridginghelp.ui.components.ConnectionStatusIndicator(
                        state = when (uiState.sessionState) {
                            is com.bridginghelp.core.model.SessionState.Connected ->
                                com.bridginghelp.core.model.ConnectionState.CONNECTED
                            is com.bridginghelp.core.model.SessionState.Connecting,
                            is com.bridginghelp.core.model.SessionState.Initializing ->
                                com.bridginghelp.core.model.ConnectionState.CONNECTING
                            else ->
                                com.bridginghelp.core.model.ConnectionState.DISCONNECTED
                        }
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.sessionState) {
                is com.bridginghelp.core.model.SessionState.Idle,
                is com.bridginghelp.core.model.SessionState.Disconnected -> {
                    DeviceListContent(
                        availableDevices = uiState.availableDevices,
                        isLoading = uiState.isLoading,
                        onConnectToDevice = { deviceId, deviceName ->
                            viewModel.connectToDevice(deviceId, deviceName)
                        }
                    )
                }
                is com.bridginghelp.core.model.SessionState.Connected -> {
                    ConnectedContent(
                        sessionState = uiState.sessionState
                            as com.bridginghelp.core.model.SessionState.Connected,
                        onDisconnect = { viewModel.disconnect() }
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
 * 设备列表内容
 */
@Composable
private fun DeviceListContent(
    availableDevices: List<com.bridginghelp.core.model.DeviceInfo>,
    isLoading: Boolean,
    onConnectToDevice: (String, String) -> Unit
) {
    if (isLoading) {
        LoadingContent()
    } else if (availableDevices.isEmpty()) {
        EmptyContent()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(
                items = availableDevices,
                key = { it.deviceId }
            ) { device ->
                DeviceListItem(
                    deviceName = device.deviceName,
                    deviceId = device.deviceId,
                    onConnect = { onConnectToDevice(device.deviceId, device.deviceName) }
                )
            }
        }
    }
}

/**
 * 已连接内容
 */
@Composable
private fun ConnectedContent(
    sessionState: com.bridginghelp.core.model.SessionState.Connected,
    onDisconnect: () -> Unit
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
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = sessionState.remoteDevice.deviceName,
            style = MaterialTheme.typography.titleLarge
        )

        com.bridginghelp.ui.components.ConnectionQualityIndicator(
            quality = sessionState.quality
        )

        Button(
            onClick = onDisconnect,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("断开连接")
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

/**
 * 空内容
 */
@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未发现可用设备",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
