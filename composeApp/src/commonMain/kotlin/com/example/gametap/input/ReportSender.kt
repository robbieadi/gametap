package com.example.gametap.input

import com.example.gametap.domain.GamepadSettings
import com.example.gametap.domain.GamepadState
import com.example.gametap.hid.HidDescriptor
import com.example.gametap.hid.HidReportEncoder
import com.example.gametap.platform.HidTransport
import com.example.gametap.platform.TransportState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * Sends a report as soon as input changes, at most once per [minIntervalMs].
 *
 * While [lowLatency] is on it also repeats the last report every [activeKeepAliveMs]. That traffic
 * is what keeps latency down: on an idle link, Bluetooth sniff subrating stretches the radio's
 * wake-up interval, and measured send-to-host time rose from about 17 ms to as much as 180 ms.
 * There is no public Android API to turn sniff off. [idleKeepAliveMs] applies otherwise, to save
 * battery. A failed send is retried, and a new connection always gets a baseline report.
 */
class ReportSender(
    private val input: InputStateHolder,
    private val transport: HidTransport,
    private val settings: StateFlow<GamepadSettings>,
    private val minIntervalMs: Long = 8,
    private val activeKeepAliveMs: Long = 16,
    private val idleKeepAliveMs: Long = 500,
) {
    /** Turn on while the player is using the gamepad screen. */
    @Volatile
    var lowLatency: Boolean = false

    suspend fun run() {
        var lastSent: ByteArray? = null
        while (true) {
            if (transport.state.value !is TransportState.Connected) {
                lastSent = null
                transport.state.first { it is TransportState.Connected }
            }
            val report = encode(input.state.value)
            if (transport.sendReport(HidDescriptor.REPORT_ID, report)) lastSent = report
            delay(minIntervalMs)

            val keepAliveMs = if (lowLatency) activeKeepAliveMs else idleKeepAliveMs
            val sent = lastSent
            withTimeoutOrNull(keepAliveMs - minIntervalMs) {
                input.state.first { sent == null || !encode(it).contentEquals(sent) }
            }
        }
    }

    private fun encode(state: GamepadState): ByteArray {
        val curve = settings.value.stickCurve
        return HidReportEncoder.encode(
            state.copy(
                leftStick = curve.apply(state.leftStick),
                rightStick = curve.apply(state.rightStick),
            ),
        )
    }
}
