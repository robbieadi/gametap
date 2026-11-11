package io.github.robbieadi.gametap.platform

/**
 * Keeps gamepad mode (landscape, no system bars, screen on) while any screen holds it. When the
 * gamepad hands over to the layout editor, one acquires before or after the other releases,
 * depending on navigation timing; counting keeps the mode on either way.
 */
class ScreenModeLock(private val screenControl: ScreenControl) {
    private var holders = 0

    fun acquire() {
        if (holders++ == 0) screenControl.setGamepadMode(true)
    }

    fun release() {
        if (holders == 0) return
        if (--holders == 0) screenControl.setGamepadMode(false)
    }
}
