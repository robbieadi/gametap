package io.github.robbieadi.gametap.session

import io.github.robbieadi.gametap.hid.HidDescriptor
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.ForgottenHost
import io.github.robbieadi.gametap.platform.GamepadKeepAlive
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The gamepad from Start for the rest of the process, not for one screen: closing the app's
 * screen, for example by swiping it away from Recents, leaves it on.
 *
 * There is no Stop. Dropping the host made Windows reconnect every 10 to 30 s for as long as the
 * phone was in range, and Steam showed the controller come and go each time; removing the
 * services to prevent that made Windows delete the gamepad. Away from the gamepad screen every
 * control is released, so a connected host gets no input. The gamepad ends when the phone goes
 * out of range, or with the process.
 *
 * Runs on the main thread: [BluetoothAccess] shows permission dialogs.
 */
class GamepadSession(
    private val transport: HidTransport,
    private val access: BluetoothAccess,
    sender: ReportSender,
    private val keepAlive: GamepadKeepAlive,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state: StateFlow<TransportState> = transport.state

    private val mutablePermissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = mutablePermissionDenied.asStateFlow()

    /** See [HidTransport.forgottenHost]. */
    val forgottenHost: StateFlow<ForgottenHost?> = transport.forgottenHost

    /** See [HidTransport.wifiClash]. */
    val wifiClash: StateFlow<Boolean> = transport.wifiClash

    fun dismissWifiClash() = transport.dismissWifiClash()

    // True from Start on. A transport that drops, for example when Bluetooth is turned off, is
    // restarted while the app is visible.
    private var wantsRunning = false
    private var isVisible = false
    private var startJob: Job? = null

    init {
        scope.launch(Dispatchers.Default) { sender.run() }
        scope.launch {
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
        startJob = scope.launch {
            val ready = access.prepare()
            mutablePermissionDenied.value = !ready
            if (!ready) {
                wantsRunning = false // Do not ask again every time the app comes back.
                return@launch
            }
            keepAlive.start()
            transport.start(if (transport.addsTriggerAxes) HidDescriptor.bytesWithTriggerAxes else HidDescriptor.bytes)
        }
    }
}
