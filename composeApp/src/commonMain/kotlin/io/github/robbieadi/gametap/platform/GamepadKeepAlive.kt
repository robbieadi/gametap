package io.github.robbieadi.gametap.platform

/**
 * Keeps the app's process running from Start on, where the platform would otherwise end it in
 * the background. If it ends while a host is connected, the gamepad's services disappear under
 * the host, and Windows then deletes the gamepad and does not reconnect.
 */
interface GamepadKeepAlive {
    /** Called once the Bluetooth permissions are granted, while the app is on screen. */
    fun start()
}

/** For platforms that keep the app running by other means, such as iOS's Bluetooth background mode. */
object NoKeepAlive : GamepadKeepAlive {
    override fun start() {}
}
