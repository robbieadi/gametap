package io.github.robbieadi.gametap.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.robbieadi.gametap.domain.GamepadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val SETTINGS_FILE_NAME = "gametap.preferences_pb"

class SettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    val settings: StateFlow<GamepadSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, GamepadSettings())

    /**
     * Applies [transform] to the stored values, not to [settings], which can lag behind a write
     * that just finished. DataStore runs edits one at a time, so quick successive changes all stick.
     */
    suspend fun update(transform: (GamepadSettings) -> GamepadSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[DEADZONE] = next.deadzone
            prefs[CURVE] = next.curveExponent
            prefs[HAPTICS] = next.hapticsEnabled
            prefs[FLOATING] = next.floatingSticks
        }
    }

    private fun Preferences.toSettings(): GamepadSettings {
        val defaults = GamepadSettings()
        return GamepadSettings(
            deadzone = this[DEADZONE] ?: defaults.deadzone,
            curveExponent = this[CURVE] ?: defaults.curveExponent,
            hapticsEnabled = this[HAPTICS] ?: defaults.hapticsEnabled,
            floatingSticks = this[FLOATING] ?: defaults.floatingSticks,
        )
    }

    private companion object {
        val DEADZONE = floatPreferencesKey("deadzone")
        val CURVE = floatPreferencesKey("curve_exponent")
        val HAPTICS = booleanPreferencesKey("haptics")
        val FLOATING = booleanPreferencesKey("floating_sticks")
    }
}
