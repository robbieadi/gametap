package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.robbieadi.gametap.domain.GamepadSettings
import io.github.robbieadi.gametap.input.GamepadTouchTracker
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.layout.Layouts
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.ScreenModeLock
import io.github.robbieadi.gametap.platform.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GamepadViewModel(
    val input: InputStateHolder,
    private val sender: ReportSender,
    private val screenMode: ScreenModeLock,
    private val haptics: Haptics,
    settingsRepository: SettingsRepository,
    private val layoutRepository: LayoutRepository,
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
        tracker.releaseAll()
    }

    fun onScreenShown() {
        sender.lowLatency = true
        screenMode.acquire()
    }

    /** Leaving the screen must never leave a button held on the host. */
    fun onScreenHidden() {
        tracker.releaseAll()
        sender.lowLatency = false
        screenMode.release()
    }
}
