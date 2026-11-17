package io.github.robbieadi.gametap.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.robbieadi.gametap.domain.Button as PadButton
import io.github.robbieadi.gametap.input.TouchView
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.Area
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.DpadDirection
import io.github.robbieadi.gametap.layout.EditorMetrics
import io.github.robbieadi.gametap.layout.EditorState
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.Guide
import io.github.robbieadi.gametap.layout.GyroAxis
import io.github.robbieadi.gametap.layout.GyroBinding
import io.github.robbieadi.gametap.layout.GyroButton
import io.github.robbieadi.gametap.layout.GyroHorizontal
import io.github.robbieadi.gametap.layout.GyroSetup
import io.github.robbieadi.gametap.layout.GyroTarget
import io.github.robbieadi.gametap.layout.LayoutEditor
import io.github.robbieadi.gametap.layout.LayoutIssue
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.NewControl
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.VolumeKey
import io.github.robbieadi.gametap.layout.area
import io.github.robbieadi.gametap.layout.blockedGyroSwitches
import io.github.robbieadi.gametap.layout.centerX
import io.github.robbieadi.gametap.layout.controlIds
import io.github.robbieadi.gametap.layout.isGyro
import io.github.robbieadi.gametap.layout.isVolumeKeys
import io.github.robbieadi.gametap.ui.components.GamepadCanvas
import io.github.robbieadi.gametap.ui.components.OverlayDialog
import io.github.robbieadi.gametap.ui.components.consumeTouchDowns
import io.github.robbieadi.gametap.ui.components.scrollIndicator
import io.github.robbieadi.gametap.ui.theme.GamepadColors
import io.github.robbieadi.gametap.viewmodel.EditorViewModel
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Height of the band the toolbar occupies, used to move it out of the selected control's way. */
private val TOOLBAR_BAND = 72.dp

/** The profile-wide settings panels, shown when no control is selected. */
private enum class Panel { GYRO, VOLUME_KEYS, HAPTICS }

