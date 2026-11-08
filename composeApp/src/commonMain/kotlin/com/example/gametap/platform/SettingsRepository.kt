package com.example.gametap.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.example.gametap.domain.GamepadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val SETTINGS_FILE_NAME = "gametap.preferences_pb"

class SettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    val settings: StateFlow<GamepadSettings> = store.data
        .map { prefs ->
            val defaults = GamepadSettings()
            GamepadSettings(
                deadzone = prefs[DEADZONE] ?: defaults.deadzone,
                curveExponent = prefs[CURVE] ?: defaults.curveExponent,
                hapticsEnabled = prefs[HAPTICS] ?: defaults.hapticsEnabled,
                floatingSticks = prefs[FLOATING] ?: defaults.floatingSticks,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, GamepadSettings())

    suspend fun update(transform: (GamepadSettings) -> GamepadSettings) {
        val next = transform(settings.value)
        store.edit { prefs ->
            prefs[DEADZONE] = next.deadzone
            prefs[CURVE] = next.curveExponent
            prefs[HAPTICS] = next.hapticsEnabled
            prefs[FLOATING] = next.floatingSticks
        }
    }

    private companion object {
        val DEADZONE = floatPreferencesKey("deadzone")
        val CURVE = floatPreferencesKey("curve_exponent")
        val HAPTICS = booleanPreferencesKey("haptics")
        val FLOATING = booleanPreferencesKey("floating_sticks")
    }
}
