package com.bridginghelp.app.ui.controlled

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bridginghelp.app.ui.qrcode.PairingInfoCard

/**
 * 受控端主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledHomeScreen(
    viewModel: ControlledHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // MediaProjection权限请求
    val screenCapturePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { intent ->
                viewModel.startScreenSharing(intent)
            }
        }
    }

    // 请求屏幕捕获权限
    val context = LocalContext.current
    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    // 请求屏幕捕获权限
    val requestScreenCapture = {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCapturePermission.launch(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("被控制设备") },
                actions = {
                    IconButton(onClick = { viewModel.showPairingDialog() }) {
                        Icon(Icons.Default.Search, contentDescription = "显示配对码")
                    }
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
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
                        isScreenSharing = uiState.isScreenSharing,
                        isClipboardSyncEnabled = uiState.isClipboardSyncEnabled,
                        onStartSession = { viewModel.startSession() },
                        onRequestScreenCapture = { requestScreenCapture() },
                        onToggleClipboardSync = { viewModel.toggleClipboardSync() }
                    )
                }
                is com.bridginghelp.core.model.SessionState.Connecting,
                is com.bridginghelp.core.model.SessionState.Initializing -> {
                    ConnectingContent()
                }
                is com.bridginghelp.core.model.SessionState.Connected -> {
                    ConnectedContent(
                        sessionState = state,
                        isScreenSharing = uiState.isScreenSharing,
                        isClipboardSyncEnabled = uiState.isClipboardSyncEnabled,
                        onEndSession = { viewModel.endSession() },
                        onToggleScreenSharing = {
                            if (uiState.isScreenSharing) {
                                viewModel.stopScreenSharing()
                            } else {
                                requestScreenCapture()
                            }
                        },
                        onToggleClipboardSync = { viewModel.toggleClipboardSync() }
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
    if (uiState.showPairingDialog && uiState.localDeviceInfo != null) {
        PairingDialog(
            localDeviceInfo = uiState.localDeviceInfo!!,
            localIpAddress = uiState.localIpAddress,
            onDismiss = { viewModel.hidePairingDialog() }
        )
    }
}

/**
 * 空闲状态内容
 */
@Composable
private fun IdleContent(
    isAccessibilityEnabled: Boolean,
    isScreenSharing: Boolean,
    isClipboardSyncEnabled: Boolean,
    onStartSession: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onToggleClipboardSync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "被控制设备",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "等待控制端连接",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 无障碍服务状态
        item {
            StatusCard(
                title = "无障碍服务",
                description = "需要启用无障碍服务以支持远程控制",
                isEnabled = isAccessibilityEnabled,
                icon = Icons.Outlined.Settings
            )
        }

        // 屏幕共享开关
        item {
            SwitchCard(
                title = "屏幕共享",
                description = "允许控制端查看您的屏幕",
                isChecked = isScreenSharing,
                onCheckedChange = { isChecked ->
                    if (isChecked) onRequestScreenCapture()
                    // 停止由ConnectedContent处理
                },
                icon = Icons.Default.Favorite
            )
        }

        // 剪贴板同步开关
        item {
            SwitchCard(
                title = "剪贴板同步",
                description = "自动同步剪贴板内容",
                isChecked = isClipboardSyncEnabled,
                onCheckedChange = { onToggleClipboardSync() },
                icon = Icons.Default.Add
            )
        }

        // 开始会话按钮
        item {
            Button(
                onClick = onStartSession,
                modifier = Modifier.fillMaxWidth(),
                enabled = isAccessibilityEnabled
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始等待连接")
            }
        }
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
            text = "正在建立连接...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "请确保控制端已启动扫描",
            style = MaterialTheme.typography.bodySmall,
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
    isScreenSharing: Boolean,
    isClipboardSyncEnabled: Boolean,
    onEndSession: () -> Unit,
    onToggleScreenSharing: () -> Unit,
    onToggleClipboardSync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 连接状态卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = com.bridginghelp.ui.theme.StatusConnected
                    )
                    Text(
                        text = "已连接",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sessionState.remoteDevice.deviceName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    com.bridginghelp.ui.components.ConnectionQualityIndicator(
                        quality = sessionState.quality
                    )
                }
            }
        }

        // 屏幕共享开关
        item {
            SwitchCard(
                title = "屏幕共享",
                description = if (isScreenSharing) "正在共享屏幕" else "允许控制端查看您的屏幕",
                isChecked = isScreenSharing,
                onCheckedChange = { onToggleScreenSharing() },
                icon = Icons.Default.Favorite
            )
        }

        // 剪贴板同步开关
        item {
            SwitchCard(
                title = "剪贴板同步",
                description = if (isClipboardSyncEnabled) "剪贴板同步已启用" else "自动同步剪贴板内容",
                isChecked = isClipboardSyncEnabled,
                onCheckedChange = { onToggleClipboardSync() },
                icon = Icons.Default.Add
            )
        }

        // 警告卡片
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "您的屏幕正在被控制",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "控制端可以查看您的屏幕并进行操作",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // 结束会话按钮
        item {
            Button(
                onClick = onEndSession,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("结束会话")
            }
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
 * 状态卡片
 */
@Composable
private fun StatusCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isEnabled)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }

            Text(
                text = if (isEnabled) "已启用" else "未启用",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * 开关卡片
 */
@Composable
private fun SwitchCard(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!isChecked) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 配对对话框
 */
@Composable
private fun PairingDialog(
    localDeviceInfo: com.bridginghelp.core.model.LocalDeviceInfo,
    localIpAddress: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "设备配对",
                fontWeight = FontWeight.Bold
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