@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenShown()
        onStopOrDispose { viewModel.onScreenHidden() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(GamepadColors.Background)) {
        val density = LocalDensity.current
        val screen = with(density) { ScreenSize(maxWidth.toPx(), maxHeight.toPx()) }
        val editor = remember(viewModel) {
            // About 7 mm minimum size, a little over one thumb-width of snapping, a fingertip-sized handle.
            val metrics = with(density) { EditorMetrics(minHalfPx = 22.dp.toPx(), snapPx = 8.dp.toPx(), handlePx = 24.dp.toPx()) }
            viewModel.editor(screen, metrics)
        }
        LaunchedEffect(screen) { editor.resize(screen) }
        val state by editor.state.collectAsState()
        val scope = rememberCoroutineScope()

        var dragging by remember { mutableStateOf(false) }
        var preferBottom by rememberSaveable { mutableStateOf(false) }
        var renaming by remember { mutableStateOf(false) }
        var confirmDiscard by remember { mutableStateOf(false) }
        var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
        // Selecting a control swaps the profile panel for the control's own.
        LaunchedEffect(state.selectedId) { if (state.selectedId != null) panel = null }

        fun toggle(next: Panel) {
            editor.select(null)
            panel = if (panel == next) null else next
        }

        fun close() {
            if (viewModel.hasUnsavedChanges()) confirmDiscard = true else onClose()
        }

        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = {
                when {
                    renaming -> renaming = false
                    confirmDiscard -> confirmDiscard = false
                    panel != null -> panel = null
                    else -> close()
                }
            },
        )

        val idleView = remember { mutableStateOf(TouchView()) }
        GamepadCanvas(
            layout = state.draft,
            view = idleView,
            flagged = state.issues.controlIds(),
            modifier = Modifier.fillMaxSize().pointerInput(editor) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    editor.press(down.position.x, down.position.y)
                    val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (start == null) {
                        editor.release(dragged = false)
                        return@awaitEachGesture
                    }
                    dragging = true
                    try {
                        editor.dragTo(start.position.x, start.position.y)
                        drag(start.id) { change ->
                            editor.dragTo(change.position.x, change.position.y)
                            change.consume()
                        }
                    } finally {
                        dragging = false
                        editor.release(dragged = true)
                    }
                }
            },
        )
        SelectionOverlay(state, editor)

        if (!dragging) {
            val bandPx = with(density) { TOOLBAR_BAND.toPx() }
            val selectedArea = state.selected?.area(screen)
            val atBottom = when {
                selectedArea == null -> preferBottom
                preferBottom -> !selectedArea.reaches(bottom = screen.height - bandPx)
                else -> selectedArea.reaches(top = bandPx)
            }
            Toolbar(
                state = state,
                editor = editor,
                onClose = ::close,
                onRename = { renaming = true },
                onFlip = { preferBottom = !atBottom },
                onGyro = { toggle(Panel.GYRO) },
                // Hidden where the keys cannot be captured. Layouts stay on the device they were
                // made on, so none there can have keys bound.
                onVolumeKeys = if (viewModel.volumeKeysAvailable) ({ toggle(Panel.VOLUME_KEYS) }) else null,
                // Hidden where the phone cannot vibrate; the setting stays with the layout for other phones.
                onHaptics = if (viewModel.hapticsAvailable) ({ toggle(Panel.HAPTICS) }) else null,
                onSave = { scope.launch { if (viewModel.save()) onClose() } },
                modifier = Modifier.align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter),
            )
            state.selected?.let { selected ->
                // Docks on the side away from the control, so it never covers what is being edited.
                val onRight = selected.position.centerX(screen) < screen.width / 2f
                ControlPanel(
                    control = selected,
                    state = state,
                    editor = editor,
                    modifier = Modifier
                        .align(if (onRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(8.dp)
                        .width(260.dp)
                        .heightIn(max = maxHeight - 16.dp),
                )
            }
            if (panel == Panel.VOLUME_KEYS && state.selected == null) {
                VolumeKeysPanel(
                    draft = state.draft,
                    editor = editor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                        .width(280.dp)
                        .heightIn(max = maxHeight - 16.dp),
                )
            }
            if (panel == Panel.HAPTICS && state.selected == null) {
                HapticsPanel(
                    enabled = state.draft.haptics,
                    editor = editor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                        .width(280.dp)
                        .heightIn(max = maxHeight - 16.dp),
                )
            }
            if (panel == Panel.GYRO && state.selected == null) {
                GyroPanel(
                    setup = state.draft.gyro,
                    available = viewModel.gyroAvailable,
                    measuresTurning = viewModel.gyroMeasuresTurning,
                    editor = editor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                        .width(280.dp)
                        .heightIn(max = maxHeight - 16.dp),
                )
            }
        }

        if (renaming) {
            var name by remember { mutableStateOf(state.draft.name) }
            OverlayDialog(
                title = "Layout name",
                confirmLabel = "Rename",
                confirmEnabled = name.isNotBlank() && !viewModel.isNameTaken(name),
                onConfirm = {
                    editor.rename(name.trim())
                    renaming = false
                },
                onDismiss = { renaming = false },
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = viewModel.isNameTaken(name),
                    supportingText = if (viewModel.isNameTaken(name)) ({ Text("Another layout has this name") }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (confirmDiscard) {
            OverlayDialog(
                title = "Discard changes?",
                confirmLabel = "Discard",
                dismissLabel = "Keep editing",
                onConfirm = onClose,
                onDismiss = { confirmDiscard = false },
            )
        }
    }
}

private fun Area.reaches(top: Float? = null, bottom: Float? = null): Boolean {
    val (areaTop, areaBottom) = when (this) {
        is Area.Circle -> (cy - r) to (cy + r)
        is Area.Rect -> this.top to this.bottom
    }
    return (top != null && areaTop < top) || (bottom != null && areaBottom > bottom)
}

/** The selection outline, its resize handle and the alignment guides. Draws only; touches pass through. */
@Composable
private fun SelectionOverlay(state: EditorState, editor: LayoutEditor) {
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        state.guides.forEach { guide ->
            when (guide) {
                is Guide.Vertical -> drawLine(GamepadColors.Accent, Offset(guide.x, 0f), Offset(guide.x, size.height), 1.5.dp.toPx(), pathEffect = dash)
                is Guide.Horizontal -> drawLine(GamepadColors.Accent, Offset(0f, guide.y), Offset(size.width, guide.y), 1.5.dp.toPx(), pathEffect = dash)
            }
        }
        val selected = state.selected ?: return@Canvas
        val stroke = Stroke(2.dp.toPx())
        when (val area = selected.area(ScreenSize(size.width, size.height))) {
            is Area.Circle -> drawCircle(GamepadColors.Accent, area.r, Offset(area.cx, area.cy), style = stroke)
            is Area.Rect -> drawRect(
                GamepadColors.Accent,
                Offset(area.left, area.top),
                androidx.compose.ui.geometry.Size(area.right - area.left, area.bottom - area.top),
                style = stroke,
            )
        }
        val (hx, hy) = editor.handleOf(selected)
        drawCircle(GamepadColors.Accent, 10.dp.toPx(), Offset(hx, hy))
        drawCircle(GamepadColors.Background, 4.dp.toPx(), Offset(hx, hy))
    }
}

@Composable
private fun Toolbar(
    state: EditorState,
    editor: LayoutEditor,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onFlip: () -> Unit,
    onGyro: () -> Unit,
    onVolumeKeys: (() -> Unit)?,
    onHaptics: (() -> Unit)?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    Row(
        modifier
            .padding(8.dp)
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(50))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose) { Text("✕") }
        TextButton(onClick = onRename, modifier = Modifier.widthIn(max = 180.dp)) {
            Text("${state.draft.name} ✎", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = editor::undo, enabled = state.canUndo) { Text("↶", fontSize = 22.sp) }
        TextButton(onClick = editor::redo, enabled = state.canRedo) { Text("↷", fontSize = 22.sp) }
        if (state.issues.isNotEmpty()) {
            val onlyName = state.issues.all { it is LayoutIssue.BlankName || it is LayoutIssue.DuplicateName }
            val noControls = state.issues.controlIds().isEmpty()
            TextButton(
                onClick = when {
                    onlyName -> onRename
                    noControls && onVolumeKeys != null && state.issues.any { it.isVolumeKeys } -> onVolumeKeys
                    noControls && state.issues.any { it.isGyro } -> onGyro
                    else -> editor::selectNextIssue
                },
            ) {
                Text("⚠ ${state.issues.size}", color = GamepadColors.Warning)
            }
        }
        Box {
            TextButton(onClick = { adding = true }) { Text("+ Add") }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                listOf(
                    Triple("Button", NewControl.Button(ButtonAction.Pad(PadButton.A)), true),
                    Triple("Trigger LT (analog)", NewControl.Throttle(Side.LEFT), true),
                    Triple("Trigger RT (analog)", NewControl.Throttle(Side.RIGHT), true),
                    Triple("Left stick", NewControl.Stick(Side.LEFT), Side.LEFT !in state.stickSides),
                    Triple("Right stick", NewControl.Stick(Side.RIGHT), Side.RIGHT !in state.stickSides),
                    Triple("D-pad", NewControl.Dpad, true),
                ).forEach { (label, kind, enabled) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        enabled = enabled,
                        onClick = {
                            adding = false
                            editor.add(kind)
                        },
                    )
                }
            }
        }
        TextButton(onClick = onGyro) { Text("Gyro") }
        if (onVolumeKeys != null) TextButton(onClick = onVolumeKeys) { Text("Keys") }
        if (onHaptics != null) TextButton(onClick = onHaptics) { Text("Haptics") }
        TextButton(onClick = onFlip) { Text("⇅") }
        Button(onClick = onSave, enabled = state.issues.isEmpty(), modifier = Modifier.padding(start = 4.dp)) {
            Text("Save")
        }
    }
}

