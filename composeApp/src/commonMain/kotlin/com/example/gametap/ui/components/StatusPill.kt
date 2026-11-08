package com.example.gametap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gametap.platform.TransportState
import com.example.gametap.ui.theme.GamepadColors

fun TransportState.label(): String = when (this) {
    TransportState.Unsupported -> "Not supported on this device"
    TransportState.Idle -> "Not started"
    TransportState.Advertising -> "Waiting for a host"
    is TransportState.Connected -> "Connected to $hostName"
    is TransportState.Error -> message
}

private fun TransportState.color(): Color = when (this) {
    is TransportState.Connected -> GamepadColors.Connected
    TransportState.Advertising -> GamepadColors.Warning
    TransportState.Idle -> GamepadColors.Label
    TransportState.Unsupported, is TransportState.Error -> GamepadColors.Error
}

@Composable
fun StatusPill(state: TransportState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(GamepadColors.Surface, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(state.color(), CircleShape))
        Text(
            text = state.label(),
            style = MaterialTheme.typography.labelMedium,
            color = GamepadColors.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
