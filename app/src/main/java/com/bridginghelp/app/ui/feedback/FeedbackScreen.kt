package com.bridginghelp.app.ui.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * 反馈类型
 */
enum class FeedbackType(val displayName: String) {
    BUG_REPORT("Bug 报告"),
    FEATURE_REQUEST("功能建议"),
    GENERAL_FEEDBACK("一般反馈"),
    OTHER("其他")
}

/**
 * 反馈页面 UI 状态
 */
data class FeedbackUiState(
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null,
    val selectedType: FeedbackType = FeedbackType.GENERAL_FEEDBACK
)

/**
 * 反馈页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var uiState by remember { mutableStateOf(FeedbackUiState()) }

    // 表单状态
    var feedbackType by remember { mutableStateOf(FeedbackType.GENERAL_FEEDBACK) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("意见反馈") },
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.submitSuccess) {
                // 成功状态
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "反馈已提交",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "感谢您的宝贵意见，我们会认真处理您的反馈。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("返回")
                    }
                }
            } else {
                // 表单
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 反馈类型选择
                    Text(
                        text = "反馈类型",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Card {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            FeedbackType.values().forEach { type ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = feedbackType == type,
                                        onClick = {
                                            feedbackType = type
                                            uiState = uiState.copy(selectedType = type)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = type.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }

                    // 主题
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("主题") },
                        placeholder = { Text("简要描述您的反馈主题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 详细描述
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("详细描述") },
                        placeholder = { Text("请详细描述您遇到的问题或建议...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )

                    // 选项
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeLogs,
                            onCheckedChange = { includeLogs = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "包含系统日志（帮助定位问题）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 错误提示
                    if (uiState.submitError != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = uiState.submitError ?: "未知错误",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 提交按钮
                    Button(
                        onClick = {
                            // 模拟提交
                            uiState = uiState.copy(
                                isSubmitting = true,
                                submitError = null
                            )
                            // 模拟网络请求
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                uiState = uiState.copy(
                                    isSubmitting = false,
                                    submitSuccess = true
                                )
                            }, 1000)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = subject.isNotBlank() && description.isNotBlank() && !uiState.isSubmitting
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("提交中...")
                        } else {
                            Text("提交反馈")
                        }
                    }
                }
            }
        }
    }
}
