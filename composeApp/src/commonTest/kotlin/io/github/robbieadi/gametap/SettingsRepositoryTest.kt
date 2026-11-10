@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.robbieadi.gametap

import androidx.datastore.preferences.core.emptyPreferences
import io.github.robbieadi.gametap.domain.GamepadSettings
import io.github.robbieadi.gametap.platform.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {
    @Test
    fun startsWithDefaults() = runTest {
        val repository = SettingsRepository(InMemoryDataStore(emptyPreferences()), backgroundScope)
        runCurrent()
        assertEquals(GamepadSettings(), repository.settings.value)
    }

    @Test
    fun quickSuccessiveUpdatesAreAllKept() = runTest {
        val repository = SettingsRepository(InMemoryDataStore(emptyPreferences()), backgroundScope)
        runCurrent()

        // No runCurrent() between them, so the settings flow has not caught up with the first write.
        repository.update { it.copy(hapticsEnabled = false) }
        repository.update { it.copy(floatingSticks = true) }
        runCurrent()

        assertEquals(GamepadSettings(hapticsEnabled = false, floatingSticks = true), repository.settings.value)
    }

    @Test
    fun storedValuesSurviveANewRepository() = runTest {
        val store = InMemoryDataStore(emptyPreferences())
        SettingsRepository(store, backgroundScope).update { it.copy(deadzone = 0.2f, curveExponent = 2f) }

        val reopened = SettingsRepository(store, backgroundScope)
        runCurrent()

        assertEquals(GamepadSettings(deadzone = 0.2f, curveExponent = 2f), reopened.settings.value)
    }
}
