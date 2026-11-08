@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.gametap

import com.example.gametap.domain.GamepadSettings
import com.example.gametap.input.InputStateHolder
import com.example.gametap.input.ReportSender
import com.example.gametap.platform.BluetoothAccess
import com.example.gametap.platform.HidTransport
import com.example.gametap.platform.TransportState
import com.example.gametap.viewmodel.ConnectionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class StartingTransport(private val reconnecting: Boolean) : HidTransport {
    override val state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val isReconnecting: Boolean get() = reconnecting

    override suspend fun start(descriptor: ByteArray) {
        state.value = TransportState.Advertising
    }

    override suspend fun stop() {
        state.value = TransportState.Idle
    }

    override fun sendReport(reportId: Int, data: ByteArray): Boolean = true
}

private class CountingAccess : BluetoothAccess {
    var prompts = 0
    override val needsDiscoverability: Boolean = true
    override suspend fun prepare(): Boolean = true
    override fun requestDiscoverable() {
        prompts++
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

    @Test
    fun newHostIsPromptedImmediately() = runTest(dispatcher) {
        viewModel(StartingTransport(reconnecting = false)).start()
        runCurrent()
        assertEquals(1, access.prompts)
    }

    @Test
    fun pairedHostThatReconnectsIsNeverPrompted() = runTest(dispatcher) {
        val transport = StartingTransport(reconnecting = true)
        viewModel(transport).start()
        advanceTimeBy(1_000)
        transport.state.value = TransportState.Connected("host")
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, access.prompts)
    }

    @Test
    fun pairedHostThatStaysAwayIsPromptedAfterTheWait() = runTest(dispatcher) {
        viewModel(StartingTransport(reconnecting = true)).start()
        advanceTimeBy(4_900)
        runCurrent()
        assertEquals(0, access.prompts)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, access.prompts)
    }

    @Test
    fun stoppingDuringTheWaitCancelsThePrompt() = runTest(dispatcher) {
        val transport = StartingTransport(reconnecting = true)
        val viewModel = viewModel(transport)
        viewModel.start()
        advanceTimeBy(1_000)
        viewModel.stop()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, access.prompts)
    }
}
