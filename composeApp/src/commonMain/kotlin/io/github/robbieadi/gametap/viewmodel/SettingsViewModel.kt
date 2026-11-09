package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.robbieadi.gametap.domain.GamepadSettings
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    haptics: Haptics,
) : ViewModel() {
    val settings: StateFlow<GamepadSettings> = repository.settings

    /** The haptics switch is hidden on devices that cannot vibrate. */
    val hapticsAvailable: Boolean = haptics.isAvailable

    fun update(transform: (GamepadSettings) -> GamepadSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
