package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.input.GamepadTouchTracker
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.TouchPoint
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.DpadDirection
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.DpadControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import io.github.robbieadi.gametap.layout.GamepadControl.ThrottleControl
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A 1000x500 screen with every control anchored left, so a position of x (in heights) sits at
 * x * 500 pixels. The comments give each control's pixel center and size.
 */
class GamepadTouchTrackerTest {
    private fun at(xPx: Float, yPx: Float) = Position(Anchor.LEFT, xPx / 500f, yPx / 500f)

    private fun button(id: String, xPx: Float, yPx: Float, action: ButtonAction, rPx: Float = 50f) =
        ButtonControl(id, at(xPx, yPx), Shape.Circle(rPx / 500f), action, id)

    private val a = button("a", 800f, 250f, ButtonAction.Pad(Button.A))                     // r50
    private val b = button("b", 900f, 250f, ButtonAction.Pad(Button.B))                     // r50
    private val stick = StickControl("ls", at(200f, 250f), Side.LEFT, 0.2f, 0.3f)          // travel 100, grab r150
    private val throttle = ThrottleControl("rt", at(500f, 100f), Side.RIGHT, 0.1f, 0.1f, "RT") // x 450..550, y 50..150
    private val dpad = DpadControl("dpad", at(500f, 400f), 0.2f)                            // r100
    private val menu = MenuControl("menu", at(40f, 40f), Shape.Circle(0.06f))               // r30

    private fun layoutOf(vararg controls: GamepadControl) = LayoutProfile("test", "Test", controls.toList())

    private val input = InputStateHolder()
    private var floating = false
    private var presses = 0
    private val tracker = tracker(layoutOf(a, b, stick, throttle, dpad, menu))
    private val state get() = input.state.value
    private val view get() = tracker.view.value

    private fun tracker(layout: LayoutProfile) =
        GamepadTouchTracker(layout, input, { floating }, { presses++ }).apply { resize(1000f, 500f) }

