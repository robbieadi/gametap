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
import io.github.robbieadi.gametap.layout.LayoutEditor
import io.github.robbieadi.gametap.layout.LayoutIssue
import io.github.robbieadi.gametap.layout.NewControl
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.area
import io.github.robbieadi.gametap.layout.centerX
import io.github.robbieadi.gametap.layout.controlIds
import io.github.robbieadi.gametap.ui.components.GamepadCanvas
import io.github.robbieadi.gametap.ui.components.OverlayDialog
import io.github.robbieadi.gametap.ui.components.consumeAllTouches
import io.github.robbieadi.gametap.ui.theme.GamepadColors
import io.github.robbieadi.gametap.viewmodel.EditorViewModel
import kotlinx.coroutines.launch

/** Height of the band the toolbar occupies, used to move it out of the selected control's way. */
private val TOOLBAR_BAND = 72.dp

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

        fun close() {
            if (viewModel.hasUnsavedChanges()) confirmDiscard = true else onClose()
        }

        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = {
                when {
                    renaming -> renaming = false
                    confirmDiscard -> confirmDiscard = false
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    Row(
        modifier
            .padding(8.dp)
            .consumeAllTouches()
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
            TextButton(onClick = if (onlyName) onRename else editor::selectNextIssue) {
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
        TextButton(onClick = onFlip) { Text("⇅") }
        Button(onClick = onSave, enabled = state.issues.isEmpty(), modifier = Modifier.padding(start = 4.dp)) {
            Text("Save")
        }
    }
}

@Composable
private fun ControlPanel(control: GamepadControl, state: EditorState, editor: LayoutEditor, modifier: Modifier = Modifier) {
    Column(
        modifier
            .consumeAllTouches()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(control.kindName(), style = MaterialTheme.typography.titleSmall, color = GamepadColors.Label)

        if (control is GamepadControl.ButtonControl) {
            Field("Sends") { ActionPicker(control.action, editor::setAction) }
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
            is GamepadControl.StickControl -> Field("Stick") {
                val taken = state.stickSides - control.side
                Segmented(listOf("Left", "Right"), control.side.ordinal, enabled = { Side.entries[it] !in taken }) {
                    editor.setSide(Side.entries[it])
                }
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
        Side.entries.map { ButtonAction.Trigger(it) }

private fun ButtonAction.displayName(): String = when (this) {
    is ButtonAction.Pad -> LayoutEditor.defaultLabel(this)
    is ButtonAction.Dpad -> "D-pad ${LayoutEditor.defaultLabel(this)}"
    is ButtonAction.Trigger -> "${LayoutEditor.defaultLabel(this)} (full pull)"
}

@Composable
private fun ActionPicker(action: ButtonAction, onPick: (ButtonAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("${action.displayName()} ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ALL_ACTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
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
