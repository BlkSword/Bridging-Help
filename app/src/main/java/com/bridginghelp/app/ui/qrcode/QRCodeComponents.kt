package com.bridginghelp.app.ui.qrcode

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 二维码显示组件
 */
@Composable
fun QRCodeDisplay(
    content: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 生成二维码
    androidx.compose.runtime.LaunchedEffect(content) {
        qrBitmap = generateQRCode(content, 512)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 二维码图片
            qrBitmap?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                )
            } ?: CircularProgressIndicator()

            // 描述文字
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            // 提示
            Text(
                text = "扫描此二维码连接设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 显示完整内容（可复制）
            OutlinedButton(
                onClick = {
                    // 复制到剪贴板
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Device ID", content)
                    clipboard.setPrimaryClip(clip)
                }
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("复制设备ID")
            }
        }
    }
}

/**
 * 二维码扫描按钮
 */
@Composable
fun QRCodeScannerButton(
    onScanResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            if (result.contents == null) {
                // 取消扫描
            } else {
                onScanResult(result.contents)
            }
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = {
            val options = ScanOptions()
            options.setBeepEnabled(true)
            scanLauncher.launch(options)
        },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "扫描二维码",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "扫描受控端设备二维码进行连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 配对信息卡片（显示配对码和二维码）
 */
@Composable
fun PairingInfoCard(
    deviceId: String,
    deviceName: String,
    ipAddress: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 标题
            Text(
                text = "设备配对信息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )

            // 设备信息
            InfoRow("设备名称", deviceName)
            InfoRow("设备ID", deviceId)
            InfoRow("IP地址", ipAddress)

            Spacer(modifier = Modifier.height(8.dp))

            // 二维码生成内容
            val qrContent = "bridginghelp://pair?deviceId=$deviceId&name=${URLEncoder.encode(deviceName, "UTF-8")}&ip=$ipAddress"
            QRCodeDisplay(
                content = qrContent,
                description = null,
                modifier = Modifier.fillMaxWidth()
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
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 生成二维码图片
 */
private fun generateQRCode(content: String, size: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }

    return bitmap
}

/**
 * 解析配对二维码内容
 */
fun parsePairingQRCode(content: String): PairingInfo? {
    if (!content.startsWith("bridginghelp://pair?")) {
        return null
    }

    try {
        val params = content.substringAfter("bridginghelp://pair?")
            .split("&")
            .associate { param ->
                val (key, value) = param.split("=")
                key to java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
            }

        return PairingInfo(
            deviceId = params["deviceId"] ?: return null,
            deviceName = params["name"] ?: return null,
            ipAddress = params["ip"] ?: return null
        )
    } catch (e: Exception) {
        return null
    }
}

/**
 * 配对信息
 */
data class PairingInfo(
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String
) {
    fun getFullAddress(): String = "$ipAddress:52345"
}
