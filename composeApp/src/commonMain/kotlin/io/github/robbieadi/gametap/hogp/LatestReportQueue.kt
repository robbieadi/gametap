package io.github.robbieadi.gametap.hogp

/**
 * Sends input reports as notifications without letting them pile up. While the radio is busy,
 * only the newest report waits, so the host always catches up to the current state.
 *
 * Not thread-safe: iOS calls it from the main queue, Android under its transport lock.
 *
 * @param waitsForCompletion true when every accepted send must be confirmed by [onReady] before
 *   the next one, as with Android's `onNotificationSent`. False when an accepted send needs no
 *   confirmation and a refused one is retried on [onReady], as with iOS's `updateValue`.
 * @param trySend sends one report and returns false if it was not accepted.
 */
class LatestReportQueue(
    private val waitsForCompletion: Boolean,
    private val trySend: (ByteArray) -> Boolean,
) {
    private var waiting: ByteArray? = null
    private var inFlight = false

    fun offer(report: ByteArray) {
        waiting = report
        pump()
    }

    /** The radio can take another notification. */
    fun onReady() {
        inFlight = false
        pump()
    }

    /** A new connection or subscription: nothing from before is still in flight. */
    fun reset() {
        inFlight = false
        waiting = null
    }

    private fun pump() {
        if (inFlight) return
        val report = waiting ?: return
        if (trySend(report)) {
            waiting = null
            inFlight = waitsForCompletion
        }
        // Otherwise the report keeps waiting for onReady() or the next offer().
    }
}
