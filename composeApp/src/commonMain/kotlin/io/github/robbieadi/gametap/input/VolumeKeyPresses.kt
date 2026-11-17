package io.github.robbieadi.gametap.input

import io.github.robbieadi.gametap.layout.VolumeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Holds every volume key press for at least [minPressMs] before passing the release on.
 *
 * Android delays volume key events to watch for system shortcuts such as volume down + power, so a
 * quick tap often arrives as a down and an up in the same instant. Passed straight through, the
 * press would come and go between two reports and never reach the host. [minPressMs] covers a few
 * reports and two frames of a 60 fps game.
 *
 * Call everything on one thread, the one [scope] runs on.
 */
class VolumeKeyPresses(
    private val scope: CoroutineScope,
    private val onChange: (VolumeKey, pressed: Boolean) -> Unit,
    private val minPressMs: Long = MIN_PRESS_MS,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val marks = HashMap<VolumeKey, TimeMark>()
    private val releases = HashMap<VolumeKey, Job>()

    fun down(key: VolumeKey) {
        // A new press during a held-over release just keeps the key down.
        releases.remove(key)?.cancel()
        if (key in marks) return // A key repeat, or the release is still pending.
        marks[key] = timeSource.markNow()
        onChange(key, true)
    }

    fun up(key: VolumeKey) {
        val mark = marks[key] ?: return
        val remaining = minPressMs.milliseconds - mark.elapsedNow()
        if (remaining <= Duration.ZERO) {
            release(key)
            return
        }
        releases.remove(key)?.cancel()
        releases[key] = scope.launch {
            delay(remaining)
            releases.remove(key)
            release(key)
        }
    }

    /** Lets go of every key at once, for when the controls must not stay held. */
    fun clear() {
        releases.values.forEach { it.cancel() }
        releases.clear()
        marks.keys.toList().forEach(::release)
    }

    private fun release(key: VolumeKey) {
        marks.remove(key)
        onChange(key, false)
    }

    companion object {
        const val MIN_PRESS_MS: Long = 40
    }
}
