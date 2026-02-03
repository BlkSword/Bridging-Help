package com.bridginghelp.app.ui.controller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bridginghelp.app.ui.qrcode.PairingInfoCard
import com.bridginghelp.app.ui.qrcode.QRCodeScannerButton
import com.bridginghelp.app.ui.qrcode.parsePairingQRCode
import com.bridginghelp.core.model.DiscoveredDevice
import com.bridginghelp.core.model.SessionState
import com.bridginghelp.core.model.LocalDeviceInfo

/**
 * 设备列表项
 */
@Composable
fun DeviceListItem(
    deviceName: String,
    deviceId: String,
    ipAddress: String,
    port: Int,
    isAvailable: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        enabled = isAvailable,
        onClick = onConnect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 设备图标
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (isAvailable)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = null,
                            tint = if (isAvailable)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAvailable)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$ipAddress:$port",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = onConnect, enabled = isAvailable) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
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
    var showPairingDialog by remember { mutableStateOf(false) }

    // 启动/停止设备发现
    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程协助 - 控制端") },
                actions = {
                    IconButton(onClick = { showPairingDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "显示配对码")
                    }
                    IconButton(onClick = { viewModel.refreshDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
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
        },
        floatingActionButton = {
            if (uiState.sessionState !is SessionState.Connected) {
                FloatingActionButton(
                    onClick = { showPairingDialog = true }
                ) {
                    Icon(Icons.Default.Search, contentDescription = "扫码连接")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.sessionState) {
                is SessionState.Idle,
                is SessionState.Disconnected -> {
                    DeviceListContent(
                        discoveredDevices = uiState.discoveredDevices,
                        isScanning = uiState.isScanning,
                        isLoading = uiState.isLoading,
                        localDeviceInfo = uiState.localDeviceInfo,
                        onConnectToDevice = { deviceId ->
                            viewModel.connectToDevice(deviceId, onNavigateToRemote)
                        },
                        onScanResult = { result ->
                            viewModel.handleScanResult(result, onNavigateToRemote)
                        }
                    )
                }
                is SessionState.Connected -> {
                    ConnectedContent(
                        sessionState = uiState.sessionState as SessionState.Connected,
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

    // 配对对话框
    if (showPairingDialog && uiState.localDeviceInfo != null) {
        PairingDialog(
            localDeviceInfo = uiState.localDeviceInfo!!,
            localIpAddress = uiState.localIpAddress,
            onDismiss = { showPairingDialog = false }
        )
    }

    // 扫描结果对话框
    if (uiState.showScanResultDialog && uiState.scanResult != null) {
        ScanResultDialog(
            pairingInfo = uiState.scanResult!!,
            onConfirm = {
                viewModel.confirmScanResult(onNavigateToRemote)
            },
            onDismiss = {
                viewModel.dismissScanResult()
            }
        )
    }
}

/**
 * 设备列表内容
 */
@Composable
private fun DeviceListContent(
    discoveredDevices: Map<String, DiscoveredDevice>,
    isScanning: Boolean,
    isLoading: Boolean,
    localDeviceInfo: com.bridginghelp.core.model.LocalDeviceInfo?,
    onConnectToDevice: (String) -> Unit,
    onScanResult: (String) -> Unit
) {
    if (isLoading) {
        LoadingContent()
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 扫描按钮
            QRCodeScannerButton(
                onScanResult = onScanResult,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 分割线
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 扫描状态提示
            if (isScanning && discoveredDevices.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "正在搜索附近设备...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 发现的设备标题
            if (discoveredDevices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "发现的设备 (${discoveredDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            // 设备列表
            if (discoveredDevices.isEmpty()) {
                EmptyContent(isScanning = isScanning)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(discoveredDevices.values.toList()) { device ->
                        DeviceListItem(
                            deviceName = device.deviceName,
                            deviceId = device.deviceId,
                            ipAddress = device.ipAddress,
                            port = device.port,
                            isAvailable = device.isAvailable,
                            onConnect = { onConnectToDevice(device.deviceId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 已连接内容
 */
@Composable
private fun ConnectedContent(
    sessionState: SessionState.Connected,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        // 连接成功图标
        Surface(
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "连接成功",
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "会话信息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                InfoRow("会话ID", sessionState.sessionId)
            }
        }

        Button(
            onClick = onDisconnect,
            modifier = Modifier.padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
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
private fun EmptyContent(isScanning: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isScanning) "正在搜索附近设备..." else "未发现可用设备",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isScanning) {
                Button(onClick = { /* 触发刷新 */ }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新搜索")
                }
            }
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

/**
 * 配对对话框
 */
@Composable
private fun PairingDialog(
    localDeviceInfo: com.bridginghelp.core.model.LocalDeviceInfo?,
    localIpAddress: String,
    onDismiss: () -> Unit
) {
    if (localDeviceInfo == null) {
        onDismiss()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "设备配对",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
            Text(
                text = "让对方扫描此二维码，或手动输入设备信息进行配对",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 配对信息卡片
            PairingInfoCard(
                deviceId = localDeviceInfo.deviceId,
                deviceName = localDeviceInfo.deviceName,
                ipAddress = localIpAddress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 扫描结果对话框
 */
@Composable
private fun ScanResultDialog(
    pairingInfo: com.bridginghelp.app.ui.qrcode.PairingInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确认连接",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("设备名称", pairingInfo.deviceName)
                InfoRow("设备ID", pairingInfo.deviceId)
                InfoRow("IP地址", pairingInfo.ipAddress)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "确定要连接到此设备吗？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("连接")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
