package com.bridginghelp.app.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 帮助条目
 */
data class HelpItem(
    val question: String,
    val answer: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * 帮助页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    val helpItems = remember {
        listOf(
            HelpItem(
                question = "如何开始远程协助？",
                answer = "在首页选择您想要的角色（控制端或受控端）。作为控制端，您可以发现并连接到附近的设备；作为受控端，您可以等待其他设备的连接请求。",
                icon = Icons.Default.Phone
            ),
            HelpItem(
                question = "需要什么权限？",
                answer = "应用需要以下权限：\n\n• 屏幕捕获权限：用于共享您的屏幕\n• 无障碍服务权限：用于接收远程控制操作\n• 网络权限：用于设备间通信\n• 通知权限：用于显示连接状态",
                icon = Icons.Outlined.Lock
            ),
            HelpItem(
                question = "如何启用无障碍服务？",
                answer = "1. 进入手机设置\n2. 找到「无障碍」或「辅助功能」\n3. 在已下载的服务中找到 BridgingHelp\n4. 开启服务开关\n5. 授予所有权限",
                icon = Icons.Default.Settings
            ),
            HelpItem(
                question = "连接不稳定怎么办？",
                answer = "• 确保两台设备在同一网络\n• 检查网络信号强度\n• 尝试降低视频质量\n• 重启应用或重新连接",
                icon = Icons.Default.Settings
            ),
            HelpItem(
                question = "如何调整视频质量？",
                answer = "应用会根据网络状况自动调整视频质量。您也可以在远程控制界面点击质量图标查看当前连接状态，系统会自动优化传输质量。",
                icon = Icons.Default.Settings
            ),
            HelpItem(
                question = "支持哪些操作？",
                answer = "作为控制端，您可以：\n\n• 触摸屏幕点击和拖动\n• 发送返回、主页、最近键\n• 双指缩放\n• 滚动浏览\n• 传输文件（即将推出）",
                icon = Icons.Default.Phone
            ),
            HelpItem(
                question = "数据安全吗？",
                answer = "是的。我们采用端到端加密（WebRTC）确保您的数据安全。所有屏幕内容和操作都在本地处理，不会上传到任何服务器。",
                icon = Icons.Default.Lock
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帮助中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // 欢迎部分
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "欢迎使用 BridgingHelp",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "以下是常见问题解答，帮助您快速上手",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // 常见问题部分
            Text(
                text = "常见问题",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            helpItems.forEach { item ->
                HelpItemCard(item)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 联系支持部分
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "需要更多帮助？",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "如果您遇到问题或有建议，请通过「我的」页面中的反馈功能联系我们。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "版本信息",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "v1.0.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpItemCard(item: HelpItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
