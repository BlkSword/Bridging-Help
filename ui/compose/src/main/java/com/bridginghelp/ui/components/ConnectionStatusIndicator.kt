package com.bridginghelp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bridginghelp.core.model.ConnectionState
import com.bridginghelp.ui.theme.StatusConnected
import com.bridginghelp.ui.theme.StatusConnecting
import com.bridginghelp.ui.theme.StatusDisconnected
import com.bridginghelp.ui.theme.StatusPaused

/**
 * 连接状态指示器
 */
@Composable
fun ConnectionStatusIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (state) {
        ConnectionState.CONNECTED -> StatusConnected to "已连接"
        ConnectionState.CONNECTING,
        ConnectionState.RECONNECTING -> StatusConnecting to "连接中..."
        ConnectionState.DISCONNECTED,
        ConnectionState.FAILED -> StatusDisconnected to "未连接"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 连接质量指示器
 */
@Composable
fun ConnectionQualityIndicator(
    quality: com.bridginghelp.core.model.ConnectionQuality,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (quality) {
        com.bridginghelp.core.model.ConnectionQuality.EXCELLENT ->
            com.bridginghelp.ui.theme.QualityExcellent to "优秀"
        com.bridginghelp.core.model.ConnectionQuality.GOOD ->
            com.bridginghelp.ui.theme.QualityGood to "良好"
        com.bridginghelp.core.model.ConnectionQuality.FAIR ->
            com.bridginghelp.ui.theme.QualityFair to "一般"
        com.bridginghelp.core.model.ConnectionQuality.POOR ->
            com.bridginghelp.ui.theme.QualityPoor to "较差"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "质量",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
