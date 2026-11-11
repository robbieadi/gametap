package io.github.robbieadi.gametap.input

import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.domain.Stick
import io.github.robbieadi.gametap.layout.Area
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.DpadDirection
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.DpadControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import io.github.robbieadi.gametap.layout.GamepadControl.ThrottleControl
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.area
import io.github.robbieadi.gametap.layout.contains
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** A point in pixels. */
data class TouchPoint(val x: Float, val y: Float)

data class StickView(val origin: TouchPoint, val value: Stick)

/**
 * How each control should look, by control id. Kept per control, not per gamepad input, so two
 * A buttons light up separately and the D-pad cross shows only its own direction.
 */
data class TouchView(
    /** Buttons and the menu control under a finger. */
    val pressed: Set<String> = emptySet(),
    val dpads: Map<String, Dpad> = emptyMap(),
    val throttles: Map<String, Float> = emptyMap(),
    val sticks: Map<String, StickView> = emptyMap(),
    /** A finger rests on the menu control. The screen times the long press. */
    val menuHeld: Boolean = false,
) {
    fun isPressed(control: GamepadControl): Boolean = control.id in pressed
}

/**
 * Turns raw multi-touch events into [GamepadState].
 *
 * Sticks, throttles and the D-pad cross capture a pointer until it lifts. Any other pointer is
 * free: it presses whichever button is under it, which gives slide-on and slide-off. Where
 * controls overlap, the one drawn on top (later in the list) owns the touch. The state is rebuilt
 * from all live pointers after each event, so controls that send the same input combine: buttons
 * and D-pad directions add up, and a trigger takes its highest value.
 *
 * Everything is in pixels and has no Compose dependency, so it can be unit tested.
 */
