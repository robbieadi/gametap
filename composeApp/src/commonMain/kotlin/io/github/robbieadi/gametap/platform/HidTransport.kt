package io.github.robbieadi.gametap.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface TransportState {
    data object Unsupported : TransportState
    data object Idle : TransportState
    data object Advertising : TransportState
    data class Connected(val hostName: String) : TransportState
    data class Error(val message: String) : TransportState
}

/** Moves HID bytes over the radio. The bytes themselves always come from shared code. */
interface HidTransport {
    val state: StateFlow<TransportState>

    /**
     * A paired host that may have forgotten the gamepad, or null. Windows deletes the gamepad when
     * its services disappear while it is connected (the app's process ends, or Bluetooth goes off)
     * and then needs pairing again. Cleared when a host connects.
     */
    val forgottenHost: StateFlow<ForgottenHost?> get() = NoForgottenHost

    /**
     * Told when the app comes to the foreground and leaves it. The transport keeps its host
     * either way; it may advertise or poll less while the app is away.
     */
    fun setForeground(foreground: Boolean) {}

    /** Registers the descriptor and starts advertising. */
    suspend fun start(descriptor: ByteArray)

    /** Drops the host and stops advertising. Returns once the host is gone, or after a timeout. */
    suspend fun stop()

    /** [data] excludes the report ID. Returns false if the report was not accepted. */
    fun sendReport(reportId: Int, data: ByteArray): Boolean
}

/**
 * [lostServices]: the gamepad's services went away while [name] was connected, so it has almost
 * certainly forgotten the gamepad. Otherwise [name] just has not reconnected for a while, which a
 * PC that is off or out of range also explains.
 */
data class ForgottenHost(val name: String, val lostServices: Boolean)

private val NoForgottenHost: StateFlow<ForgottenHost?> = MutableStateFlow(null)
