package com.bridginghelp.app.ui.mydevice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bridginghelp.core.model.DeviceCapability
import com.bridginghelp.core.model.DeviceType
import kotlinx.coroutines.delay

/**
 * 我的设备屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDeviceScreen(
    viewModel: MyDeviceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCopyToast by remember { mutableStateOf(false) }

    // 自动隐藏复制提示
    LaunchedEffect(showCopyToast) {
        if (showCopyToast) {
            delay(2000)
            showCopyToast = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的设备") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 设备信息卡片 - 突出显示设备ID和验证码
                DeviceIdentityCard(
                    deviceId = uiState.deviceId,
                    deviceName = uiState.deviceName,
                    onCopyDeviceId = {
                        copyToClipboard(context, uiState.deviceId)
                        showCopyToast = true
                    },
                    onCopyVerifyCode = {
                        // 生成6位验证码（从设备ID取后6位）
                        val verifyCode = uiState.deviceId.takeLast(6)
                        copyToClipboard(context, verifyCode)
                        showCopyToast = true
                    }
                )

                // 系统信息卡片
                SystemInfoCard(
                    osVersion = uiState.osVersion,
                    appVersion = uiState.appVersion,
                    screenWidth = uiState.screenWidth,
                    screenHeight = uiState.screenHeight,
                    screenDensity = uiState.screenDensity
                )

                // 设备能力卡片
                DeviceCapabilitiesCard(capabilities = uiState.capabilities)

                // 状态卡片
                DeviceStatusCard(
                    isAccessibilityEnabled = uiState.isAccessibilityEnabled
                )
            }

            // 复制成功提示
            if (showCopyToast) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("已复制到剪贴板")
                }
            }
        }
    }
}

/**
 * 设备身份卡片 - 显示设备ID和验证码
 */
@Composable
private fun DeviceIdentityCard(
    deviceId: String,
    deviceName: String,
    onCopyDeviceId: () -> Unit,
    onCopyVerifyCode: () -> Unit
) {
    // 生成6位验证码
    val verifyCode = deviceId.takeLast(6)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 设备名称头部
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            // 设备ID
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "设备 ID",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                DeviceCodeRow(
                    code = deviceId,
                    onCopy = onCopyDeviceId
                )
            }

            // 验证码
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "连接验证码",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                DeviceCodeRow(
                    code = verifyCode,
                    onCopy = onCopyVerifyCode
                )
            }

            // 说明文字
            Text(
                text = "• 向好友提供设备 ID 或验证码即可建立连接\n• 点击上方代码可快速复制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 设备代码行 - 显示可复制的代码
 */
@Composable
private fun DeviceCodeRow(
    code: String,
    onCopy: () -> Unit
) {
    Surface(
        onClick = onCopy,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.2f, androidx.compose.ui.unit.TextUnitType.Em)
            )
            Text(
                text = "点击复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 系统信息卡片
 */
@Composable
private fun SystemInfoCard(
    osVersion: String,
    appVersion: String,
    screenWidth: Int,
    screenHeight: Int,
    screenDensity: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "系统信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            InfoRow("系统版本", osVersion)
            InfoRow("应用版本", appVersion)
            InfoRow("屏幕分辨率", "${screenWidth}x$screenHeight")
            InfoRow("屏幕密度", "${screenDensity} dpi")
        }
    }
}

/**
 * 设备能力卡片
 */
@Composable
private fun DeviceCapabilitiesCard(capabilities: Set<DeviceCapability>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "设备能力",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            capabilities.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { capability ->
                        CapabilityChip(
                            capability = capability,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 填充空白位置
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 能力标签
 */
@Composable
private fun CapabilityChip(
    capability: DeviceCapability,
    modifier: Modifier = Modifier
) {
    SuggestionChip(
        onClick = {},
        label = { Text(getCapabilityName(capability), style = MaterialTheme.typography.bodySmall) },
        modifier = modifier
    )
}

/**
 * 设备状态卡片
 */
@Composable
private fun DeviceStatusCard(
    isAccessibilityEnabled: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "服务状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            StatusItem(
                icon = Icons.Filled.Settings,
                label = "无障碍服务",
                isEnabled = isAccessibilityEnabled
            )
        }
    }
}

/**
 * 状态项
 */
@Composable
private fun StatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isEnabled: Boolean
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
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Text(label)
        }

        Surface(
            color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = if (isEnabled) "已启用" else "未启用",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
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
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 复制到剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("device_info", text))
}

// 辅助函数

private fun getDeviceTypeName(type: DeviceType): String {
    return when (type) {
        DeviceType.PHONE -> "手机"
        DeviceType.TABLET -> "平板"
        DeviceType.FOLDABLE -> "折叠屏"
        DeviceType.DESKTOP -> "桌面"
    }
}

private fun getCapabilityName(capability: DeviceCapability): String {
    return when (capability) {
        DeviceCapability.TOUCH_INPUT -> "触摸"
        DeviceCapability.MULTI_TOUCH -> "多点触控"
        DeviceCapability.KEYBOARD_INPUT -> "键盘"
        DeviceCapability.MOUSE_INPUT -> "鼠标"
        DeviceCapability.SCREEN_CAPTURE -> "录屏"
        DeviceCapability.AUDIO_CAPTURE -> "录音"
        DeviceCapability.CAMERA_CAPTURE -> "相机"
        DeviceCapability.P2P_CONNECTION -> "P2P"
        DeviceCapability.RELAY_CONNECTION -> "中继"
        DeviceCapability.H264_ENCODE -> "H.264"
        DeviceCapability.H265_ENCODE -> "H.265"
        DeviceCapability.VP8_ENCODE -> "VP8"
        DeviceCapability.VP9_ENCODE -> "VP9"
        DeviceCapability.HIGH_RESOLUTION -> "高清"
        DeviceCapability.HDR -> "HDR"
        DeviceCapability.FILE_TRANSFER -> "文件传输"
        DeviceCapability.CLIPBOARD_SYNC -> "剪贴板"
    }
}
