package com.bridginghelp.app.ui.mydevice

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bridginghelp.core.model.DeviceCapability
import com.bridginghelp.core.model.DeviceType

/**
 * 我的设备屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDeviceScreen(
    viewModel: MyDeviceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的设备") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 设备信息卡片
            DeviceInfoCard(
                deviceName = uiState.deviceName,
                deviceId = uiState.deviceId,
                deviceType = uiState.deviceType
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
    }
}

/**
 * 设备信息卡片
 */
@Composable
private fun DeviceInfoCard(
    deviceName: String,
    deviceId: String,
    deviceType: DeviceType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            InfoRow("设备ID", deviceId)
            InfoRow("设备类型", getDeviceTypeName(deviceType))
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
            InfoRow("屏幕分辨率", "${screenWidth}x$screenHeight}")
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
