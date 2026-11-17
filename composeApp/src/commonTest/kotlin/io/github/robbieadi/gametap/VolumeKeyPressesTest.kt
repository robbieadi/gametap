package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.input.VolumeKeyPresses
import io.github.robbieadi.gametap.layout.VolumeKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeKeyPressesTest {
    private val events = mutableListOf<Pair<VolumeKey, Boolean>>()

    private fun TestScope.presses() =
        VolumeKeyPresses(backgroundScope, { key, pressed -> events += key to pressed }, minPressMs = 40, timeSource = testScheduler.timeSource)

    @Test
    fun instantTapIsHeldForTheMinimum() = runTest {
        val keys = presses()
        keys.down(VolumeKey.UP)
        keys.up(VolumeKey.UP)
        assertEquals(listOf(VolumeKey.UP to true), events)
        advanceTimeBy(39)
        runCurrent()
        assertEquals(listOf(VolumeKey.UP to true), events)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(VolumeKey.UP to true, VolumeKey.UP to false), events)
    }

    @Test
    fun longPressReleasesAtOnce() = runTest {
        val keys = presses()
        keys.down(VolumeKey.DOWN)
        advanceTimeBy(100)
        keys.up(VolumeKey.DOWN)
        assertEquals(listOf(VolumeKey.DOWN to true, VolumeKey.DOWN to false), events)
    }

    @Test
    fun repeatsAreIgnored() = runTest {
        val keys = presses()
        keys.down(VolumeKey.UP)
        keys.down(VolumeKey.UP)
        assertEquals(listOf(VolumeKey.UP to true), events)
    }

    @Test
    fun pressingAgainDuringTheHoldKeepsTheKeyDown() = runTest {
        val keys = presses()
        keys.down(VolumeKey.UP)
        keys.up(VolumeKey.UP)
        advanceTimeBy(10)
        keys.down(VolumeKey.UP)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(VolumeKey.UP to true), events)
        keys.up(VolumeKey.UP)
        assertEquals(listOf(VolumeKey.UP to true, VolumeKey.UP to false), events)
    }

    @Test
    fun clearReleasesAtOnceAndDropsPendingReleases() = runTest {
        val keys = presses()
        keys.down(VolumeKey.UP)
        keys.up(VolumeKey.UP)
        keys.down(VolumeKey.DOWN)
        keys.clear()
        assertEquals(setOf(VolumeKey.UP to false, VolumeKey.DOWN to false), events.drop(2).toSet())
        advanceTimeBy(100)
        runCurrent()
        assertEquals(4, events.size)
    }
}
