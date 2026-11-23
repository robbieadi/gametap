package io.github.robbieadi.gametap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.robbieadi.gametap.platform.ForgottenHost
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/**
 * What the gamepad is waiting for, under the status pill while no host is connected: how to pair,
 * or what went wrong. Hidden once a host is connected.
 */
@Composable
fun ConnectionBanner(
    state: TransportState,
    permissionDenied: Boolean,
    forgottenHost: ForgottenHost?,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = message(state, permissionDenied, forgottenHost) ?: return
    val canRetry = permissionDenied || state is TransportState.Error
    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = if (canRetry) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val problem = permissionDenied || state is TransportState.Error || state == TransportState.Unsupported ||
            forgottenHost?.lostServices == true
        Box(Modifier.size(8.dp).background(if (problem) GamepadColors.Error else GamepadColors.Warning, CircleShape))
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = GamepadColors.Label,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
        )
        if (canRetry) {
            Text(
                text = "Try again",
                style = MaterialTheme.typography.labelLarge,
                color = GamepadColors.Accent,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onTryAgain)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

private fun message(state: TransportState, permissionDenied: Boolean, forgotten: ForgottenHost?): String? = when {
    permissionDenied -> "Gametap needs Bluetooth on and the Bluetooth permission to act as a controller."
    state is TransportState.Connected -> null
    // Windows deletes the gamepad when its services go away under it, and needs pairing again.
    forgotten != null && state == TransportState.Advertising -> if (forgotten.lostServices) {
        "Gametap or Bluetooth was closed while ${forgotten.name} was connected, so it has probably " +
            "forgotten this controller. $REPAIR"
    } else {
        "${forgotten.name} has not reconnected. If it is on and nearby, it may have forgotten this " +
            "controller. $REPAIR"
    }
    state == TransportState.Advertising ->
        "On your PC, open Bluetooth settings, add a device and choose this phone. A paired PC connects by itself."
    state == TransportState.Unsupported ->
        "This phone cannot advertise as a Bluetooth Low Energy device, so it cannot act as a controller."
    state is TransportState.Error -> state.message
    else -> null // Starting: the status pill says so.
}

private const val REPAIR =
    "On the PC, remove this phone in Bluetooth settings, then add it again."
