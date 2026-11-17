package io.github.robbieadi.gametap.platform

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

    /** True when the gamepad should stop shortly after the app leaves the foreground. */
    val stopsInBackground: Boolean get() = false

    /** Registers the descriptor and starts advertising. */
    suspend fun start(descriptor: ByteArray)

    suspend fun stop()

    /** [data] excludes the report ID. Returns false if the report was not accepted. */
    fun sendReport(reportId: Int, data: ByteArray): Boolean
}
