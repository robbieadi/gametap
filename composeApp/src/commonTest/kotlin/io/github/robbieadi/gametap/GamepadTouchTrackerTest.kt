package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadControl
import io.github.robbieadi.gametap.domain.GamepadLayout
import io.github.robbieadi.gametap.domain.Region
import io.github.robbieadi.gametap.domain.Side
import io.github.robbieadi.gametap.input.GamepadTouchTracker
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.TouchPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A 1000x500 screen: control centers are at easy pixel positions. */
class GamepadTouchTrackerTest {
    private val layout = GamepadLayout(
        controls = listOf(
            GamepadControl.ButtonControl(Button.A, "A", Region(0.8f, 0.5f, 0.1f)),  // 800,250 r50
            GamepadControl.ButtonControl(Button.B, "B", Region(0.9f, 0.5f, 0.1f)),  // 900,250 r50
            GamepadControl.StickControl(Side.LEFT, 0.2f, Region(0.2f, 0.5f, 0.3f)), // 200,250 travel 100
            GamepadControl.TriggerControl(Side.RIGHT, "RT", Region(0.5f, 0.2f, 0.1f)), // 500,100 y 50..150
            GamepadControl.DpadControl(Region(0.5f, 0.8f, 0.2f)), // 500,400 r100
        ),
        settingsCorner = Region(0f, 0f, 0.1f),
    )
    private val input = InputStateHolder()
    private var floating = false
    private var presses = 0
    private val tracker = GamepadTouchTracker(layout, input, { floating }, { presses++ }).apply {
        resize(1000f, 500f)
    }
    private val state get() = input.state.value

    @Test
    fun buttonPressAndRelease() {
        tracker.down(1, 800f, 250f)
        assertTrue(state.isPressed(Button.A))
        assertEquals(1, presses)
        tracker.up(1)
        assertFalse(state.isPressed(Button.A))
    }

    @Test
    fun buttonsSlideOnAndOff() {
        tracker.down(1, 800f, 250f)
        tracker.move(1, 900f, 250f)
        assertFalse(state.isPressed(Button.A))
        assertTrue(state.isPressed(Button.B))
        assertEquals(2, presses)
        tracker.move(1, 700f, 50f)
        assertEquals(0, state.buttons)
    }

    @Test
    fun touchInEmptySpaceCanSlideOntoAButton() {
        tracker.down(1, 700f, 50f)
        assertEquals(0, state.buttons)
        tracker.move(1, 800f, 250f)
        assertTrue(state.isPressed(Button.A))
    }

    @Test
    fun twoFingersOnOneButtonHoldUntilBothLift() {
        tracker.down(1, 800f, 250f)
        tracker.down(2, 810f, 250f)
        assertEquals(1, presses)
        tracker.up(1)
        assertTrue(state.isPressed(Button.A))
        tracker.up(2)
        assertFalse(state.isPressed(Button.A))
    }

    @Test
    fun stickKeepsItsPointerAcrossTheScreen() {
        tracker.down(1, 200f, 250f)
        tracker.move(1, 800f, 250f)
        assertEquals(1f, state.leftStick.x, 0.0001f)
        assertEquals(0f, state.leftStick.y, 0.0001f)
        assertFalse(state.isPressed(Button.A))
    }

    @Test
    fun stickValueScalesWithTravel() {
        tracker.down(1, 250f, 250f)
        assertEquals(0.5f, state.leftStick.x, 0.0001f)
        tracker.up(1)
        assertEquals(0f, state.leftStick.x)
    }

    @Test
    fun floatingStickAnchorsAtTouchDown() {
        floating = true
        tracker.down(1, 250f, 300f)
        assertEquals(0f, state.leftStick.x)
        assertEquals(TouchPoint(250f, 300f), tracker.stickOrigins.value[Side.LEFT])
        tracker.move(1, 300f, 300f)
        assertEquals(0.5f, state.leftStick.x, 0.0001f)
    }

    @Test
    fun secondFingerDoesNotStealAHeldStick() {
        tracker.down(1, 250f, 250f)
        tracker.down(2, 150f, 250f)
        assertEquals(0.5f, state.leftStick.x, 0.0001f)
    }

    @Test
    fun simultaneousControlsAllRegister() {
        tracker.down(1, 300f, 250f)
        tracker.down(2, 800f, 250f)
        tracker.down(3, 500f, 150f)
        assertEquals(1f, state.leftStick.x, 0.0001f)
        assertTrue(state.isPressed(Button.A))
        assertEquals(1f, state.rightTrigger, 0.0001f)
    }

    @Test
    fun triggerIsAnalogWithAMinimumPress() {
        tracker.down(1, 500f, 50f)
        assertEquals(0.15f, state.rightTrigger, 0.0001f)
        tracker.move(1, 500f, 100f)
        assertEquals(0.5f, state.rightTrigger, 0.0001f)
    }

    @Test
    fun dpadSupportsDiagonals() {
        tracker.down(1, 570f, 330f)
        assertEquals(Dpad(up = true, right = true), state.dpad)
        tracker.move(1, 500f, 330f)
        assertEquals(Dpad(up = true), state.dpad)
        tracker.move(1, 500f, 400f)
        assertEquals(Dpad(), state.dpad)
    }

    @Test
    fun hapticFiresOncePerDpadDirection() {
        tracker.down(1, 500f, 330f)
        tracker.move(1, 502f, 330f)
        assertEquals(1, presses)
        tracker.move(1, 570f, 330f)
        assertEquals(2, presses)
    }

    @Test
    fun releaseAllClearsEverything() {
        tracker.down(1, 300f, 250f)
        tracker.down(2, 800f, 250f)
        tracker.releaseAll()
        assertEquals(io.github.robbieadi.gametap.domain.GamepadState(), state)
        assertTrue(tracker.stickOrigins.value.isEmpty())
    }
}
