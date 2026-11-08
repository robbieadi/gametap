package com.example.gametap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gametap.ui.theme.GamepadColors
import com.example.gametap.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = GamepadColors.Label,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Done") }
        }
        SliderRow(
            title = "Stick deadzone",
            value = settings.deadzone,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            range = 0f..0.4f,
            onChange = { value -> viewModel.update { it.copy(deadzone = value) } },
        )
        SliderRow(
            title = "Sensitivity curve",
            value = settings.curveExponent,
            valueLabel = { if (it <= 1.01f) "Linear" else "Finer near center" },
            range = 1f..3f,
            onChange = { value -> viewModel.update { it.copy(curveExponent = value) } },
        )
        SwitchRow("Haptics", settings.hapticsEnabled) { value ->
            viewModel.update { it.copy(hapticsEnabled = value) }
        }
        SwitchRow("Floating sticks", settings.floatingSticks) { value ->
            viewModel.update { it.copy(floatingSticks = value) }
        }
    }
}

/**
 * Shows the dragged position at once and saves only when the drag ends, so a drag is one write
 * instead of one per frame. [onChange] receives the final value.
 */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueLabel: (Float) -> String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    // Set while dragging and until the saved value comes back, so the thumb never jumps back.
    var dragged by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value) { dragged = null }
    val shown = dragged ?: value

    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(title, color = GamepadColors.Label, modifier = Modifier.weight(1f))
            Text(valueLabel(shown), color = GamepadColors.Accent)
        }
        Slider(
            value = shown,
            onValueChange = { dragged = it },
            onValueChangeFinished = { dragged?.let(onChange) },
            valueRange = range,
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = GamepadColors.Label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
