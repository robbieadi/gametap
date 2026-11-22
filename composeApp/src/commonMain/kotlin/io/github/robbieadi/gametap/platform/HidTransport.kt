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

    /**
     * Told when the app comes to the foreground and leaves it. The transport keeps its host
     * either way; it may advertise or poll less while the app is away.
     */
    fun setForeground(foreground: Boolean) {}

    /** Registers the descriptor and starts advertising. */
    suspend fun start(descriptor: ByteArray)

    suspend fun stop()

    /** [data] excludes the report ID. Returns false if the report was not accepted. */
    fun sendReport(reportId: Int, data: ByteArray): Boolean
}