@Composable
private fun ControlPanel(control: GamepadControl, state: EditorState, editor: LayoutEditor, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .scrollIndicator(scroll, GamepadColors.Label.copy(alpha = 0.4f))
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(control.kindName(), style = MaterialTheme.typography.titleSmall, color = GamepadColors.Label)

        if (control is GamepadControl.ButtonControl) {
            Field("Sends") {
                // Gyro on/off and Gyro on while held exclude each other, so offer only the kind in use.
                val others = state.draft.controls
                    .filter { it.id != control.id }
                    .mapNotNull { (it as? GamepadControl.ButtonControl)?.action } + state.draft.volumeKeys.actions
                ActionPicker(control.action, blockedGyroSwitches(others)) { it?.let(editor::setAction) }
            }
        }
        val label = when (control) {
            is GamepadControl.ButtonControl -> control.label
            is GamepadControl.MenuControl -> control.label
            is GamepadControl.ThrottleControl -> control.label
            else -> null
        }
        if (label != null) {
            OutlinedTextField(
                value = label,
                onValueChange = editor::setLabel,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (control is GamepadControl.ButtonControl || control is GamepadControl.MenuControl) {
            Field("Shape") {
                Segmented(listOf("Circle", "Rectangle"), if (control.shape is Shape.Circle) 0 else 1) { editor.setShape(round = it == 0) }
            }
        }
        when (control) {
            is GamepadControl.StickControl -> {
                Field("Stick") {
                    val taken = state.stickSides - control.side
                    Segmented(listOf("Left", "Right"), control.side.ordinal, enabled = { Side.entries[it] !in taken }) {
                        editor.setSide(Side.entries[it])
                    }
                }
                StickTuning(control, editor)
            }
            is GamepadControl.ThrottleControl -> Field("Trigger") {
                Segmented(listOf("LT", "RT"), control.side.ordinal) { editor.setSide(Side.entries[it]) }
            }
            else -> Unit
        }
        Field("Anchored to") {
            Segmented(listOf("Left", "Center", "Right"), control.position.anchor.ordinal) { editor.setAnchor(Anchor.entries[it]) }
        }
        Field("Nudge") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("←" to (-1 to 0), "↑" to (0 to -1), "↓" to (0 to 1), "→" to (1 to 0)).forEach { (arrow, step) ->
                    OutlinedButton(
                        onClick = { editor.nudge(step.first, step.second) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(arrow) }
                }
            }
        }
        if (control !is GamepadControl.MenuControl) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = editor::duplicate, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                OutlinedButton(onClick = editor::delete, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = GamepadColors.Error)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GamepadColors.Label.copy(alpha = 0.7f))
        content()
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, enabled: (Int) -> Boolean = { true }, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                enabled = enabled(index),
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(option, maxLines = 1) },
            )
        }
    }
}

