package io.github.robbieadi.gametap.platform

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override val isAvailable: Boolean = vibrator?.hasVibrator() == true

    override fun tick() {
        if (isAvailable) vibrator?.vibrate(VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val TICK_MS = 12L
    }
}

class AndroidScreenControl(private val activities: ActivityProvider) : ScreenControl {
    override fun setGamepadMode(enabled: Boolean) {
        val activity = activities.activity ?: return
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
            // Android otherwise batches touch moves to the display refresh, which limits stick updates.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)
            }
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.requestUnbufferedDispatch(0)
            }
        }
    }
}
