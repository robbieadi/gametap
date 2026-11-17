package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.gyro.GyroController
import io.github.robbieadi.gametap.gyro.GyroOutput
import io.github.robbieadi.gametap.gyro.Quaternion
import io.github.robbieadi.gametap.gyro.Vec3
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GyroAxis
import io.github.robbieadi.gametap.layout.GyroBinding
import io.github.robbieadi.gametap.layout.GyroButton
import io.github.robbieadi.gametap.layout.GyroSetup
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.VolumeKeys
import io.github.robbieadi.gametap.platform.MotionSample
import kotlin.test.Test
import kotlin.test.assertEquals

/** RT pulls fully at 10 degrees of clockwise roll, so readings are 0 or 1 at the angles used here. */
class GyroControllerTest {
    private var output = GyroOutput.Zero
    private val gyro = GyroController { output = it }
    private val setup = GyroSetup(listOf(GyroBinding.TriggerBinding(Side.RIGHT, GyroAxis.HORIZONTAL, range = 10f)), deadzone = 0f)

    private fun button(action: GyroButton): GamepadControl =
        ButtonControl(action.name, Position(Anchor.LEFT, 0f, 0f), Shape.Circle(0.1f), ButtonAction.Gyro(action), "")

    private fun profile(vararg controls: GamepadControl) = LayoutProfile("p", "P", controls.toList(), setup)

    /** Clockwise roll, in degrees, from the natural orientation. */
    private fun sample(roll: Float, quarterTurns: Int = 0) =
        gyro.onSample(MotionSample.Orientation(Quaternion.about(Vec3(0f, 0f, 1f), -roll)), quarterTurns)

    private val rt get() = output.rightTrigger

    private fun tap(button: GyroButton) {
        gyro.setButtons(setOf(button))
        gyro.setButtons(emptySet())
    }

    private fun started(vararg controls: GamepadControl) = apply {
        gyro.setLayout(profile(*controls))
        gyro.start()
    }

    @Test
    fun firstSampleIsNeutral() {
        started()
        sample(30f)
        assertEquals(0f, rt, 0.001f)
        sample(40f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun recenterMakesTheNextSampleNeutral() {
        started()
        sample(0f)
        sample(20f)
        assertEquals(1f, rt, 0.001f)
        gyro.recenter()
        sample(20f)
        assertEquals(0f, rt, 0.001f)
        sample(25f)
        assertEquals(0.5f, rt, 0.001f)
    }

    @Test
    fun recenterButtonRecentersOnPress() {
        started(button(GyroButton.RECENTER))
        sample(0f)
        gyro.setButtons(setOf(GyroButton.RECENTER))
        sample(20f)
        sample(20f)
        assertEquals(0f, rt, 0.001f)
    }

    @Test
    fun holdButtonLimitsGyroToWhileHeldAndRecentersOnPress() {
        started(button(GyroButton.HOLD))
        sample(0f)
        sample(20f)
        assertEquals(0f, rt, 0.001f)

        gyro.setButtons(setOf(GyroButton.HOLD))
        sample(20f)
        sample(30f)
        assertEquals(1f, rt, 0.001f)

        gyro.setButtons(emptySet())
        sample(30f)
        assertEquals(0f, rt, 0.001f)
    }

    @Test
    fun toggleStartsOffAndTurningOnRecenters() {
        started(button(GyroButton.TOGGLE))
        sample(0f)
        sample(20f)
        assertEquals(0f, rt, 0.001f)

        tap(GyroButton.TOGGLE)
        sample(20f)
        assertEquals(0f, rt, 0.001f)
        sample(30f)
        assertEquals(1f, rt, 0.001f)

        tap(GyroButton.TOGGLE)
        sample(30f)
        assertEquals(0f, rt, 0.001f)
    }

    @Test
    fun holdingToggleDoesNotFlipItAgain() {
        started(button(GyroButton.TOGGLE))
        sample(0f)
        gyro.setButtons(setOf(GyroButton.TOGGLE))
        gyro.setButtons(setOf(GyroButton.TOGGLE))
        sample(0f)
        sample(20f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun newLayoutTurnsTheToggleOffAndRecenters() {
        started(button(GyroButton.TOGGLE))
        tap(GyroButton.TOGGLE)
        sample(0f)
        gyro.setLayout(profile(button(GyroButton.TOGGLE)))
        assertEquals(false, gyro.toggledOn.value)
        tap(GyroButton.TOGGLE)
        sample(20f)
        sample(30f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun withoutAToggleOrHoldGyroIsAlwaysOn() {
        started(button(GyroButton.RECENTER))
        sample(0f)
        sample(20f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun otherButtonsDoNotNeedHold() {
        started(ButtonControl("a", Position(Anchor.LEFT, 0f, 0f), Shape.Circle(0.1f), ButtonAction.Pad(Button.A), "A"))
        sample(0f)
        sample(20f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun displayRotationChangeRecenters() {
        started()
        sample(0f, quarterTurns = 1)
        sample(180f, quarterTurns = 3)
        assertEquals(0f, rt, 0.001f)
        sample(190f, quarterTurns = 3)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun stopClearsOutputAndIgnoresLateSamples() {
        started()
        sample(0f)
        sample(20f)
        assertEquals(1f, rt, 0.001f)
        gyro.stop()
        assertEquals(0f, rt, 0.001f)
        sample(20f)
        assertEquals(0f, rt, 0.001f)
    }

    /** Up in device coordinates while held upright in portrait and rolled clockwise. */
    private fun gravity(roll: Float) =
        gyro.onSample(MotionSample.Gravity(Quaternion.about(Vec3(0f, 0f, 1f), roll).rotate(Vec3(0f, 9.81f, 0f))), 0)

    @Test
    fun accelerometerSamplesDriveTheSameOutput() {
        started()
        gravity(0f)
        gravity(5f)
        assertEquals(0.5f, rt, 0.001f)
        gravity(20f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun aDifferentKindOfSampleBecomesNeutral() {
        started()
        sample(0f)
        gravity(20f)
        assertEquals(0f, rt, 0.001f)
        gravity(30f)
        assertEquals(1f, rt, 0.001f)
    }

    @Test
    fun toggledOnFollowsTheToggle() {
        started(button(GyroButton.TOGGLE))
        assertEquals(false, gyro.toggledOn.value)
        tap(GyroButton.TOGGLE)
        assertEquals(true, gyro.toggledOn.value)
        tap(GyroButton.TOGGLE)
        assertEquals(false, gyro.toggledOn.value)
    }

    @Test
    fun holdOnAVolumeKeyAlsoStartsGyroOff() {
        gyro.setLayout(profile().copy(volumeKeys = VolumeKeys(down = ButtonAction.Gyro(GyroButton.HOLD))))
        gyro.start()
        sample(0f)
        sample(20f)
        assertEquals(0f, rt, 0.001f)
    }
}