private val ALL_ACTIONS: List<ButtonAction> =
    PadButton.entries.map { ButtonAction.Pad(it) } +
        DpadDirection.entries.map { ButtonAction.Dpad(it) } +
        Side.entries.map { ButtonAction.Trigger(it) } +
        GyroButton.entries.map { ButtonAction.Gyro(it) }

private fun ButtonAction.displayName(): String = when (this) {
    is ButtonAction.Pad -> LayoutEditor.defaultLabel(this)
    is ButtonAction.Dpad -> "D-pad ${LayoutEditor.defaultLabel(this)}"
    is ButtonAction.Trigger -> "${LayoutEditor.defaultLabel(this)} (full pull)"
    is ButtonAction.Gyro -> when (action) {
        GyroButton.HOLD -> "Gyro on while held"
        GyroButton.TOGGLE -> "Gyro on/off"
        GyroButton.RECENTER -> "Recenter gyro"
    }
}

/** [none] names a choice of no action, offered first and shown for a null [action]; null offers none. */
@Composable
private fun ActionPicker(
    action: ButtonAction?,
    blocked: Set<ButtonAction>,
    none: String? = null,
    onPick: (ButtonAction?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${action?.displayName() ?: none.orEmpty()} ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (none != null) {
                DropdownMenuItem(
                    text = { Text(none) },
                    onClick = {
                        open = false
                        onPick(null)
                    },
                )
            }
            ALL_ACTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                    enabled = option !in blocked,
                )
            }
        }
    }
}

private fun GamepadControl.kindName(): String = when (this) {
    is GamepadControl.ButtonControl -> "Button"
    is GamepadControl.MenuControl -> "Menu button"
    is GamepadControl.StickControl -> "Stick"
    is GamepadControl.ThrottleControl -> "Analog trigger"
    is GamepadControl.DpadControl -> "D-pad"
}

