@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.DeadzoneCurve
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.GamepadKeepAlive
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.session.GamepadSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class StartingTransport : HidTransport {
    override val state = MutableStateFlow<TransportState>(TransportState.Idle)
    var starts = 0
    var stops = 0
    val foregroundCalls = mutableListOf<Boolean>()

    override fun setForeground(foreground: Boolean) {
        foregroundCalls += foreground
    }

    override suspend fun start(descriptor: ByteArray) {
        starts++
        state.value = TransportState.Advertising
    }

    /** The transport going away by itself, for example when Bluetooth is turned off. */
    fun drops() {
        state.value = TransportState.Idle
    }

    override suspend fun stop() {
        stops++
        state.value = TransportState.Idle
    }

    override fun sendReport(reportId: Int, data: ByteArray): Boolean = true
}

private class CountingAccess : BluetoothAccess {
    var granted = true
    var prepares = 0
    override suspend fun prepare(): Boolean {
        prepares++
        return granted
    }
}

private class CountingKeepAlive : GamepadKeepAlive {
    var running = false
    var starts = 0

    override fun start() {
        running = true
        starts++
    }
}

class GamepadSessionTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val access = CountingAccess()
    private val keepAlive = CountingKeepAlive()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun session(transport: HidTransport) = GamepadSession(
        transport,
        access,
        ReportSender(InputStateHolder(), transport, stickCurve = { DeadzoneCurve() }),
        keepAlive,
    )

    /** Started, connected and visible: the state the app is in during play. */
    private fun TestScope.playing(transport: StartingTransport): GamepadSession {
        val session = session(transport)
        session.onAppVisible(true)
        session.start()
        runCurrent()
        transport.state.value = TransportState.Connected("host")
        runCurrent()
        return session
    }

    @Test
    fun startAdvertisesAfterAccessIsGranted() = runTest(dispatcher) {
        val transport = StartingTransport()
        session(transport).start()
        runCurrent()
        assertEquals(1, access.prepares)
        assertEquals(1, transport.starts)
        assertEquals(TransportState.Advertising, transport.state.value)
        assertEquals(true, keepAlive.running)
    }

    @Test
    fun keepsTheHostInTheBackgroundAndTellsTheTransport() = runTest(dispatcher) {
        val transport = StartingTransport()
        val session = playing(transport)

        session.onAppVisible(false)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, transport.stops)
        assertEquals(TransportState.Connected("host"), transport.state.value)
        assertEquals(true, keepAlive.running)

        session.onAppVisible(true)
        runCurrent()
        assertEquals(1, transport.starts)
        assertEquals(listOf(true, false, true), transport.foregroundCalls)
    }

    @Test
    fun dropWhileAwayIsRestartedOnReturn() = runTest(dispatcher) {
        val transport = StartingTransport()
        val session = playing(transport)

        session.onAppVisible(false)
        transport.drops()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, transport.starts, "must not restart while away")

        session.onAppVisible(true)
        runCurrent()
        assertEquals(2, transport.starts)
        assertEquals(TransportState.Advertising, transport.state.value)
    }

    @Test
    fun dropWhileVisibleIsRestarted() = runTest(dispatcher) {
        val transport = StartingTransport()
        playing(transport)

        transport.drops()
        runCurrent()

        assertEquals(2, transport.starts)
    }

    @Test
    fun nothingStartsBeforeTheUserPressesStart() = runTest(dispatcher) {
        val transport = StartingTransport()
        val session = session(transport)

        session.onAppVisible(true)
        session.onAppVisible(false)
        advanceTimeBy(10_000)
        session.onAppVisible(true)
        runCurrent()

        assertEquals(0, transport.starts)
        assertEquals(0, access.prepares)
    }

    @Test
    fun refusedPermissionIsNotAskedAgainOnReturn() = runTest(dispatcher) {
        access.granted = false
        val transport = StartingTransport()
        val session = session(transport)
        session.onAppVisible(true)
        session.start()
        runCurrent()

        session.onAppVisible(false)
        advanceTimeBy(10_000)
        session.onAppVisible(true)
        runCurrent()

        assertEquals(1, access.prepares)
        assertEquals(0, transport.starts)
        assertEquals(0, keepAlive.starts)
    }
}
