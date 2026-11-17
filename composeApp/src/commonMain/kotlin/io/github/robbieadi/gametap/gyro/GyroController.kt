package io.github.robbieadi.gametap.gyro

import io.github.robbieadi.gametap.layout.GyroButton
import io.github.robbieadi.gametap.layout.GyroSetup
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.platform.MotionSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * Turns orientation samples into [GyroOutput] for the active profile.
 *
 * The profile's [LayoutProfile.gyroMode] decides when gyro is on: always with
 * [GyroButton.RECENTER], while a [GyroButton.TOGGLE], which starts off, has turned it on, or while
 * a [GyroButton.HOLD] is held. A profile without a gyro button or key has no gyro. Turning gyro on
 * makes that angle neutral, so the player turns it on once their grip is settled.
 *
 * Samples arrive on the sensor's thread and everything else on the UI thread. The UI thread only
 * writes the volatile flags and [toggledOn]; the neutral angle belongs to the sensor thread alone.
 */
class GyroController(private val sink: (GyroOutput) -> Unit) {
    @Volatile private var setup = GyroSetup()
    @Volatile private var mode: GyroButton? = null
    @Volatile private var held = false
    private val mutableToggledOn = MutableStateFlow(false)

    /** A [GyroButton.TOGGLE] has turned gyro on. */
    val toggledOn: StateFlow<Boolean> = mutableToggledOn.asStateFlow()
    @Volatile private var recenterPending = true
    /** Set by [stop], so a sample still in flight from the sensor cannot undo the cleared output. */
    @Volatile private var stopped = true

    // Sensor thread only.
    private var neutral: MotionSample? = null
    private var neutralTurns = 0

    // UI thread only.
    private var lastButtons: Set<GyroButton> = emptySet()

    /** A new profile starts toggled off and recentered. */
    fun setLayout(profile: LayoutProfile) {
        setup = profile.gyro
        mode = profile.gyroMode
        mutableToggledOn.value = false
        recenter()
    }

    /** The gyro buttons under a finger. Acts on presses, so call it with every change. */
    fun setButtons(buttons: Set<GyroButton>) {
        val pressed = buttons - lastButtons
        lastButtons = buttons
        val wasActive = mutableToggledOn.value || held
        if (GyroButton.TOGGLE in pressed) mutableToggledOn.value = !mutableToggledOn.value
        held = GyroButton.HOLD in buttons
        // Turning on sets the neutral angle.
        val turnedOn = !wasActive && (mutableToggledOn.value || held)
        if (turnedOn || GyroButton.RECENTER in pressed) recenter()
    }

    /** The next sample becomes neutral. */
    fun recenter() {
        recenterPending = true
    }

    /** Call before starting the sensor. The first sample becomes neutral. */
    fun start() {
        recenter()
        stopped = false
    }

    fun onSample(sample: MotionSample, quarterTurns: Int) {
        if (stopped) return
        val start = neutral
        val tilt = when {
            start is MotionSample.Orientation && sample is MotionSample.Orientation ->
                Tilt.between(start.rotation, sample.rotation, quarterTurns)
            start is MotionSample.Gravity && sample is MotionSample.Gravity ->
                Tilt.between(start.up, sample.up, quarterTurns)
            else -> null
        }
        // Flipping to the other landscape side is a half turn; measuring across it means nothing.
        if (recenterPending || tilt == null || quarterTurns != neutralTurns) {
            recenterPending = false
            neutral = sample
            neutralTurns = quarterTurns
            sink(GyroOutput.Zero)
            return
        }
        val active = when (mode) {
            GyroButton.RECENTER -> true
            GyroButton.TOGGLE -> mutableToggledOn.value
            GyroButton.HOLD -> held
            null -> false
        }
        sink(if (active) setup.output(tilt) else GyroOutput.Zero)
    }

    /** Clears the output and ignores samples until [start]. */
    fun stop() {
        stopped = true
        sink(GyroOutput.Zero)
    }
}
