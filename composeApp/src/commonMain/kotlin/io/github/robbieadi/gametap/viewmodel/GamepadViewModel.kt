package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.robbieadi.gametap.domain.GamepadSettings
import io.github.robbieadi.gametap.input.GamepadTouchTracker
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.ScreenControl
import io.github.robbieadi.gametap.platform.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class GamepadViewModel(
    val input: InputStateHolder,
    private val sender: ReportSender,
    private val screenControl: ScreenControl,
    private val haptics: Haptics,
    settingsRepository: SettingsRepository,
    layoutRepository: LayoutRepository,
) : ViewModel() {
    private val settings: StateFlow<GamepadSettings> = settingsRepository.settings

    val tracker = GamepadTouchTracker(
        layout = layoutRepository.layouts.value.active,
        input = input,
        floatingSticks = { settings.value.floatingSticks },
        onPress = { if (settings.value.hapticsEnabled) haptics.tick() },
    )

    /** The profile in use. The tracker switches over before the screen draws the new one. */
    val layout: StateFlow<LayoutProfile> = layoutRepository.layouts
        .map { it.active }
        .onEach { tracker.setLayout(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, layoutRepository.layouts.value.active)

    fun onScreenShown() {
        sender.lowLatency = true
        screenControl.setGamepadMode(true)
    }

    /** Leaving the screen must never leave a button held on the host. */
    fun onScreenHidden() {
        tracker.releaseAll()
        sender.lowLatency = false
        screenControl.setGamepadMode(false)
    }
}
