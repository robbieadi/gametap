package io.github.robbieadi.gametap.platform

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import io.github.robbieadi.gametap.layout.VolumeKey

/** Fed by the activity's dispatchKeyEvent, which sees the keys before the system changes the volume. */
class AndroidVolumeKeyInput : VolumeKeyInput {
    private var handler: ((VolumeKey, Boolean) -> Boolean)? = null

    override val isAvailable: Boolean = true

    override fun setHandler(handler: ((key: VolumeKey, pressed: Boolean) -> Boolean)?) {
        this.handler = handler
    }

    /** Returns true when the event was taken and must not reach the system. */
    fun dispatch(event: KeyEvent): Boolean {
        val handler = handler ?: return false
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
            else -> return false
        }
        if (event.repeatCount == 0) {
            // How long the system held the event back, to check the delay players feel:
            // adb logcat -s GametapKeys
            val heldMs = SystemClock.uptimeMillis() - event.eventTime
            Log.d(TAG, "$key ${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} reached the app after $heldMs ms")
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handler(key, true)
            KeyEvent.ACTION_UP -> handler(key, false)
            else -> false
        }
    }

    private companion object {
        const val TAG = "GametapKeys"
    }
}
