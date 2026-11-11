package io.github.robbieadi.gametap.layout

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.DpadControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import io.github.robbieadi.gametap.layout.GamepadControl.ThrottleControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Sizes that depend on screen density, in pixels. */
data class EditorMetrics(
    /** Half of the smallest width or height a control may have. */
    val minHalfPx: Float,
    /** How close an edge or center must come to another before it snaps to it. */
    val snapPx: Float,
    /** Touch radius of the resize handle. */
    val handlePx: Float,
)

/** A line two controls line up on, in pixels. */
sealed interface Guide {
    data class Vertical(val x: Float) : Guide

    data class Horizontal(val y: Float) : Guide
}

/** An entry in the editor's add palette. */
sealed interface NewControl {
    data class Button(val action: ButtonAction) : NewControl

    data class Throttle(val side: Side) : NewControl

    data class Stick(val side: Side) : NewControl

    data object Dpad : NewControl
}

data class EditorState(
    val draft: LayoutProfile,
    val selectedId: String? = null,
    /** Shown while dragging. */
    val guides: List<Guide> = emptyList(),
    /** Everything that blocks saving. */
    val issues: List<LayoutIssue> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val selected: GamepadControl? get() = draft.controls.firstOrNull { it.id == selectedId }

    /** Sides that already have a stick, so the palette can grey them out. */
    val stickSides: Set<Side> get() = draft.controls.filterIsInstance<StickControl>().mapTo(HashSet()) { it.side }
}

/**
 * The layout editor's model: a draft profile, the selection and an undo history. Gestures come in
 * as pixels; everything else is a plain call. Every change produces a new draft, so undo just
 * keeps the old ones. No Compose dependency, so it can be unit tested.
 *
 * Touch flow: [press] on finger down, then [dragTo] for each move once the finger has moved past
 * the touch slop, then [release]. A press without a drag is a tap.
 */
