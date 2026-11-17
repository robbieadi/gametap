package io.github.robbieadi.gametap.platform

import io.github.robbieadi.gametap.layout.VolumeKey

/** The phone's volume keys, for profiles that bind them to gamepad inputs. */
interface VolumeKeyInput {
    /** False where the platform cannot capture the keys, as on iOS. */
    val isAvailable: Boolean

    /**
     * While set, [handler] sees every press and release on the UI thread, key repeats included,
     * and returns true to keep the event from changing the volume. Null leaves the keys to the system.
     */
    fun setHandler(handler: ((key: VolumeKey, pressed: Boolean) -> Boolean)?)
}

/**
 * iOS has no public way to take the volume keys: the known tricks watch the volume change, so
 * they cannot tell a hold from a press and still move the volume.
 */
object NoVolumeKeyInput : VolumeKeyInput {
    override val isAvailable: Boolean = false

    override fun setHandler(handler: ((key: VolumeKey, pressed: Boolean) -> Boolean)?) = Unit
}
