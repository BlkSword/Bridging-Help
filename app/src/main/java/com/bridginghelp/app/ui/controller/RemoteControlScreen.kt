package com.bridginghelp.app.ui.controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bridginghelp.core.model.ConnectionQuality
import com.bridginghelp.core.model.SessionState
import androidx.compose.foundation.gestures.detectDragGestures

/**
 * 远程控制屏幕
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程控制") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleQualityInfo() }) {
                        Icon(Icons.Default.Info, contentDescription = "质量信息")
                    }
                }
            )
        },
        bottomBar = {
            RemoteControlBottomBar(
                connectionQuality = uiState.connectionQuality,
                latency = uiState.latency,
                resolution = uiState.resolution,
                fps = uiState.fps,
                onDisconnect = { viewModel.disconnect() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState.sessionState) {
                is SessionState.Connected -> {
                    // 远程屏幕显示
                    RemoteScreenCanvas(
                        modifier = Modifier.fillMaxSize(),
                        videoFrame = uiState.videoFrame,
                        onTouchEvent = { x, y, action ->
                            viewModel.sendTouchEvent(x, y, action)
                        }
                    )

                    // 质量信息浮窗
                    if (uiState.showQualityInfo) {
                        QualityInfoOverlay(
                            quality = state.quality,
                            latency = uiState.latency,
                            fps = uiState.fps,
                            resolution = uiState.resolution,
                            onDismiss = { viewModel.toggleQualityInfo() }
                        )
                    }
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

            // 虚拟按键栏
            VirtualKeyBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                onBackClick = { viewModel.sendBackKey() },
                onHomeClick = { viewModel.sendHomeKey() },
                onRecentClick = { viewModel.sendRecentKey() }
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
    var canvasSize by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Canvas(
        modifier = modifier
            .background(Color.Black)
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
private fun RemoteControlBottomBar(
    connectionQuality: ConnectionQuality,
    latency: Int,
    resolution: String,
    fps: Int,
    onDisconnect: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 连接信息
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionQualityIndicator(quality = connectionQuality)
                Column {
                    Text(
                        text = "$latency ms | ${resolution} | ${fps} fps",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 断开按钮
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "断开连接",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 连接质量指示器
 */
@Composable
private fun ConnectionQualityIndicator(quality: ConnectionQuality) {
    val (color, text) = when (quality) {
        ConnectionQuality.EXCELLENT -> Color(0xFF4CAF50) to "优秀"
        ConnectionQuality.GOOD -> Color(0xFF8BC34A) to "良好"
        ConnectionQuality.FAIR -> Color(0xFFFF9800) to "一般"
        ConnectionQuality.POOR -> Color(0xFFF44336) to "较差"
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 质量信息浮窗
 */
@Composable
private fun QualityInfoOverlay(
    quality: ConnectionQuality,
    latency: Int,
    fps: Int,
    resolution: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接质量") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QualityRow("质量", getQualityText(quality))
                QualityRow("延迟", "$latency ms")
                QualityRow("帧率", "$fps fps")
                QualityRow("分辨率", resolution)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun QualityRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

private fun getQualityText(quality: ConnectionQuality): String {
    return when (quality) {
        ConnectionQuality.EXCELLENT -> "优秀"
        ConnectionQuality.GOOD -> "良好"
        ConnectionQuality.FAIR -> "一般"
        ConnectionQuality.POOR -> "较差"
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
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "正在连接...",
                style = MaterialTheme.typography.bodyLarge
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
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onReconnect) {
                Text("重新连接")
            }
        }
    }
}

/**
 * 虚拟按键栏
 */
@Composable
private fun VirtualKeyBar(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecentClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VirtualKeyButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "返回",
                onClick = onBackClick
            )
            VirtualKeyButton(
                icon = Icons.Default.Star,
                contentDescription = "主页",
                onClick = onHomeClick
            )
            VirtualKeyButton(
                icon = Icons.Default.List,
                contentDescription = "最近",
                onClick = onRecentClick
            )
        }
    }
}

@Composable
private fun VirtualKeyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}
