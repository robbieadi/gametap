package com.example.gametap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gametap.hid.HidDescriptor
import com.example.gametap.input.ReportSender
import com.example.gametap.platform.BluetoothAccess
import com.example.gametap.platform.HidTransport
import com.example.gametap.platform.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** App-wide connection owner. It runs the sender so reports keep flowing on every screen. */
class ConnectionViewModel(
    private val transport: HidTransport,
    private val access: BluetoothAccess,
    sender: ReportSender,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<TransportState> = transport.state
    val needsDiscoverability: Boolean = access.needsDiscoverability

    private val mutablePermissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = mutablePermissionDenied.asStateFlow()

    // True from Start until Stop. Android drops the gamepad about 2 s after the app leaves the
    // foreground, so this is what tells a drop to undo apart from the user stopping on purpose.
    private var wantsRunning = false
    private var isVisible = false
    private var startJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) { sender.run() }
        viewModelScope.launch {
            transport.state.collect { if (it == TransportState.Idle) restartIfDropped() }
        }
    }

    fun start() {
        wantsRunning = true
        launchStart(promptForNewHost = true)
    }

    /** Call when the app comes to the foreground (true) and leaves it (false). */
    fun onAppVisible(visible: Boolean) {
        isVisible = visible
        if (visible) restartIfDropped()
    }

    /** Restarts silently: a paired host reconnects by itself, and nobody asked for the prompt. */
    private fun restartIfDropped() {
        val dropped = wantsRunning && isVisible && transport.state.value == TransportState.Idle
        if (dropped && startJob?.isActive != true) launchStart(promptForNewHost = false)
    }

    private fun launchStart(promptForNewHost: Boolean) {
        startJob = viewModelScope.launch {
            val ready = access.prepare()
            mutablePermissionDenied.value = !ready
            if (!ready) {
                wantsRunning = false // Do not ask again every time the app comes back.
                return@launch
            }
            transport.start(HidDescriptor.bytes)
            val reached = withTimeoutOrNull(START_TIMEOUT_MS) {
                transport.state.first { it != TransportState.Idle }
            }
            if (reached != TransportState.Advertising || !promptForNewHost) return@launch
            if (transport.isReconnecting && awaitReconnect()) return@launch
            if (transport.state.value == TransportState.Advertising) access.requestDiscoverable()
        }
    }

    /** Returns true if the state moved on, so the discoverable prompt is not needed. */
    private suspend fun awaitReconnect(): Boolean = withTimeoutOrNull(RECONNECT_TIMEOUT_MS) {
        transport.state.first { it != TransportState.Advertising }
    } != null

    fun stop() {
        wantsRunning = false
        startJob?.cancel()
        viewModelScope.launch { transport.stop() }
    }

    fun requestDiscoverable() = access.requestDiscoverable()

    override fun onCleared() {
        appScope.launch { transport.stop() }
    }

    private companion object {
        const val START_TIMEOUT_MS = 5_000L
        const val RECONNECT_TIMEOUT_MS = 5_000L
    }
}
