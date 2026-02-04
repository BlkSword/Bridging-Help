package com.bridginghelp.app.ui.controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bridginghelp.core.model.ConnectionQuality
import com.bridginghelp.core.model.SessionState

/**
 * 远程控制屏幕
 * 根据用户需求重新设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    sessionId: String,
    viewModel: RemoteControlViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showBrightnessDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // 处理生命周期
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                Lifecycle.Event.ON_DESTROY -> viewModel.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 初始化会话
    LaunchedEffect(sessionId) {
        viewModel.initializeSession(sessionId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部状态栏
            TopStatusBar(
                quality = uiState.connectionQuality,
                latency = uiState.latency,
                onBack = onBack
            )

            // 远程屏幕显示区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                when (val state = uiState.sessionState) {
                    is SessionState.Connected -> {
                        RemoteScreenCanvas(
                            modifier = Modifier.fillMaxSize(),
                            videoFrame = uiState.videoFrame,
                            onTouchEvent = { x, y, action ->
                                viewModel.sendTouchEvent(x, y, action)
                            }
                        )
                    }
                    is SessionState.Connecting,
                    is SessionState.Initializing -> {
                        ConnectingOverlay()
                    }
                    else -> {
                        ErrorOverlay(
                            message = uiState.errorMessage ?: "连接已断开",
                            onReconnect = { viewModel.reconnect() }
                        )
                    }
                }
            }

            // 底部控制栏
            ControlBar(
                onDisconnect = { viewModel.disconnect() },
                onBack = { viewModel.sendBackKey() },
                onHome = { viewModel.sendHomeKey() },
                onRecent = { viewModel.sendRecentKey() },
                onLock = { /* TODO */ },
                onVolume = { showVolumeDialog = true },
                onBrightness = { showBrightnessDialog = true },
                onMore = { showMoreMenu = true }
            )
        }

        // 音量对话框
        if (showVolumeDialog) {
            VolumeDialog(
                onDismiss = { showVolumeDialog = false },
                onVolumeUp = { /* TODO */ },
                onVolumeDown = { /* TODO */ }
            )
        }

        // 亮度对话框
        if (showBrightnessDialog) {
            BrightnessDialog(
                onDismiss = { showBrightnessDialog = false },
                onBrightnessChange = { /* TODO */ }
            )
        }

        // 更多菜单
        if (showMoreMenu) {
            MoreMenuDialog(
                onDismiss = { showMoreMenu = false },
                onScreenshot = { /* TODO */ },
                onRotate = { /* TODO */ },
                onFullscreen = { /* TODO */ }
            )
        }
    }
}

/**
 * 顶部状态栏
 */
@Composable
private fun TopStatusBar(
    quality: ConnectionQuality,
    latency: Int,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 连接质量指示器
                QualityIndicator(quality = quality)
            }

            // 延迟显示
            Text(
                text = "${latency}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 质量指示器
 */
@Composable
private fun QualityIndicator(quality: ConnectionQuality) {
    val (color, text) = when (quality) {
        ConnectionQuality.EXCELLENT -> Color(0xFF4CAF50) to "优秀"
        ConnectionQuality.GOOD -> Color(0xFF8BC34A) to "良好"
        ConnectionQuality.FAIR -> Color(0xFFFF9800) to "一般"
        ConnectionQuality.POOR -> Color(0xFFF44336) to "较差"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * 远程屏幕画布
 */
@Composable
private fun RemoteScreenCanvas(
    modifier: Modifier = Modifier,
    videoFrame: androidx.compose.ui.graphics.ImageBitmap?,
    onTouchEvent: (Float, Float, String) -> Unit
) {
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = offset.x / canvasSize.width
                        val y = offset.y / canvasSize.height
                        onTouchEvent(x, y, "tap")
                    },
                    onDoubleTap = { offset ->
                        val x = offset.x / canvasSize.width
                        val y = offset.y / canvasSize.height
                        onTouchEvent(x, y, "double_tap")
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val x = offset.x / canvasSize.width
                        val y = offset.y / canvasSize.height
                        onTouchEvent(x, y, "down")
                    },
                    onDrag = { change, _ ->
                        val x = change.position.x / canvasSize.width
                        val y = change.position.y / canvasSize.height
                        onTouchEvent(x, y, "move")
                        change.consume()
                    },
                    onDragEnd = {
                        onTouchEvent(0f, 0f, "up")
                    },
                    onDragCancel = {
                        onTouchEvent(0f, 0f, "up")
                    }
                )
            }
    ) {
        canvasSize = size

        // 绘制视频帧
        if (videoFrame != null) {
            drawImage(
                image = videoFrame,
                dstSize = androidx.compose.ui.unit.IntSize(
                    width = size.width.toInt(),
                    height = size.height.toInt()
                )
            )
        } else {
            // 占位符
            drawRect(Color.Gray)
            drawCircle(
                color = Color.White,
                radius = 40.dp.toPx(),
                center = center
            )
        }
    }
}

