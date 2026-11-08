@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.gametap

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.gametap.domain.GamepadSettings
import com.example.gametap.platform.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-memory DataStore. Like the real one, it applies updates one at a time. */
private class InMemoryStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val current = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = current

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(current.value).also { current.value = it } }
}

class SettingsRepositoryTest {
    @Test
    fun startsWithDefaults() = runTest {
        val repository = SettingsRepository(InMemoryStore(), backgroundScope)
        runCurrent()
        assertEquals(GamepadSettings(), repository.settings.value)
    }

    @Test
    fun quickSuccessiveUpdatesAreAllKept() = runTest {
        val repository = SettingsRepository(InMemoryStore(), backgroundScope)
        runCurrent()

        // No runCurrent() between them, so the settings flow has not caught up with the first write.
        repository.update { it.copy(hapticsEnabled = false) }
        repository.update { it.copy(floatingSticks = true) }
        runCurrent()

        assertEquals(GamepadSettings(hapticsEnabled = false, floatingSticks = true), repository.settings.value)
    }

    @Test
    fun storedValuesSurviveANewRepository() = runTest {
        val store = InMemoryStore()
        SettingsRepository(store, backgroundScope).update { it.copy(deadzone = 0.2f, curveExponent = 2f) }

        val reopened = SettingsRepository(store, backgroundScope)
        runCurrent()

        assertEquals(GamepadSettings(deadzone = 0.2f, curveExponent = 2f), reopened.settings.value)
    }
}
