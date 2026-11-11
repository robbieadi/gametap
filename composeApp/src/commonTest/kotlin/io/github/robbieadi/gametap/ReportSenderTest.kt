@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.robbieadi.gametap

import app.cash.turbine.test
import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.domain.GamepadSettings
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.domain.Stick
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.TransportState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Virtual test time plus the time spent inside [FakeTransport.sendReport]. A real send blocks the
 * sender's thread, which virtual time cannot express, so the blocked time is added here instead.
 */
private class FakeClock(private val scheduler: TestCoroutineScheduler) : TimeSource {
    var blockedMs = 0L

    fun nowMs(): Long = scheduler.currentTime + blockedMs

    override fun markNow(): TimeMark {
        val at = nowMs()
        return object : TimeMark {
            override fun elapsedNow(): Duration = (nowMs() - at).milliseconds
        }
    }
}

private class FakeTransport(initial: TransportState = TransportState.Connected("host")) : HidTransport {
    override val state = MutableStateFlow(initial)
    val sent = MutableSharedFlow<List<Int>>(extraBufferCapacity = 256)
    var accept = true
    var attempts = 0
    var clock: FakeClock? = null
    /** How long each send blocks, in clock time. */
    var callMs = 0L
    /** Clock time at the start of each send. */
    val startedAt = mutableListOf<Long>()

    override suspend fun start(descriptor: ByteArray) = Unit
    override suspend fun stop() = Unit

    override fun sendReport(reportId: Int, data: ByteArray): Boolean {
        attempts++
        clock?.let {
            startedAt += it.nowMs()
            it.blockedMs += callMs
        }
        if (accept) sent.tryEmit(data.map { it.toInt() and 0xFF })
        return accept
    }
}

class ReportSenderTest {
    private val input = InputStateHolder()
    private val settings = MutableStateFlow(GamepadSettings(deadzone = 0f))

    private fun TestScope.startSender(transport: FakeTransport, lowLatency: Boolean = false): ReportSender {
        val clock = FakeClock(testScheduler)
        transport.clock = clock
        val sender = ReportSender(input, transport, settings, timeSource = clock)
        sender.lowLatency = lowLatency
        backgroundScope.launch { sender.run() }
        runCurrent()
        return sender
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun sendsBaselineThenOnlyOnChangeWhenIdle() = runTest {
        val transport = FakeTransport()
        transport.sent.test {
            startSender(transport)
            assertEquals(0, awaitItem()[0])

            advance(100)
            expectNoEvents()

            input.set(GamepadState(buttons = Button.A.mask))
            runCurrent()
            assertEquals(1, awaitItem()[0])
        }
    }

    @Test
    fun changeIsSentImmediatelyAfterAPause() = runTest {
        val transport = FakeTransport()
        startSender(transport)
        advance(100)
        val before = transport.attempts

        input.set(GamepadState(buttons = Button.A.mask))
        runCurrent()

        assertEquals(before + 1, transport.attempts)
    }

    @Test
    fun changesAreLimitedToTheMinimumInterval() = runTest {
        val transport = FakeTransport()
        startSender(transport)
        repeat(80) { step ->
            input.set(GamepadState(leftStick = Stick(x = step / 100f)))
            advance(1)
        }
        assertTrue(transport.attempts in 9..11, "attempts=${transport.attempts}")
    }

    @Test
    fun idleKeepAliveRepeatsAnUnchangedReport() = runTest {
        val transport = FakeTransport()
        startSender(transport)
        assertEquals(1, transport.attempts)
        advance(490)
        assertEquals(1, transport.attempts)
        advance(20)
        assertEquals(2, transport.attempts)
    }

    @Test
    fun lowLatencyKeepsTheLinkBusy() = runTest {
        val transport = FakeTransport()
        startSender(transport, lowLatency = true)
        advance(160)
        assertEquals(11, transport.attempts)
    }

    @Test
    fun keepAliveCountsFromTheStartOfEachSend() = runTest {
        val transport = FakeTransport().apply { callMs = 2 }
        startSender(transport, lowLatency = true)
        advance(150)
        val gaps = transport.startedAt.zipWithNext { a, b -> b - a }
        assertTrue(gaps.size >= 9, "gaps=$gaps")
        assertEquals(setOf(16L), gaps.toSet())
    }

    @Test
    fun slowSendDoesNotDelayTheNextChange() = runTest {
        val transport = FakeTransport().apply { callMs = 5 }
        startSender(transport)
        input.set(GamepadState(buttons = Button.A.mask))
        runCurrent()
        assertEquals(1, transport.attempts, "still inside the minimum interval")

        // The first send took 5 ms of the 8 ms minimum interval, so 3 ms remain.
        advance(2)
        assertEquals(1, transport.attempts)
        advance(1)
        assertEquals(2, transport.attempts)
        assertEquals(listOf(0L, 8L), transport.startedAt)
    }

    @Test
    fun sendsNothingWhileDisconnected() = runTest {
        val transport = FakeTransport(TransportState.Advertising)
        startSender(transport)
        advance(1_000)
        assertEquals(0, transport.attempts)
    }

    @Test
    fun reconnectGetsAFreshBaseline() = runTest {
        val transport = FakeTransport()
        startSender(transport)
        transport.state.value = TransportState.Advertising
        advance(600)
        val before = transport.attempts

        transport.state.value = TransportState.Connected("host")
        runCurrent()

        assertEquals(before + 1, transport.attempts)
    }

    @Test
    fun failedSendIsRetriedAtTheMinimumInterval() = runTest {
        val transport = FakeTransport().apply { accept = false }
        startSender(transport)
        advance(16)
        assertEquals(3, transport.attempts)
    }

    @Test
    fun stickCurveIsAppliedToSentReports() = runTest {
        settings.value = GamepadSettings(deadzone = 0.5f)
        input.set(GamepadState(leftStick = Stick(x = 0.4f)))
        val transport = FakeTransport()
        transport.sent.test {
            startSender(transport)
            assertEquals(128, awaitItem()[3])
        }
        assertEquals(0.4f, input.state.value.leftStick.x)
    }
}
