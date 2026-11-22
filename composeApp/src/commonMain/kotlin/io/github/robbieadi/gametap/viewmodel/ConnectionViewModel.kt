package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.robbieadi.gametap.hid.HidDescriptor
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-wide connection owner. It runs the sender so reports keep flowing on every screen. */
class ConnectionViewModel(
    private val transport: HidTransport,
    private val access: BluetoothAccess,
    sender: ReportSender,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<TransportState> = transport.state

    private val mutablePermissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = mutablePermissionDenied.asStateFlow()

    // True from Start until Stop. A transport that drops, for example when Bluetooth is turned off,
    // is restarted while the app is visible; this tells that apart from the user stopping.
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
        launchStart()
    }

    /**
     * Call when the app comes to the foreground (true) and leaves it (false). The gamepad stays
     * connected while the app is away: stopping it removed the HID service from a host that was
     * still connected, and Windows then deleted the gamepad and did not reconnect.
     */
    fun onAppVisible(visible: Boolean) {
        isVisible = visible
        transport.setForeground(visible)
        if (visible) restartIfDropped()
    }

    /** A paired host reconnects by itself once the transport advertises again. */
    private fun restartIfDropped() {
        val dropped = wantsRunning && isVisible && transport.state.value == TransportState.Idle
        if (dropped && startJob?.isActive != true) launchStart()
    }

    private fun launchStart() {
        startJob = viewModelScope.launch {
            val ready = access.prepare()
            mutablePermissionDenied.value = !ready
            if (!ready) {
                wantsRunning = false // Do not ask again every time the app comes back.
                return@launch
            }
            transport.start(HidDescriptor.bytes)
        }
    }

    fun stop() {
        wantsRunning = false
        startJob?.cancel()
        viewModelScope.launch { transport.stop() }
    }

    override fun onCleared() {
        appScope.launch { transport.stop() }
    }
}