/**
 * 底部控制栏
 */
@Composable
private fun ControlBar(
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecent: () -> Unit,
    onLock: () -> Unit,
    onVolume: () -> Unit,
    onBrightness: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // 第一行：主要控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 红色挂断按钮
                ControlButton(
                    onClick = onDisconnect,
                    icon = Icons.Default.Call,
                    iconTint = Color.White,
                    backgroundColor = Color(0xFFE53935),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // 返回按钮
                ControlButton(
                    onClick = onBack,
                    icon = Icons.Default.ArrowBack,
                    text = "返回"
                )

                // 主页按钮
                ControlButton(
                    onClick = onHome,
                    icon = Icons.Default.Star,
                    text = "主页"
                )

                // 多任务按钮
                ControlButton(
                    onClick = onRecent,
                    icon = Icons.Default.List,
                    text = "多任务"
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 第二行：辅助控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 锁屏按钮
                ControlButton(
                    onClick = onLock,
                    icon = Icons.Default.Lock,
                    text = "锁屏"
                )

                // 音量按钮
                ControlButton(
                    onClick = onVolume,
                    icon = Icons.Default.Notifications,
                    text = "音量"
                )

                // 亮度按钮
                ControlButton(
                    onClick = onBrightness,
                    icon = Icons.Default.Settings,
                    text = "亮度"
                )

                Spacer(modifier = Modifier.weight(1f))

                // 更多按钮
                ControlButton(
                    onClick = onMore,
                    icon = Icons.Default.MoreVert
                )
            }
        }
    }
}

/**
 * 控制按钮
 */
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    text: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(if (text != null) 56.dp else 48.dp),
        color = backgroundColor,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            if (text != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 连接中覆盖层
 */
@Composable
private fun ConnectingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = "正在连接...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

/**
 * 错误覆盖层
 */
@Composable
private fun ErrorOverlay(
    message: String,
    onReconnect: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Button(onClick = onReconnect) {
                Text("重新连接")
            }
        }
    }
}

/**
 * 音量对话框
 */
@Composable
private fun VolumeDialog(
    onDismiss: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音量控制") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onVolumeDown, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "减小音量", modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onVolumeUp, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "增大音量", modifier = Modifier.size(32.dp))
                }
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
 * 亮度对话框
 */
@Composable
private fun BrightnessDialog(
    onDismiss: () -> Unit,
    onBrightnessChange: (Float) -> Unit
) {
    var brightness by remember { mutableStateOf(0.5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("屏幕亮度") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0f..1f
                )
                Text(
                    text = "${(brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onBrightnessChange(brightness); onDismiss() }) {
                Text("确定")
            }
        }
    )
}

/**
 * 更多菜单对话框
 */
@Composable
private fun MoreMenuDialog(
    onDismiss: () -> Unit,
    onScreenshot: () -> Unit,
    onRotate: () -> Unit,
    onFullscreen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更多选项") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuOption(icon = Icons.Default.Info, text = "截图", onClick = onScreenshot)
                MenuOption(icon = Icons.Default.Refresh, text = "旋转屏幕", onClick = onRotate)
                MenuOption(icon = Icons.Default.Settings, text = "全屏模式", onClick = onFullscreen)
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
 * 菜单选项
 */
@Composable
private fun MenuOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