class LayoutEditor(
    initial: LayoutProfile,
    private var screen: ScreenSize,
    private val metrics: EditorMetrics,
    private val otherNames: Set<String>,
    private val newId: () -> String,
) {
    private sealed interface Gesture {
        data object None : Gesture

        /** [below] is selected instead if the press turns out to be a tap. */
        class Move(val id: String, val startX: Float, val startY: Float, val pressX: Float, val pressY: Float, val below: String?) : Gesture

        class Resize(val id: String) : Gesture

        data object Empty : Gesture
    }

    private val undo = ArrayDeque<LayoutProfile>()
    private val redo = ArrayDeque<LayoutProfile>()
    private var gesture: Gesture = Gesture.None
    private var gesturePushed = false
    /** Consecutive edits with the same key (typing a label) share one undo step. */
    private var lastEditKey: String? = null

    private val mutableState = MutableStateFlow(stateFor(initial, selectedId = null))
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    private val draft get() = mutableState.value.draft
    private val h get() = screen.height

    fun resize(screen: ScreenSize) {
        if (screen == this.screen) return
        this.screen = screen
        publish(draft)
    }

    // ---------- Gestures ----------

    fun press(x: Float, y: Float) {
        gesturePushed = false
        lastEditKey = null
        val selected = mutableState.value.selected
        val handle = selected?.let { handleOf(it) }
        if (selected != null && handle != null && hypot(x - handle.first, y - handle.second) <= metrics.handlePx) {
            gesture = Gesture.Resize(selected.id)
            return
        }
        val hits = draft.controls.asReversed().filter { it.area(screen).contains(x, y) }
        if (hits.isEmpty()) {
            gesture = Gesture.Empty
            return
        }
        val current = hits.indexOfFirst { it.id == mutableState.value.selectedId }
        val target = if (current >= 0) hits[current] else hits.first()
        val below = if (current >= 0) hits[(current + 1) % hits.size].id.takeIf { hits.size > 1 } else null
        if (current < 0) select(target.id)
        val center = centerOf(target)
        gesture = Gesture.Move(target.id, center.first, center.second, x, y, below)
    }

    fun dragTo(x: Float, y: Float) {
        when (val g = gesture) {
            is Gesture.Move -> {
                beginGestureChange()
                val control = find(g.id) ?: return
                val (cx, cy, guides) = snap(control, g.startX + x - g.pressX, g.startY + y - g.pressY)
                replace(control.withPosition(positionAt(cx, cy, autoAnchor(cx))), guides)
            }
            is Gesture.Resize -> {
                beginGestureChange()
                val control = find(g.id) ?: return
                replace(control.resizedTo(x, y))
            }
            Gesture.Empty, Gesture.None -> Unit
        }
    }

    fun release(dragged: Boolean) {
        val g = gesture
        gesture = Gesture.None
        if (!dragged) {
            when (g) {
                is Gesture.Move -> g.below?.let { select(it) }
                Gesture.Empty -> select(null)
                else -> Unit
            }
        }
        if (mutableState.value.guides.isNotEmpty()) mutableState.value = mutableState.value.copy(guides = emptyList())
    }

    /** Where the resize handle of [control] sits, in pixels. */
    fun handleOf(control: GamepadControl): Pair<Float, Float> = when (val area = control.area(screen)) {
        is Area.Circle -> (area.cx + area.r * DIAGONAL) to (area.cy + area.r * DIAGONAL)
        is Area.Rect -> area.right to area.bottom
    }

    // ---------- Selection ----------

    fun select(id: String?) {
        mutableState.value = mutableState.value.copy(selectedId = id?.takeIf { find(it) != null })
    }

    /** Selects the next control named by an issue, after the current selection. */
    fun selectNextIssue() {
        val flagged = mutableState.value.issues.controlIds()
        val ordered = draft.controls.map { it.id }.filter { it in flagged }
        if (ordered.isEmpty()) return
        val next = ordered.getOrNull(ordered.indexOf(mutableState.value.selectedId) + 1) ?: ordered.first()
        select(next)
    }

    // ---------- Edits on the selected control ----------

    fun setAction(action: ButtonAction) = editSelected { control ->
        if (control !is ButtonControl) return@editSelected control
        // A label still at its default follows the new action.
        val label = if (control.label == defaultLabel(control.action)) defaultLabel(action) else control.label
        control.copy(action = action, label = label)
    }

    fun setLabel(label: String) = editSelected(key = "label:${mutableState.value.selectedId}") { control ->
        when (control) {
            is ButtonControl -> control.copy(label = label)
            is MenuControl -> control.copy(label = label)
            is ThrottleControl -> control.copy(label = label)
            else -> control
        }
    }

    fun setShape(round: Boolean) = editSelected { control ->
        when (control) {
            is ButtonControl -> control.copy(shape = control.shape.converted(round))
            is MenuControl -> control.copy(shape = control.shape.converted(round))
            else -> control
        }
    }

    /** Keeps the control where it is on this screen; only how it follows other screens changes. */
    fun setAnchor(anchor: Anchor) = editSelected { control ->
        val (cx, cy) = centerOf(control)
        control.withPosition(positionAt(cx, cy, anchor))
    }

    fun setSide(side: Side) = editSelected { control ->
        when (control) {
            is StickControl -> control.copy(side = side)
            is ThrottleControl -> control.copy(side = side, label = if (control.label == triggerLabel(control.side)) triggerLabel(side) else control.label)
            else -> control
        }
    }

    /** Moves by whole grid steps, for positioning finer than a finger allows. */
    fun nudge(dx: Int, dy: Int) = editSelected { control ->
        val (cx, cy) = centerOf(control)
        val step = GRID * h
        control.withPosition(positionAt(cx + dx * step, cy + dy * step, control.position.anchor))
    }

    fun rename(name: String) = edit(key = "name") { it.copy(name = name) }

    // ---------- Adding and removing ----------

    fun add(kind: NewControl) {
        val template = when (kind) {
            is NewControl.Button -> ButtonControl(newId(), ORIGIN, Shape.Circle(0.08f), kind.action, defaultLabel(kind.action))
            is NewControl.Throttle -> ThrottleControl(newId(), ORIGIN, kind.side, 0.10f, 0.12f, triggerLabel(kind.side))
            is NewControl.Stick -> StickControl(newId(), ORIGIN, kind.side, radius = 0.20f * STICK_BASE_RATIO, grabRadius = 0.20f)
            NewControl.Dpad -> DpadControl(newId(), ORIGIN, 0.17f)
        }
        place(template)
    }

    /** Copies the selected control into free space. The menu control cannot be copied. */
    fun duplicate() {
        val control = mutableState.value.selected ?: return
        if (control is MenuControl) return
        place(control.withId(newId()))
    }

    /** Deletes the selected control. The menu control cannot be deleted. */
    fun delete() {
        val control = mutableState.value.selected ?: return
        if (control is MenuControl) return
        edit { profile -> profile.copy(controls = profile.controls.filterNot { it.id == control.id }) }
        select(null)
    }

    // ---------- History ----------

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(draft)
        lastEditKey = null
        publish(previous)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(draft)
        lastEditKey = null
        publish(next)
    }

    // ---------- Internals ----------

    private fun find(id: String) = draft.controls.firstOrNull { it.id == id }

    private fun stateFor(profile: LayoutProfile, selectedId: String?, guides: List<Guide> = emptyList()) = EditorState(
        draft = profile,
        selectedId = selectedId?.takeIf { id -> profile.controls.any { it.id == id } },
        guides = guides,
        issues = profile.validate(screen, otherNames),
        canUndo = undo.isNotEmpty(),
        canRedo = redo.isNotEmpty(),
    )

    private fun publish(profile: LayoutProfile, guides: List<Guide> = emptyList()) {
        mutableState.value = stateFor(profile, mutableState.value.selectedId, guides)
    }

    private fun push(key: String? = null) {
        if (key != null && key == lastEditKey) return
        lastEditKey = key
        undo.addLast(draft)
        if (undo.size > MAX_UNDO) undo.removeFirst()
        redo.clear()
    }

    private fun edit(key: String? = null, transform: (LayoutProfile) -> LayoutProfile) {
        val next = transform(draft)
        if (next == draft) return
        push(key)
        publish(next)
    }

    private fun editSelected(key: String? = null, transform: (GamepadControl) -> GamepadControl) {
        val id = mutableState.value.selectedId ?: return
        edit(key) { profile -> profile.copy(controls = profile.controls.map { if (it.id == id) transform(it) else it }) }
    }

    /** One undo step per drag, taken at its first movement so a plain tap leaves no step. */
    private fun beginGestureChange() {
        if (gesturePushed) return
        gesturePushed = true
        push()
    }

    private fun replace(control: GamepadControl, guides: List<Guide> = emptyList()) {
        publish(draft.copy(controls = draft.controls.map { if (it.id == control.id) control else it }), guides)
    }

    private fun place(template: GamepadControl) {
        val spot = freeSpot(template)
        val control = template.withPosition(positionAt(spot.first, spot.second, autoAnchor(spot.first)))
        edit { it.copy(controls = it.controls + control) }
        select(control.id)
    }

    private fun centerOf(control: GamepadControl): Pair<Float, Float> =
        control.position.centerX(screen) to control.position.centerY(screen)

    private fun autoAnchor(cx: Float): Anchor = when {
        cx < screen.width / 3f -> Anchor.LEFT
        cx > screen.width * 2f / 3f -> Anchor.RIGHT
        else -> Anchor.CENTER
    }

    private fun positionAt(cx: Float, cy: Float, anchor: Anchor): Position {
        val x = when (anchor) {
            Anchor.LEFT -> cx / h
            Anchor.CENTER -> (cx - screen.width / 2f) / h
            Anchor.RIGHT -> (screen.width - cx) / h
        }
        return Position(anchor, x, cy / h)
    }

    /** Half width and half height in pixels. */
    private fun extentsOf(control: GamepadControl): Pair<Float, Float> = when (val area = control.area(screen)) {
        is Area.Circle -> area.r to area.r
        is Area.Rect -> (area.right - area.left) / 2f to (area.bottom - area.top) / 2f
    }

    /**
     * Lines the moving control up with another one's edge or center, or with the middle of the
     * screen, when one is within [EditorMetrics.snapPx]. Otherwise rounds to the grid. Each axis
     * is handled on its own.
     */
    private fun snap(control: GamepadControl, rawX: Float, rawY: Float): Triple<Float, Float, List<Guide>> {
        val cx = rawX.coerceIn(0f, screen.width)
        val cy = rawY.coerceIn(0f, screen.height)
        val (hw, hh) = extentsOf(control)
        val others = draft.controls.filter { it.id != control.id }
        val xTargets = others.flatMap { o -> val (ox, _) = centerOf(o); val (ow, _) = extentsOf(o); listOf(ox - ow, ox, ox + ow) } + (screen.width / 2f)
        val yTargets = others.flatMap { o -> val (_, oy) = centerOf(o); val (_, oh) = extentsOf(o); listOf(oy - oh, oy, oy + oh) } + (screen.height / 2f)
        val guides = ArrayList<Guide>()
        val x = snapAxis(cx, hw, xTargets)?.let { (shift, line) -> guides += Guide.Vertical(line); cx + shift } ?: grid(cx)
        val y = snapAxis(cy, hh, yTargets)?.let { (shift, line) -> guides += Guide.Horizontal(line); cy + shift } ?: grid(cy)
        return Triple(x, y, guides)
    }

    /** The smallest shift that puts an edge or the center of the control on a target, and that target. */
    private fun snapAxis(center: Float, half: Float, targets: List<Float>): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        for (own in listOf(center - half, center, center + half)) {
            for (target in targets) {
                val shift = target - own
                if (abs(shift) <= metrics.snapPx && (best == null || abs(shift) < abs(best.first))) best = shift to target
            }
        }
        return best
    }

    private fun grid(value: Float): Float {
        val step = GRID * h
        return (value / step).roundToInt() * step
    }

    private fun GamepadControl.resizedTo(x: Float, y: Float): GamepadControl {
        val (cx, cy) = centerOf(this)
        val min = metrics.minHalfPx / h
        fun size(px: Float) = (px / h).coerceIn(min, MAX_HALF)
        val radius = size(hypot(x - cx, y - cy))
        val halfW = size(abs(x - cx))
        val halfH = size(abs(y - cy))
        return when (this) {
            is ButtonControl -> copy(shape = shape.resized(radius, halfW, halfH))
            is MenuControl -> copy(shape = shape.resized(radius, halfW, halfH))
            is StickControl -> copy(grabRadius = radius, radius = radius * STICK_BASE_RATIO)
            is ThrottleControl -> copy(halfW = halfW, halfH = halfH)
            is DpadControl -> copy(radius = radius)
        }
    }

    private fun Shape.resized(radius: Float, halfW: Float, halfH: Float): Shape = when (this) {
        is Shape.Circle -> Shape.Circle(radius)
        is Shape.Rect -> Shape.Rect(halfW, halfH)
    }

    private fun Shape.converted(round: Boolean): Shape = when {
        round && this is Shape.Rect -> Shape.Circle(max(halfW, halfH))
        !round && this is Shape.Circle -> Shape.Rect(r, r)
        else -> this
    }

    /**
     * The open spot furthest from every other control, on a grid of candidate centers, kept a
     * little off the screen edges. Falls back
     * to the middle of the screen when nothing fits; the overlap is then flagged like any other.
     */
    private fun freeSpot(control: GamepadControl): Pair<Float, Float> {
        val others = draft.controls.map { it.area(screen) }
        val step = CANDIDATE_STEP * h
        val margin = EDGE_MARGIN * h
        val inset = ScreenSize(screen.width - 2 * margin, screen.height - 2 * margin)
        var best: Pair<Float, Float>? = null
        var bestScore = -1f
        var cy = step
        while (cy < screen.height) {
            var cx = step
            while (cx < screen.width) {
                val area = control.withPosition(positionAt(cx, cy, Anchor.LEFT)).area(screen)
                if (area.shifted(-margin).isInside(inset) && others.none { it.overlaps(area) }) {
                    val score = others.minOfOrNull { clearance(cx, cy, it) } ?: Float.MAX_VALUE
                    if (score > bestScore) {
                        bestScore = score
                        best = cx to cy
                    }
                }
                cx += step
            }
            cy += step
        }
        return best ?: (screen.width / 2f to screen.height / 2f)
    }

    /** Distance from a point to the edge of [area], an approximation good enough to rank spots. */
    private fun clearance(x: Float, y: Float, area: Area): Float = when (area) {
        is Area.Circle -> hypot(x - area.cx, y - area.cy) - area.r
        is Area.Rect -> hypot(max(max(area.left - x, x - area.right), 0f), max(max(area.top - y, y - area.bottom), 0f))
    }

    companion object {
        /** A stick's drawn base as a share of its touch area, as in the default layout. */
        const val STICK_BASE_RATIO: Float = 0.85f
        /** Grid step for snapping and nudging, in screen heights. */
        const val GRID: Float = 0.01f
        private const val MAX_HALF = 0.5f
        private const val CANDIDATE_STEP = 0.02f
        private const val EDGE_MARGIN = 0.02f
        private const val MAX_UNDO = 100
        private const val DIAGONAL = 0.70710677f
        private val ORIGIN = Position(Anchor.LEFT, 0f, 0f)

        fun defaultLabel(action: ButtonAction): String = when (action) {
            is ButtonAction.Pad -> when (action.button) {
                Button.SELECT -> "Select"
                Button.START -> "Start"
                Button.HOME -> "Home"
                else -> action.button.name
            }
            is ButtonAction.Dpad -> when (action.direction) {
                DpadDirection.UP -> "↑"
                DpadDirection.DOWN -> "↓"
                DpadDirection.LEFT -> "←"
                DpadDirection.RIGHT -> "→"
            }
            is ButtonAction.Trigger -> triggerLabel(action.side)
        }

        private fun triggerLabel(side: Side) = if (side == Side.LEFT) "LT" else "RT"
    }
}

private fun Area.shifted(by: Float): Area = when (this) {
    is Area.Circle -> copy(cx = cx + by, cy = cy + by)
    is Area.Rect -> Area.Rect(left + by, top + by, right + by, bottom + by)
}

private fun GamepadControl.withPosition(position: Position): GamepadControl = when (this) {
    is ButtonControl -> copy(position = position)
    is MenuControl -> copy(position = position)
    is StickControl -> copy(position = position)
    is ThrottleControl -> copy(position = position)
    is DpadControl -> copy(position = position)
}

private fun GamepadControl.withId(id: String): GamepadControl = when (this) {
    is ButtonControl -> copy(id = id)
    is MenuControl -> copy(id = id)
    is StickControl -> copy(id = id)
    is ThrottleControl -> copy(id = id)
    is DpadControl -> copy(id = id)
}
