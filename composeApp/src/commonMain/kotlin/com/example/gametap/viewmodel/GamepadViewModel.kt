package com.example.gametap.viewmodel

import androidx.lifecycle.ViewModel
import com.example.gametap.domain.GamepadLayout
import com.example.gametap.domain.GamepadSettings
import com.example.gametap.input.GamepadTouchTracker
import com.example.gametap.input.InputStateHolder
import com.example.gametap.input.ReportSender
import com.example.gametap.platform.Haptics
import com.example.gametap.platform.ScreenControl
import com.example.gametap.platform.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class GamepadViewModel(
    val input: InputStateHolder,
    private val sender: ReportSender,
    private val screenControl: ScreenControl,
    private val haptics: Haptics,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val settings: StateFlow<GamepadSettings> = settingsRepository.settings

    val layout: GamepadLayout = GamepadLayout.Default

    val tracker = GamepadTouchTracker(
        layout = layout,
        input = input,
        floatingSticks = { settings.value.floatingSticks },
        onPress = { if (settings.value.hapticsEnabled) haptics.tick() },
    )

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