/** What the volume keys send, for the whole profile. */
@Composable
private fun VolumeKeysPanel(
    draft: LayoutProfile,
    editor: LayoutEditor,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .scrollIndicator(scroll, GamepadColors.Label.copy(alpha = 0.4f))
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Volume keys", style = MaterialTheme.typography.titleSmall, color = GamepadColors.Label)
        Text(
            "A bound key sends its input while held instead of changing the volume. The keys change " +
                "the volume again while the menu is open.",
            style = MaterialTheme.typography.bodySmall,
            color = GamepadColors.Label.copy(alpha = 0.7f),
        )
        Text(
            "Android holds volume key presses back about 150 ms to check for shortcuts like " +
                "volume down + power, so these keys respond later than on-screen buttons. Use them " +
                "for actions where timing matters less, such as gyro hold or recenter.",
            style = MaterialTheme.typography.bodySmall,
            color = GamepadColors.Warning,
        )
        val buttons = draft.controls.mapNotNull { (it as? GamepadControl.ButtonControl)?.action }
        VolumeKey.entries.forEach { key ->
            Field(if (key == VolumeKey.UP) "Volume up" else "Volume down") {
                val other = draft.volumeKeys.with(key, null).actions
                ActionPicker(draft.volumeKeys[key], blockedGyroSwitches(buttons + other), none = "Volume (unbound)") {
                    editor.setVolumeKey(key, it)
                }
            }
        }
    }
}

/** Which outputs tilting the phone drives, for the whole profile. */
@Composable
private fun GyroPanel(
    setup: GyroSetup,
    available: Boolean,
    measuresTurning: Boolean,
    editor: LayoutEditor,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .scrollIndicator(scroll, GamepadColors.Label.copy(alpha = 0.4f))
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gyro", style = MaterialTheme.typography.titleSmall, color = GamepadColors.Label)
        if (!available) {
            Text(
                "This device has no motion sensor. The settings are kept with the layout for other phones.",
                style = MaterialTheme.typography.bodySmall,
                color = GamepadColors.Warning,
            )
        } else if (!measuresTurning) {
            Text(
                "This device has no gyroscope, so tilt comes from the accelerometer: a little slower " +
                    "and shakier, and horizontal tilt is always steering.",
                style = MaterialTheme.typography.bodySmall,
                color = GamepadColors.Warning,
            )
        }
        Text(
            "Without a Gyro on/off or Gyro on while held button, gyro is always on. With one, it " +
                "starts off; a layout can use one of the two, not both. Tilt is measured from the " +
                "angle the phone has when gyro turns on.",
            style = MaterialTheme.typography.bodySmall,
            color = GamepadColors.Label.copy(alpha = 0.7f),
        )
        // Turning cannot be measured from the accelerometer alone, so the choice is hidden there,
        // unless a layout from another phone already uses it and needs switching back.
        if (measuresTurning || !available || setup.horizontal == GyroHorizontal.YAW) {
            Field("Horizontal is") {
                Segmented(listOf("Steering", "Turning"), setup.horizontal.ordinal) {
                    editor.setGyroHorizontal(GyroHorizontal.entries[it])
                }
            }
        }
        Field("Deadzone") {
            Stepper(setup.deadzone, step = 0.5f, bounds = 0f..GyroSetup.MAX_DEADZONE, onChange = editor::setGyroDeadzone)
        }
        GyroTarget.entries.forEach { target -> GyroTargetEditor(target, setup.binding(target), editor) }
    }
}

@Composable
private fun GyroTargetEditor(target: GyroTarget, binding: GyroBinding?, editor: LayoutEditor) {
    val side = if (target == GyroTarget.LEFT_STICK || target == GyroTarget.LEFT_TRIGGER) Side.LEFT else Side.RIGHT
    val range = binding?.range ?: GyroBinding.DEFAULT_RANGE
    when (target) {
        GyroTarget.LEFT_STICK, GyroTarget.RIGHT_STICK -> {
            val stick = binding as? GyroBinding.StickBinding
            val selected = when {
                stick == null -> 0
                stick.horizontal && stick.vertical -> 3
                stick.vertical -> 2
                else -> 1
            }
            Field(if (side == Side.LEFT) "Left stick" else "Right stick") {
                Segmented(listOf("Off", "↔", "↕", "Both"), selected) { index ->
                    editor.setGyroBinding(
                        target,
                        if (index == 0) {
                            null
                        } else {
                            GyroBinding.StickBinding(
                                side = side,
                                horizontal = index == 1 || index == 3,
                                vertical = index == 2 || index == 3,
                                range = range,
                                invertVertical = stick?.invertVertical ?: false,
                            )
                        },
                    )
                }
            }
            if (stick != null) {
                RangeStepper(target, range, editor)
                if (stick.vertical) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Invert vertical", color = GamepadColors.Label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = stick.invertVertical,
                            onCheckedChange = { editor.setGyroBinding(target, stick.copy(invertVertical = it)) },
                        )
                    }
                }
            }
        }
        GyroTarget.LEFT_TRIGGER, GyroTarget.RIGHT_TRIGGER -> {
            val trigger = binding as? GyroBinding.TriggerBinding
            Field(if (side == Side.LEFT) "LT" else "RT") {
                Segmented(listOf("Off", "↔", "↕"), trigger?.axis?.let { it.ordinal + 1 } ?: 0) { index ->
                    editor.setGyroBinding(
                        target,
                        if (index == 0) null else GyroBinding.TriggerBinding(side, GyroAxis.entries[index - 1], trigger?.invert ?: false, range),
                    )
                }
            }
            if (trigger != null) {
                val directions = if (trigger.axis == GyroAxis.HORIZONTAL) listOf("Right", "Left") else listOf("Up", "Down")
                Field("Pulls when tilted") {
                    Segmented(directions, if (trigger.invert) 1 else 0) {
                        editor.setGyroBinding(target, trigger.copy(invert = it == 1))
                    }
                }
                RangeStepper(target, range, editor)
            }
        }
    }
}