class GamepadTouchTracker(
    layout: LayoutProfile,
    private val input: InputStateHolder,
    private val floatingSticks: () -> Boolean = { false },
    private val onPress: () -> Unit = {},
) {
    private class Placed(val control: GamepadControl, val area: Area)

    private class Pointer(var x: Float, var y: Float, val captured: Placed?, val origin: TouchPoint?)

    private var layout = layout
    private var screen = ScreenSize(1f, 1f)

    /** Topmost first, so the first hit is the control drawn on top. */
    private var placed: List<Placed> = place()
    private val pointers = LinkedHashMap<Long, Pointer>()
    private var lastDigital = 0L

    private val mutableView = MutableStateFlow(TouchView())

    val view: StateFlow<TouchView> = mutableView.asStateFlow()

    /** Switching profiles drops every touch, so nothing stays held from controls that are gone. */
    fun setLayout(layout: LayoutProfile) {
        if (layout == this.layout) return
        this.layout = layout
        pointers.clear()
        placed = place()
        publish()
    }

    fun resize(width: Float, height: Float) {
        val next = ScreenSize(width, height)
        if (next == screen) return
        screen = next
        placed = place()
    }

    fun down(id: Long, x: Float, y: Float) {
        val hit = topmostAt(x, y)
        val captured = hit?.takeIf { it.captures() && pointers.values.none { p -> p.captured === it } }
        val origin = (captured?.control as? StickControl)?.let {
            if (floatingSticks()) TouchPoint(x, y) else centerOf(captured)
        }
        pointers[id] = Pointer(x, y, captured, origin)
        publish()
    }

    fun move(id: Long, x: Float, y: Float) {
        val pointer = pointers[id] ?: return
        pointer.x = x
        pointer.y = y
        publish()
    }

    fun up(id: Long) {
        if (pointers.remove(id) != null) publish()
    }

    fun releaseAll() {
        pointers.clear()
        publish()
    }

    private fun place(): List<Placed> = layout.controls.map { Placed(it, it.area(screen)) }.asReversed()

    private fun topmostAt(x: Float, y: Float): Placed? = placed.firstOrNull { it.area.contains(x, y) }

    private fun Placed.captures(): Boolean =
        control is StickControl || control is ThrottleControl || control is DpadControl

    private fun centerOf(placed: Placed): TouchPoint = when (val area = placed.area) {
        is Area.Circle -> TouchPoint(area.cx, area.cy)
        is Area.Rect -> TouchPoint((area.left + area.right) / 2f, (area.top + area.bottom) / 2f)
    }

    private fun publish() {
        var buttons = 0
        var dpad = Dpad()
        var leftTrigger = 0f
        var rightTrigger = 0f
        var leftStick = Stick.Center
        var rightStick = Stick.Center
        var triggerButtons = 0
        val pressed = HashSet<String>()
        val dpads = HashMap<String, Dpad>()
        val throttles = HashMap<String, Float>()
        val sticks = HashMap<String, StickView>()

        var menuHeld = false

        fun pullTrigger(side: Side, value: Float) {
            when (side) {
                Side.LEFT -> leftTrigger = max(leftTrigger, value)
                Side.RIGHT -> rightTrigger = max(rightTrigger, value)
            }
        }

        for (pointer in pointers.values) {
            val captured = pointer.captured
            if (captured == null) {
                when (val control = topmostAt(pointer.x, pointer.y)?.control) {
                    is ButtonControl -> {
                        pressed += control.id
                        when (val action = control.action) {
                            is ButtonAction.Pad -> buttons = buttons or action.button.mask
                            is ButtonAction.Dpad -> dpad = dpad + action.direction
                            is ButtonAction.Trigger -> {
                                pullTrigger(action.side, 1f)
                                triggerButtons = triggerButtons or (1 shl action.side.ordinal)
                            }
                        }
                    }
                    is MenuControl -> {
                        pressed += control.id
                        menuHeld = true
                    }
                    else -> Unit // Empty space, or an analog control already held by another finger.
                }
                continue
            }
            when (val control = captured.control) {
                is StickControl -> {
                    val origin = pointer.origin ?: centerOf(captured)
                    val value = stickValue(pointer, origin, control.radius * screen.height)
                    sticks[control.id] = StickView(origin, value)
                    when (control.side) {
                        Side.LEFT -> leftStick = value
                        Side.RIGHT -> rightStick = value
                    }
                }
                is ThrottleControl -> {
                    val value = throttleValue(pointer, captured.area)
                    throttles[control.id] = value
                    pullTrigger(control.side, value)
                }
                is DpadControl -> {
                    val value = dpadValue(pointer, centerOf(captured), control.radius * screen.height)
                    dpads[control.id] = value
                    dpad = dpad or value
                }
                is ButtonControl, is MenuControl -> Unit // Never captured.
            }
        }

        // Every digital input in one mask, so a newly pressed one of any kind gives one buzz.
        val digital = buttons.toLong() or (dpad.bits().toLong() shl 32) or (triggerButtons.toLong() shl 40)
        if (digital and lastDigital.inv() != 0L) onPress()
        lastDigital = digital

        mutableView.value = TouchView(pressed, dpads, throttles, sticks, menuHeld)
        input.set(GamepadState(buttons, dpad, leftStick, rightStick, leftTrigger, rightTrigger))
    }

    private fun stickValue(pointer: Pointer, origin: TouchPoint, radius: Float): Stick {
        val dx = (pointer.x - origin.x) / radius
        val dy = (pointer.y - origin.y) / radius
        val magnitude = hypot(dx, dy)
        return if (magnitude > 1f) Stick(dx / magnitude, dy / magnitude) else Stick(dx, dy)
    }

    /** Further down the control pulls harder. Any touch registers at least [THROTTLE_MIN]. */
    private fun throttleValue(pointer: Pointer, area: Area): Float {
        val rect = area as Area.Rect
        val fraction = (pointer.y - rect.top) / (rect.bottom - rect.top)
        return fraction.coerceIn(THROTTLE_MIN, 1f)
    }

    /** Eight 45-degree sectors, with a small neutral spot in the center. */
    private fun dpadValue(pointer: Pointer, center: TouchPoint, radius: Float): Dpad {
        val dx = pointer.x - center.x
        val dy = pointer.y - center.y
        if (hypot(dx, dy) < radius * DPAD_NEUTRAL) return Dpad()
        val horizontal = abs(dx) > abs(dy) * TAN_22_5
        val vertical = abs(dy) > abs(dx) * TAN_22_5
        return Dpad(
            up = vertical && dy < 0,
            down = vertical && dy > 0,
            left = horizontal && dx < 0,
            right = horizontal && dx > 0,
        )
    }

    private operator fun Dpad.plus(direction: DpadDirection): Dpad = when (direction) {
        DpadDirection.UP -> copy(up = true)
        DpadDirection.DOWN -> copy(down = true)
        DpadDirection.LEFT -> copy(left = true)
        DpadDirection.RIGHT -> copy(right = true)
    }

    private infix fun Dpad.or(other: Dpad): Dpad =
        Dpad(up || other.up, down || other.down, left || other.left, right || other.right)

    private fun Dpad.bits(): Int =
        (if (up) 1 else 0) or (if (down) 2 else 0) or (if (left) 4 else 0) or (if (right) 8 else 0)

    private companion object {
        const val THROTTLE_MIN = 0.15f
        const val DPAD_NEUTRAL = 0.2f
        const val TAN_22_5 = 0.41421357f
    }
}
