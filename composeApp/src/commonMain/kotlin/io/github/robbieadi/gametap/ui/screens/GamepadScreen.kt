package io.github.robbieadi.gametap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.controlIds
import io.github.robbieadi.gametap.layout.screenIssues
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.ui.components.FitBanner
import io.github.robbieadi.gametap.ui.components.GamepadCanvas
import io.github.robbieadi.gametap.ui.components.GamepadMenu
import io.github.robbieadi.gametap.ui.components.OverlayDialog
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
    /** Back to the start screen. [disconnect] also stops the gamepad. */
    onLeave: (disconnect: Boolean) -> Unit,
) {
    val transport by transportState.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val layouts by viewModel.layouts.collectAsState()
    val touchView = viewModel.tracker.view.collectAsState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    // Restarts on every change, so lifting or sliding off the menu control cancels the hold.
    val menuHeld by remember { derivedStateOf { touchView.value.menuHeld } }
    LaunchedEffect(menuHeld) {
        if (menuHeld) {
            delay(MENU_HOLD_MS)
            viewModel.onMenuOpened()
            menuOpen = true
        }
    }

    // While the menu is open, Back closes it instead of leaving the gamepad.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = menuOpen || renaming != null || deleting != null,
        onBackCompleted = {
            when {
                renaming != null -> renaming = null
                deleting != null -> deleting = null
                else -> menuOpen = false
            }
        },
    )

    // Checked again whenever the window changes size (a fold, split screen), not only on load.
    var size by remember { mutableStateOf(IntSize.Zero) }
    val flagged = remember(layout, size) {
        if (size == IntSize.Zero) {
            emptySet()
        } else {
            layout.screenIssues(ScreenSize(size.width.toFloat(), size.height.toFloat())).controlIds()
        }
    }
    // Dismissing hides the banner for this layout at this size. The outlines stay.
    var dismissedFor by remember { mutableStateOf<Pair<LayoutProfile, IntSize>?>(null) }
    val showBanner = flagged.isNotEmpty() && dismissedFor != (layout to size)

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
            .onSizeChanged { size = it }
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
                                    // A consumed down landed on the banner, so it stays off the controls.
                                    change.changedToDownIgnoreConsumed() ->
                                        if (!change.isConsumed) tracker.down(id, position.x, position.y)
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
        GamepadCanvas(layout, touchView, Modifier.fillMaxSize(), flagged)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(transport)
            if (showBanner) {
                FitBanner(onDismiss = { dismissedFor = layout to size })
            }
        }
        if (menuOpen) {
            GamepadMenu(
                layouts = layouts,
                transport = transport,
                onSelectProfile = { id ->
                    viewModel.selectProfile(id)
                    menuOpen = false
                },
                onDuplicateProfile = viewModel::duplicateProfile,
                onRenameProfile = { renaming = it },
                onDeleteProfile = { deleting = it },
                onResume = { menuOpen = false },
                onOpenSettings = {
                    menuOpen = false
                    onOpenSettings()
                },
                onLeave = {
                    menuOpen = false
                    onLeave(transport is TransportState.Connected || transport == TransportState.Advertising)
                },
            )
        }
        renaming?.let { id ->
            var name by remember(id) { mutableStateOf(layouts.find(id)?.name.orEmpty()) }
            val taken = viewModel.isNameTaken(id, name)
            OverlayDialog(
                title = "Rename layout",
                confirmLabel = "Rename",
                confirmEnabled = name.isNotBlank() && !taken,
                onConfirm = {
                    viewModel.renameProfile(id, name)
                    renaming = null
                },
                onDismiss = { renaming = null },
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = taken,
                    supportingText = if (taken) ({ Text("Another layout has this name") }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        deleting?.let { id ->
            OverlayDialog(
                title = "Delete \"${layouts.find(id)?.name.orEmpty()}\"?",
                confirmLabel = "Delete",
                onConfirm = {
                    viewModel.deleteProfile(id)
                    deleting = null
                },
                onDismiss = { deleting = null },
            )
        }
    }
}
