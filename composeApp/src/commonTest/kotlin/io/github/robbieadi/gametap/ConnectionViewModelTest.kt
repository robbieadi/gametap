@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.GamepadSettings
import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.viewmodel.ConnectionViewModel
import kotlinx.coroutines.CoroutineScope
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

private class StartingTransport(override val stopsInBackground: Boolean = true) : HidTransport {
    override val state = MutableStateFlow<TransportState>(TransportState.Idle)
    var starts = 0
    var stops = 0

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

class ConnectionViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val access = CountingAccess()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(transport: HidTransport) = ConnectionViewModel(
        transport,
        access,
        ReportSender(InputStateHolder(), transport, MutableStateFlow(GamepadSettings())),
        CoroutineScope(dispatcher),
    )

    /** Started, connected and visible: the state the app is in during play. */
    private fun TestScope.playing(transport: StartingTransport): ConnectionViewModel {
        val viewModel = viewModel(transport)
        viewModel.onAppVisible(true)
        viewModel.start()
        runCurrent()
        transport.state.value = TransportState.Connected("host")
        runCurrent()
        return viewModel
    }

    @Test
    fun startAdvertisesAfterAccessIsGranted() = runTest(dispatcher) {
        val transport = StartingTransport()
        viewModel(transport).start()
        runCurrent()
        assertEquals(1, access.prepares)
        assertEquals(1, transport.starts)
        assertEquals(TransportState.Advertising, transport.state.value)
    }

    @Test
    fun stopsAfterTwoSecondsInTheBackgroundAndRestartsOnReturn() = runTest(dispatcher) {
        val transport = StartingTransport()
        val viewModel = playing(transport)

        viewModel.onAppVisible(false)
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(0, transport.stops, "must wait before stopping")

        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, transport.stops)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, transport.starts, "must not restart while away")

        viewModel.onAppVisible(true)
        runCurrent()
        assertEquals(2, transport.starts)
        assertEquals(TransportState.Advertising, transport.state.value)
    }

    @Test
    fun quickReturnKeepsTheHost() = runTest(dispatcher) {
        val transport = StartingTransport()
        val viewModel = playing(transport)

        viewModel.onAppVisible(false)
        advanceTimeBy(1_000)
        viewModel.onAppVisible(true)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(0, transport.stops)
        assertEquals(TransportState.Connected("host"), transport.state.value)
    }

    @Test
    fun transportThatKeepsRunningIsLeftAloneInTheBackground() = runTest(dispatcher) {
        val transport = StartingTransport(stopsInBackground = false)
        val viewModel = playing(transport)

        viewModel.onAppVisible(false)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(0, transport.stops)
        assertEquals(TransportState.Connected("host"), transport.state.value)
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
    fun stopMeansStayStopped() = runTest(dispatcher) {
        val transport = StartingTransport()
        val viewModel = playing(transport)

        viewModel.stop()
        runCurrent()
        viewModel.onAppVisible(false)
        advanceTimeBy(10_000)
        viewModel.onAppVisible(true)
        runCurrent()

        assertEquals(1, transport.starts)
        assertEquals(TransportState.Idle, transport.state.value)
    }

    @Test
    fun nothingStartsBeforeTheUserPressesStart() = runTest(dispatcher) {
        val transport = StartingTransport()
        val viewModel = viewModel(transport)

        viewModel.onAppVisible(true)
        viewModel.onAppVisible(false)
        advanceTimeBy(10_000)
        viewModel.onAppVisible(true)
        runCurrent()

        assertEquals(0, transport.starts)
        assertEquals(0, access.prepares)
    }

    @Test
    fun refusedPermissionIsNotAskedAgainOnReturn() = runTest(dispatcher) {
        access.granted = false
        val transport = StartingTransport()
        val viewModel = viewModel(transport)
        viewModel.onAppVisible(true)
        viewModel.start()
        runCurrent()

        viewModel.onAppVisible(false)
        advanceTimeBy(10_000)
        viewModel.onAppVisible(true)
        runCurrent()

        assertEquals(1, access.prepares)
        assertEquals(0, transport.starts)
    }
}
