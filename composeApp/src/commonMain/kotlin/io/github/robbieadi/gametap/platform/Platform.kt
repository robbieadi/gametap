package io.github.robbieadi.gametap.platform

interface Haptics {
    /** False when the device has no vibration hardware, such as most tablets. */
    val isAvailable: Boolean

    fun tick()
}

interface ScreenControl {
    /** Keeps the screen awake and, where the platform needs it, goes landscape and full-screen. */
    fun setGamepadMode(enabled: Boolean)
}

/** Permission and adapter steps that need the UI before the transport can start. */
interface BluetoothAccess {
    /** Requests whatever is missing. Returns true when Bluetooth is usable. */
    suspend fun prepare(): Boolean
}
