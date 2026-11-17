package io.github.robbieadi.gametap

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.robbieadi.gametap.platform.ActivityProvider
import io.github.robbieadi.gametap.platform.AndroidBluetoothAccess
import io.github.robbieadi.gametap.platform.AndroidVolumeKeyInput
import io.github.robbieadi.gametap.ui.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val activities: ActivityProvider by inject()
    private val bluetoothAccess: AndroidBluetoothAccess by inject()
    private val volumeKeys: AndroidVolumeKeyInput by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so bar icons stay light whatever the system theme is.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        activities.set(this)
        bluetoothAccess.attach(this)
        setContent { App() }
    }

    // Before the window's own handling, which would change the volume.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        volumeKeys.dispatch(event) || super.dispatchKeyEvent(event)
}
