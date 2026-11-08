package com.example.gametap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.example.gametap.platform.TransportState
import com.example.gametap.ui.components.GamepadCanvas
import com.example.gametap.ui.components.StatusPill
import com.example.gametap.ui.theme.GamepadColors
import com.example.gametap.viewmodel.GamepadViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SETTINGS_HOLD_MS = 700L

@Composable
fun GamepadScreen(
    viewModel: GamepadViewModel,
    transportState: StateFlow<TransportState>,
    onOpenSettings: () -> Unit,
) {
    val transport by transportState.collectAsState()
    val inputState = viewModel.input.state.collectAsState()
    val stickOrigins = viewModel.tracker.stickOrigins.collectAsState()
    val openSettings by rememberUpdatedState(onOpenSettings)
    val scope = rememberCoroutineScope()

    DisposableEffect(viewModel) {
        viewModel.onScreenShown()
        onDispose { viewModel.onScreenHidden() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GamepadColors.Background)
            .pointerInput(viewModel) {
                val tracker = viewModel.tracker
                val corner = viewModel.layout.settingsCorner
                val holds = HashMap<Long, Job>()
                awaitPointerEventScope {
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            tracker.resize(width, height)
                            for (change in event.changes) {
                                val id = change.id.value
                                val position = change.position
                                when {
                                    change.changedToDownIgnoreConsumed() -> {
                                        tracker.down(id, position.x, position.y)
                                        if (corner.contains(position.x, position.y, width, height)) {
                                            holds[id] = scope.launch {
                                                delay(SETTINGS_HOLD_MS)
                                                openSettings()
                                            }
                                        }
                                    }
                                    change.changedToUpIgnoreConsumed() -> {
                                        tracker.up(id)
                                        holds.remove(id)?.cancel()
                                    }
                                    change.positionChanged() -> tracker.move(id, position.x, position.y)
                                }
                                change.consume()
                            }
                        }
                    } finally {
                        holds.values.forEach { it.cancel() }
                        tracker.releaseAll()
                    }
                }
            },
    ) {
        GamepadCanvas(viewModel.layout, inputState, stickOrigins, Modifier.fillMaxSize())
        StatusPill(transport, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}
