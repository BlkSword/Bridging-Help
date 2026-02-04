package com.bridginghelp.app.ui.remoteassist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

enum class RemoteAssistMode {
    CONTROLLER,
    CONTROLLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteAssistScreen(
    viewModel: RemoteAssistViewModel = hiltViewModel(),
    onNavigateToController: () -> Unit,
    onNavigateToControlled: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMode by remember { mutableStateOf(RemoteAssistMode.CONTROLLER) }
    var partnerDeviceId by remember { mutableStateOf("") }
    var partnerVerifyCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程协助") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F2F5))
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = if (selectedMode == RemoteAssistMode.CONTROLLER) 0 else 1,
                containerColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(
                            tabPositions[if (selectedMode == RemoteAssistMode.CONTROLLER) 0 else 1]
                        ),
                        color = Color(0xFFA8C7FA)
                    )
                }
            ) {
                Tab(
                    selected = selectedMode == RemoteAssistMode.CONTROLLER,
                    onClick = { selectedMode = RemoteAssistMode.CONTROLLER },
                    text = {
                        Text(
                            text = "控制别人",
                            fontWeight = if (selectedMode == RemoteAssistMode.CONTROLLER) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedMode == RemoteAssistMode.CONTROLLED,
                    onClick = { selectedMode = RemoteAssistMode.CONTROLLED },
                    text = {
                        Text(
                            text = "被别人控制",
                            fontWeight = if (selectedMode == RemoteAssistMode.CONTROLLED) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedMode == RemoteAssistMode.CONTROLLER) {
                    ControllerModeContent(
                        partnerDeviceId = partnerDeviceId,
                        partnerVerifyCode = partnerVerifyCode,
                        onDeviceIdChange = { partnerDeviceId = it },
                        onVerifyCodeChange = { partnerVerifyCode = it },
                        onStartRemoteControl = { onNavigateToController() }
                    )
                } else {
                    ControlledModeContent(
                        onWaitForConnection = { onNavigateToControlled() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerModeContent(
    partnerDeviceId: String,
    partnerVerifyCode: String,
    onDeviceIdChange: (String) -> Unit,
    onVerifyCodeChange: (String) -> Unit,
    onStartRemoteControl: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "远控伙伴设备",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "通过其他设备的【设备ID】及【设备验证码】启动远程控制",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(8.dp))

                InputSection(
                    label = "伙伴的设备ID",
                    placeholder = "请输入设备ID",
                    value = partnerDeviceId,
                    onValueChange = onDeviceIdChange
                )

                InputSection(
                    label = "伙伴的设备验证码",
                    placeholder = "请输入设备验证码",
                    value = partnerVerifyCode,
                    onValueChange = onVerifyCodeChange,
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onStartRemoteControl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8C7FA)
                    )
                ) {
                    Text(
                        text = "开始远控",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        BottomTips()
    }
}

@Composable
private fun ControlledModeContent(
    onWaitForConnection: () -> Unit
) {
    val myDeviceId = "BH-12345678"
    val myVerifyCode = myDeviceId.takeLast(6)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "我的设备信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "将以下信息提供给好友，好友即可远程控制您的设备",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(8.dp))

                DeviceCodeDisplay(
                    label = "我的设备ID",
                    code = myDeviceId
                )

                DeviceCodeDisplay(
                    label = "我的验证码",
                    code = myVerifyCode
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onWaitForConnection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8C7FA)
                    )
                ) {
                    Text(
                        text = "等待连接",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        BottomTips()
    }
}

@Composable
private fun DeviceCodeDisplay(
    label: String,
    code: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = code,
                modifier = Modifier.padding(16.dp),
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }
    }
}

@Composable
private fun InputSection(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color(0xFF999999)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color(0xFFA8C7FA),
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFFA8C7FA)
            ),
            singleLine = true
        )
    }
}

@Composable
private fun BottomTips() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = null,
            tint = Color(0xFF666666),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "在\"我的设备\"中查看您的完整设备信息",
            fontSize = 14.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )
        Text(
            text = "向好友提供这些信息即可建立连接",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center
        )
    }
}
