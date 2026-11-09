package io.github.robbieadi.gametap.input

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadControl
import io.github.robbieadi.gametap.domain.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.domain.GamepadControl.DpadControl
import io.github.robbieadi.gametap.domain.GamepadControl.StickControl
import io.github.robbieadi.gametap.domain.GamepadControl.TriggerControl
import io.github.robbieadi.gametap.domain.GamepadLayout
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.domain.Side
import io.github.robbieadi.gametap.domain.Stick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot

/** A point in pixels. */
data class TouchPoint(val x: Float, val y: Float)

/**
 * Turns raw multi-touch events into [GamepadState].
 *
 * Sticks, triggers and the D-pad capture a pointer until it lifts. Any other pointer is free:
 * it presses whichever button is under it, which gives slide-on and slide-off. The state is
 * recomputed from all live pointers after each event, so two fingers on one button stay
 * consistent. Everything is in pixels and has no Compose dependency, so it can be unit tested.
 */
class GamepadTouchTracker(
    private val layout: GamepadLayout,
    private val input: InputStateHolder,
    private val floatingSticks: () -> Boolean = { false },
    private val onPress: () -> Unit = {},
) {
    private class Pointer(var x: Float, var y: Float, val control: GamepadControl?, val origin: TouchPoint?)

    private val pointers = LinkedHashMap<Long, Pointer>()
    private var width = 1f
    private var height = 1f
    private var lastButtons = 0
    private var lastDpad = Dpad()

    private val mutableStickOrigins = MutableStateFlow<Map<Side, TouchPoint>>(emptyMap())

    /** Where each held stick is anchored, for drawing floating sticks. */
    val stickOrigins: StateFlow<Map<Side, TouchPoint>> = mutableStickOrigins.asStateFlow()

    fun resize(width: Float, height: Float) {
        this.width = width
        this.height = height
    }

    fun down(id: Long, x: Float, y: Float) {
        val control = capturableAt(x, y)
        val origin = (control as? StickControl)?.let { stick ->
            if (floatingSticks()) TouchPoint(x, y) else centerOf(stick)
        }
        pointers[id] = Pointer(x, y, control, origin)
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

    /** Buttons win over the larger analog regions. A control already held is not captured twice. */
    private fun capturableAt(x: Float, y: Float): GamepadControl? {
        if (buttonAt(x, y) != null) return null
        val held = pointers.values.mapNotNull { it.control }
        return layout.controls.firstOrNull { control ->
            control !is ButtonControl && control !in held && control.region.contains(x, y, width, height)
        }
    }

    private fun buttonAt(x: Float, y: Float): Button? =
        layout.controls.filterIsInstance<ButtonControl>()
            .firstOrNull { it.region.contains(x, y, width, height) }
            ?.button

    private fun centerOf(control: GamepadControl) =
        TouchPoint(control.region.cx * width, control.region.cy * height)

    private fun publish() {
        var state = GamepadState()
        val origins = HashMap<Side, TouchPoint>()
        for (pointer in pointers.values) {
            when (val control = pointer.control) {
                null -> buttonAt(pointer.x, pointer.y)?.let {
                    state = state.copy(buttons = state.buttons or it.mask)
                }
                is StickControl -> {
                    val origin = pointer.origin ?: centerOf(control)
                    origins[control.side] = origin
                    val stick = stickValue(pointer, origin, control.radius * height)
                    state = when (control.side) {
                        Side.LEFT -> state.copy(leftStick = stick)
                        Side.RIGHT -> state.copy(rightStick = stick)
                    }
                }
                is TriggerControl -> {
                    val value = triggerValue(pointer, control)
                    state = when (control.side) {
                        Side.LEFT -> state.copy(leftTrigger = value)
                        Side.RIGHT -> state.copy(rightTrigger = value)
                    }
                }
                is DpadControl -> state = state.copy(dpad = dpadValue(pointer, control))
                is ButtonControl -> Unit // Buttons are never captured.
            }
        }

        val newButton = state.buttons and lastButtons.inv() != 0
        val newDpad = !state.dpad.isNeutral && state.dpad != lastDpad
        if (newButton || newDpad) onPress()
        lastButtons = state.buttons
        lastDpad = state.dpad

        mutableStickOrigins.value = origins
        input.set(state)
    }

    private fun stickValue(pointer: Pointer, origin: TouchPoint, radius: Float): Stick {
        val dx = (pointer.x - origin.x) / radius
        val dy = (pointer.y - origin.y) / radius
        val magnitude = hypot(dx, dy)
        return if (magnitude > 1f) Stick(dx / magnitude, dy / magnitude) else Stick(dx, dy)
    }

    /** Pressing harder means further down the control. Any touch registers at least [TRIGGER_MIN]. */
    private fun triggerValue(pointer: Pointer, control: TriggerControl): Float {
        val top = (control.region.cy - control.region.halfH) * height
        val fraction = (pointer.y - top) / (control.region.halfH * 2f * height)
        return fraction.coerceIn(TRIGGER_MIN, 1f)
    }

    /** Eight 45-degree sectors, with a small neutral spot in the center. */
    private fun dpadValue(pointer: Pointer, control: DpadControl): Dpad {
        val center = centerOf(control)
        val dx = pointer.x - center.x
        val dy = pointer.y - center.y
        if (hypot(dx, dy) < control.region.halfW * height * DPAD_NEUTRAL) return Dpad()
        val horizontal = abs(dx) > abs(dy) * TAN_22_5
        val vertical = abs(dy) > abs(dx) * TAN_22_5
        return Dpad(
            up = vertical && dy < 0,
            down = vertical && dy > 0,
            left = horizontal && dx < 0,
            right = horizontal && dx > 0,
        )
    }

    private companion object {
        const val TRIGGER_MIN = 0.15f
        const val DPAD_NEUTRAL = 0.2f
        const val TAN_22_5 = 0.41421357f
    }
}
