package com.example.gametap.platform

interface Haptics {
    fun tick()
}

interface ScreenControl {
    /** Keeps the screen awake and, where the platform needs it, goes landscape and full-screen. */
    fun setGamepadMode(enabled: Boolean)
}

/** Permission and adapter steps that need the UI before the transport can start. */
interface BluetoothAccess {
    /** True when the host has to be told to make this phone discoverable. */
    val needsDiscoverability: Boolean

    /** Requests whatever is missing. Returns true when Bluetooth is usable. */
    suspend fun prepare(): Boolean

    fun requestDiscoverable()
}
