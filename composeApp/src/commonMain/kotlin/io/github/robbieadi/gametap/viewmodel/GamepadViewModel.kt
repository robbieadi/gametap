package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.robbieadi.gametap.gyro.GyroController
import io.github.robbieadi.gametap.input.GamepadTouchTracker
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.input.VolumeKeyPresses
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.layout.Layouts
import io.github.robbieadi.gametap.layout.VolumeKey
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.MotionSensor
import io.github.robbieadi.gametap.platform.ScreenModeLock
import io.github.robbieadi.gametap.platform.VolumeKeyInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GamepadViewModel(
    val input: InputStateHolder,
    private val sender: ReportSender,
    private val screenMode: ScreenModeLock,
    private val haptics: Haptics,
    private val layoutRepository: LayoutRepository,
    private val motionSensor: MotionSensor,
    private val volumeKeyInput: VolumeKeyInput,
) : ViewModel() {
    private val gyro = GyroController(input::setMotion)
    private var shown = false
    private var sensorRunning = false
    private var menuOpen = false

    val tracker = GamepadTouchTracker(
        layout = layoutRepository.layouts.value.active,
        input = input,
        onPress = { if (layoutRepository.layouts.value.active.haptics) haptics.tick() },
        onGyroButtons = gyro::setButtons,
    )

    private val volumeKeys = VolumeKeyPresses(viewModelScope, tracker::key)

    /** The profile in use. The tracker switches over before the screen draws the new one. */
    val layout: StateFlow<LayoutProfile> = layoutRepository.layouts
        .map { it.active }
        .onEach {
            volumeKeys.clear()
            tracker.setLayout(it)
            gyro.setLayout(it)
            updateSensor(it)
            updateVolumeKeys(it)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, layoutRepository.layouts.value.active)

    /** A toggle button has turned gyro on and it can run, which shows the button lit. */
    val gyroOn: StateFlow<Boolean> = combine(gyro.toggledOn, layout) { on, profile ->
        on && profile.runsGyro
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Every profile, for the in-game menu. */
    val layouts: StateFlow<Layouts> = layoutRepository.layouts

    fun selectProfile(id: String) {
        viewModelScope.launch { layoutRepository.select(id) }
    }

    fun duplicateProfile(id: String) {
        viewModelScope.launch { layoutRepository.duplicate(id) }
    }

    fun renameProfile(id: String, name: String) {
        viewModelScope.launch { layoutRepository.rename(id, name.trim()) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { layoutRepository.delete(id) }
    }

    /** True if a profile other than [id] already uses [name]. */
    fun isNameTaken(id: String, name: String): Boolean =
        layouts.value.profiles.any { it.id != id && it.name == name.trim() }

    /** Opening the menu lets go of every control, so nothing stays held behind it. */
    fun onMenuOpened() {
        releaseAll()
    }

    /** While the menu is open, the volume keys change the volume again. */
    fun setMenuOpen(open: Boolean) {
        menuOpen = open
        updateVolumeKeys(layout.value)
    }

    fun onScreenShown() {
        sender.lowLatency = true
        screenMode.acquire()
        shown = true
        updateSensor(layout.value)
        updateVolumeKeys(layout.value)
    }

    /** Leaving the screen must never leave a button held on the host. */
    fun onScreenHidden() {
        releaseAll()
        sender.lowLatency = false
        screenMode.release()
        shown = false
        updateSensor(layout.value)
        updateVolumeKeys(layout.value)
    }

    override fun onCleared() {
        shown = false
        updateSensor(layout.value)
        updateVolumeKeys(layout.value)
    }

    /** The keys are taken only while the gamepad is on screen, unobstructed, and the profile binds one. */
    private fun updateVolumeKeys(profile: LayoutProfile) {
        val wanted = shown && !menuOpen && !profile.volumeKeys.isEmpty
        volumeKeyInput.setHandler(if (wanted) ::onVolumeKey else null)
    }

    /** An unbound key goes on changing the volume. */
    private fun onVolumeKey(key: VolumeKey, pressed: Boolean): Boolean {
        if (layout.value.volumeKeys[key] == null) return false
        if (pressed) volumeKeys.down(key) else volumeKeys.up(key)
        return true
    }

    private fun releaseAll() {
        volumeKeys.clear()
        tracker.releaseAll()
    }

    /** Gyro needs a sensor, a gyro button or key, and something for tilting to drive. */
    private val LayoutProfile.runsGyro: Boolean
        get() = motionSensor.isAvailable && gyroMode != null && gyro.bindings.isNotEmpty()

    /** The sensor runs only while the gamepad is on screen and the profile uses gyro. */
    private fun updateSensor(profile: LayoutProfile) {
        val wanted = shown && profile.runsGyro
        if (wanted == sensorRunning) return
        sensorRunning = wanted
        if (wanted) {
            gyro.start()
            motionSensor.start(gyro::onSample)
        } else {
            // Stop returns once no sample is in flight, so nothing lands after the output is cleared.
            motionSensor.stop()
            gyro.stop()
        }
    }
}
