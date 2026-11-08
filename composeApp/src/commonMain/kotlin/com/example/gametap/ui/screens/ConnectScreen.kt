package com.example.gametap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gametap.platform.TransportState
import com.example.gametap.ui.components.StatusPill
import com.example.gametap.ui.theme.GamepadColors
import com.example.gametap.viewmodel.ConnectionViewModel

@Composable
fun ConnectScreen(
    viewModel: ConnectionViewModel,
    onOpenGamepad: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val permissionDenied by viewModel.permissionDenied.collectAsState()
    val isConnected = state is TransportState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Gametap", style = MaterialTheme.typography.headlineLarge, color = GamepadColors.Label)
        StatusPill(state)
        Text(
            text = helpText(state, permissionDenied),
            style = MaterialTheme.typography.bodyMedium,
            color = GamepadColors.Label,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Column(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                TransportState.Idle, is TransportState.Error -> Button(
                    onClick = viewModel::start,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start gamepad") }

                TransportState.Advertising, is TransportState.Connected -> {
                    if (viewModel.needsDiscoverability && !isConnected) {
                        Button(
                            onClick = viewModel::requestDiscoverable,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Make discoverable") }
                    }
                    OutlinedButton(
                        onClick = viewModel::stop,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop") }
                }

                TransportState.Unsupported -> Unit
            }
            OutlinedButton(onClick = onOpenGamepad, modifier = Modifier.fillMaxWidth()) {
                Text("Open gamepad")
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Settings")
            }
        }
    }
}

private fun helpText(state: TransportState, permissionDenied: Boolean): String = when {
    permissionDenied -> "Gametap needs Bluetooth access. Turn Bluetooth on and allow the permission, then try again."
    state == TransportState.Unsupported ->
        "This phone does not offer the Bluetooth gamepad role, so it cannot act as a controller."
    state == TransportState.Advertising ->
        "On your PC, open Bluetooth settings, add a device and choose this phone."
    state is TransportState.Connected -> "Hold the dots in the top-left corner of the gamepad to open settings."
    else -> "Your phone becomes a Bluetooth gamepad. The host needs no app or driver."
}
