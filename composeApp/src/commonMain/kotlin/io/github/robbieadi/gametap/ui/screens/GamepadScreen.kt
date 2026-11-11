package io.github.robbieadi.gametap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.ui.components.GamepadCanvas
import io.github.robbieadi.gametap.ui.components.StatusPill
import io.github.robbieadi.gametap.ui.theme.GamepadColors
import io.github.robbieadi.gametap.viewmodel.GamepadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private const val MENU_HOLD_MS = 700L

@Composable
fun GamepadScreen(
    viewModel: GamepadViewModel,
    transportState: StateFlow<TransportState>,
    onOpenSettings: () -> Unit,
) {
    val transport by transportState.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val touchView = viewModel.tracker.view.collectAsState()

    // Restarts on every change, so lifting or sliding off the menu control cancels the hold.
    val menuHeld by remember { derivedStateOf { touchView.value.menuHeld } }
    LaunchedEffect(menuHeld) {
        if (menuHeld) {
            delay(MENU_HOLD_MS)
            onOpenSettings()
        }
    }

    // Runs on start and on stop, not only when the screen leaves composition, so Home or locking
    // the phone also releases every control and stops the 16 ms keep-alive.
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenShown()
        onStopOrDispose { viewModel.onScreenHidden() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GamepadColors.Background)
            .pointerInput(viewModel) {
                val tracker = viewModel.tracker
                awaitPointerEventScope {
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            tracker.resize(size.width.toFloat(), size.height.toFloat())
                            for (change in event.changes) {
                                val id = change.id.value
                                val position = change.position
                                when {
                                    change.changedToDownIgnoreConsumed() -> tracker.down(id, position.x, position.y)
                                    change.changedToUpIgnoreConsumed() -> tracker.up(id)
                                    change.positionChanged() -> tracker.move(id, position.x, position.y)
                                }
                                change.consume()
                            }
                        }
                    } finally {
                        tracker.releaseAll()
                    }
                }
            },
    ) {
        GamepadCanvas(layout, touchView, Modifier.fillMaxSize())
        StatusPill(transport, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}