    @Test
    fun buttonPressAndRelease() {
        tracker.down(1, 800f, 250f)
        assertTrue(state.isPressed(Button.A))
        assertEquals(setOf("a"), view.pressed)
        assertEquals(1, presses)
        tracker.up(1)
        assertFalse(state.isPressed(Button.A))
        assertEquals(emptySet(), view.pressed)
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

    /** The round touch area matches the drawn circle: its bounding square's corner is not part of it. */
    @Test
    fun roundButtonIgnoresTheCornersOfItsSquare() {
        tracker.down(1, 840f, 210f)
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
        assertEquals(TouchPoint(200f, 250f), view.sticks.getValue("ls").origin)
        tracker.up(1)
        assertEquals(0f, state.leftStick.x)
        assertTrue(view.sticks.isEmpty())
    }

    @Test
    fun floatingStickAnchorsAtTouchDown() {
        floating = true
        tracker.down(1, 250f, 300f)
        assertEquals(0f, state.leftStick.x)
        assertEquals(TouchPoint(250f, 300f), view.sticks.getValue("ls").origin)
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
    fun throttleIsAnalogWithAMinimumPress() {
        tracker.down(1, 500f, 50f)
        assertEquals(0.15f, state.rightTrigger, 0.0001f)
        tracker.move(1, 500f, 100f)
        assertEquals(0.5f, state.rightTrigger, 0.0001f)
        assertEquals(0.5f, view.throttles.getValue("rt"), 0.0001f)
    }

    @Test
    fun dpadSupportsDiagonals() {
        tracker.down(1, 570f, 330f)
        assertEquals(Dpad(up = true, right = true), state.dpad)
        assertEquals(Dpad(up = true, right = true), view.dpads.getValue("dpad"))
        tracker.move(1, 500f, 330f)
        assertEquals(Dpad(up = true), state.dpad)
        tracker.move(1, 500f, 400f)
        assertEquals(Dpad(), state.dpad)
    }

    @Test
    fun hapticFiresOncePerNewDpadDirection() {
        tracker.down(1, 500f, 330f)
        tracker.move(1, 502f, 330f)
        assertEquals(1, presses)
        tracker.move(1, 570f, 330f) // Adds right.
        assertEquals(2, presses)
        tracker.move(1, 500f, 330f) // Drops right: nothing new pressed.
        assertEquals(2, presses)
    }

    @Test
    fun releaseAllClearsEverything() {
        tracker.down(1, 300f, 250f)
        tracker.down(2, 800f, 250f)
        tracker.releaseAll()
        assertEquals(GamepadState(), state)
        assertTrue(view.sticks.isEmpty())
        assertTrue(view.pressed.isEmpty())
    }

    @Test
    fun duplicateButtonsLightSeparatelyAndHoldTogether() {
        val tracker = tracker(layoutOf(a, a.copy(id = "a2", position = at(800f, 400f))))
        tracker.down(1, 800f, 250f)
        tracker.down(2, 800f, 400f)
        assertEquals(1, presses, "the second copy adds no new input")
        tracker.up(1)
        assertTrue(state.isPressed(Button.A))
        assertEquals(setOf("a2"), tracker.view.value.pressed)
    }

    @Test
    fun dpadButtonsCombineWithTheCross() {
        val up = button("up", 800f, 100f, ButtonAction.Dpad(DpadDirection.UP))
        val tracker = tracker(layoutOf(up, dpad))
        tracker.down(1, 800f, 100f)
        tracker.down(2, 500f, 470f) // Cross, pointing down.
        assertEquals(Dpad(up = true, down = true), state.dpad)
        assertEquals(Dpad(down = true), tracker.view.value.dpads.getValue("dpad"), "the cross shows only its own input")
        assertEquals(2, presses)
    }

    @Test
    fun triggerButtonPullsFullyAndOutranksAThrottle() {
        val rtButton = button("rt_button", 800f, 250f, ButtonAction.Trigger(Side.RIGHT))
        val tracker = tracker(layoutOf(throttle, rtButton))
        tracker.down(1, 500f, 50f)
        assertEquals(0.15f, state.rightTrigger, 0.0001f)
        assertEquals(0, presses, "a throttle is analog and does not buzz")
        tracker.down(2, 800f, 250f)
        assertEquals(1f, state.rightTrigger)
        assertEquals(1, presses)
        tracker.up(2)
        assertEquals(0.15f, state.rightTrigger, 0.0001f)
    }

    /** Only reachable in a layout that failed validation, but it must still be predictable. */
    @Test
    fun controlDrawnOnTopOwnsAnOverlappingTouch() {
        val overStick = button("over", 200f, 250f, ButtonAction.Pad(Button.X))
        tracker(layoutOf(stick, overStick)).down(1, 200f, 250f)
        assertTrue(state.isPressed(Button.X))
        assertEquals(0f, state.leftStick.x)

        val underStick = tracker(layoutOf(overStick, stick))
        underStick.down(2, 250f, 250f)
        assertFalse(state.isPressed(Button.X))
        assertEquals(0.5f, state.leftStick.x, 0.0001f)
    }

    @Test
    fun menuIsHeldButSendsNothing() {
        tracker.down(1, 40f, 40f)
        assertTrue(view.menuHeld)
        assertEquals(GamepadState(), state)
        assertEquals(0, presses)
        tracker.move(1, 120f, 40f)
        assertFalse(view.menuHeld, "sliding off cancels the hold")
    }

    @Test
    fun switchingLayoutsReleasesEverything() {
        tracker.down(1, 800f, 250f)
        tracker.setLayout(layoutOf(b))
        assertEquals(GamepadState(), state)
        tracker.move(1, 900f, 250f)
        assertEquals(0, state.buttons, "a finger from the old layout is forgotten")
    }
}
