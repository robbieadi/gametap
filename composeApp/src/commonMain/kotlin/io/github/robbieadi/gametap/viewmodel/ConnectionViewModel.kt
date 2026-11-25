package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import io.github.robbieadi.gametap.platform.ForgottenHost
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.session.GamepadSession
import kotlinx.coroutines.flow.StateFlow

/** The screens' view of [GamepadSession], which outlives them: clearing this leaves the gamepad on. */
class ConnectionViewModel(private val session: GamepadSession) : ViewModel() {
    val state: StateFlow<TransportState> = session.state
    val permissionDenied: StateFlow<Boolean> = session.permissionDenied
    val forgottenHost: StateFlow<ForgottenHost?> = session.forgottenHost
    val wifiClash: StateFlow<Boolean> = session.wifiClash

    fun dismissWifiClash() = session.dismissWifiClash()

    fun start() = session.start()

    /** See [GamepadSession.onAppVisible]. */
    fun onAppVisible(visible: Boolean) = session.onAppVisible(visible)
}