@Composable
private fun RangeStepper(target: GyroTarget, range: Float, editor: LayoutEditor) {
    Field("Full at") {
        Stepper(range, step = 5f, bounds = GyroBinding.MIN_RANGE..GyroBinding.MAX_RANGE) { editor.setGyroRange(target, it) }
    }
}

/**
 * − value + in degrees. Not a Slider: Material's Slider consumes the touch-down, so a swipe that
 * starts on it neither moves it nor scrolls the panel, and these panels are mostly settings.
 * Steps land on multiples of [step], so an odd stored value rounds to the grid on the first tap.
 */
@Composable
private fun Stepper(
    value: Float,
    step: Float,
    bounds: ClosedFloatingPointRange<Float>,
    format: (Float) -> String = ::degrees,
    onChange: (Float) -> Unit,
) {
    val down = (ceil(value / step - 0.001f) - 1) * step
    val up = (floor(value / step + 0.001f) + 1) * step
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onChange(down.coerceIn(bounds)) }, enabled = value > bounds.start, modifier = Modifier.weight(1f)) {
            Text("−")
        }
        Text(format(value), color = GamepadColors.Label, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
        OutlinedButton(onClick = { onChange(up.coerceIn(bounds)) }, enabled = value < bounds.endInclusive, modifier = Modifier.weight(1f)) {
            Text("+")
        }
    }
}

private fun degrees(value: Float): String = "${(value * 10).roundToInt() / 10f}°"

private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

private fun curve(value: Float): String =
    if (value <= GamepadControl.StickControl.MIN_CURVE + 0.001f) "Linear" else "${(value * 100).roundToInt() / 100f}"

/** How one stick turns thumb travel into output. Steppers, not sliders, as in the other panels. */
@Composable
private fun StickTuning(stick: GamepadControl.StickControl, editor: LayoutEditor) {
    Field("Deadzone") {
        Stepper(stick.deadzone, step = 0.02f, bounds = 0f..GamepadControl.StickControl.MAX_DEADZONE, format = ::percent, onChange = editor::setStickDeadzone)
    }
    Field("Sensitivity curve") {
        Stepper(
            stick.curveExponent,
            step = 0.25f,
            bounds = GamepadControl.StickControl.MIN_CURVE..GamepadControl.StickControl.MAX_CURVE,
            format = ::curve,
            onChange = editor::setStickCurve,
        )
    }
    Text(
        "Higher curves give finer control near the center.",
        style = MaterialTheme.typography.bodySmall,
        color = GamepadColors.Label.copy(alpha = 0.7f),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Floating", color = GamepadColors.Label, modifier = Modifier.weight(1f))
        Switch(checked = stick.floating, onCheckedChange = editor::setStickFloating)
    }
    Text(
        "A floating stick centers where your thumb lands instead of at its drawn position.",
        style = MaterialTheme.typography.bodySmall,
        color = GamepadColors.Label.copy(alpha = 0.7f),
    )
}

/** Vibration on press, for the whole profile. */
@Composable
private fun HapticsPanel(enabled: Boolean, editor: LayoutEditor, modifier: Modifier = Modifier) {
    Column(
        modifier
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Haptics", style = MaterialTheme.typography.titleSmall, color = GamepadColors.Label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vibrate on press", color = GamepadColors.Label, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = editor::setHaptics)
        }
    }
}
