package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.hogp.LatestReportQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class LatestReportQueueTest {
    private class Radio {
        var accepting = true
        val sent = mutableListOf<Int>()
        fun trySend(report: ByteArray): Boolean {
            if (!accepting) return false
            sent += report[0].toInt()
            return true
        }
    }

    private fun report(n: Int) = byteArrayOf(n.toByte())

    // Android: every send is confirmed by onNotificationSent before the next.

    @Test
    fun confirmedModeHoldsReportsUntilTheSendIsConfirmed() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = true, trySend = radio::trySend)
        queue.offer(report(1))
        queue.offer(report(2))
        queue.offer(report(3))
        assertEquals(listOf(1), radio.sent)

        queue.onReady()
        assertEquals(listOf(1, 3), radio.sent, "only the newest waiting report goes out")
    }

    @Test
    fun confirmedModeSendsAtOnceWhenNothingIsInFlight() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = true, trySend = radio::trySend)
        queue.offer(report(1))
        queue.onReady()
        queue.offer(report(2))
        assertEquals(listOf(1, 2), radio.sent)
    }

    @Test
    fun confirmationWithNothingWaitingSendsNothing() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = true, trySend = radio::trySend)
        queue.offer(report(1))
        queue.onReady()
        queue.onReady()
        assertEquals(listOf(1), radio.sent)
    }

    @Test
    fun refusedSendIsRetriedOnTheNextOffer() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = true, trySend = radio::trySend)
        radio.accepting = false
        queue.offer(report(1))
        radio.accepting = true
        queue.offer(report(2))
        assertEquals(listOf(2), radio.sent)
    }

    @Test
    fun resetForgetsAConfirmationThatWillNeverCome() {
        // A disconnect can drop the onNotificationSent of the last send.
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = true, trySend = radio::trySend)
        queue.offer(report(1))
        queue.reset()
        queue.offer(report(2))
        assertEquals(listOf(1, 2), radio.sent)
    }

    @Test
    fun resetDropsTheWaitingReport() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = true, trySend = radio::trySend)
        queue.offer(report(1))
        queue.offer(report(2))
        queue.reset()
        queue.onReady()
        assertEquals(listOf(1), radio.sent)
    }

    // iOS: accepted sends need no confirmation; a full queue is retried when the radio is ready.

    @Test
    fun unconfirmedModeSendsEveryAcceptedReport() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = false, trySend = radio::trySend)
        queue.offer(report(1))
        queue.offer(report(2))
        assertEquals(listOf(1, 2), radio.sent)
    }

    @Test
    fun unconfirmedModeRetriesTheNewestReportWhenReady() {
        val radio = Radio()
        val queue = LatestReportQueue(waitsForCompletion = false, trySend = radio::trySend)
        radio.accepting = false
        queue.offer(report(1))
        queue.offer(report(2))
        radio.accepting = true
        queue.onReady()
        assertEquals(listOf(2), radio.sent)
    }
}
