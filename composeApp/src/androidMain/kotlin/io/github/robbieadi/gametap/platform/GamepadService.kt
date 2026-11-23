package io.github.robbieadi.gametap.platform

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.robbieadi.gametap.session.GamepadSession
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Runs from Start for the rest of the process, so Android keeps the process, and with it the
 * gamepad's services, while Gametap is in the background. Its notification shows the connection
 * and opens Gametap. It has no Stop button, see [GamepadSession].
 *
 * Swiping Gametap away from Recents closes only the screen; the gamepad stays on, as
 * [GamepadSession] explains. On the Redmi Pad SE, MIUI kept the process through that swipe.
 */
class GamepadService : Service() {
    private val transport: HidTransport by inject()
    private val scope = MainScope()
    private var showing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!showing) {
            showing = true
            if (!enterForeground(notification(transport.state.value))) {
                stopSelf()
                return START_NOT_STICKY
            }
            scope.launch {
                transport.state.collect { state ->
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                }
            }
        }
        // Without the screen there is no one to press Start, so a restart would do nothing.
        return START_NOT_STICKY
    }

    private fun enterForeground(notification: Notification): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        true
    } catch (e: RuntimeException) {
        // A missing Bluetooth permission (SecurityException) or a start from the background.
        Log.w(TAG, "could not enter the foreground", e)
        false
    }

    override fun onDestroy() {
        scope.cancel()
        // An update posted after stopSelf can land after Android removed the notification and
        // leave it behind, ongoing, with no service.
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun notification(state: TransportState): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gamepad", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the gamepad is on"
                    setShowBadge(false)
                },
            )
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Gamepad is on")
            .setContentText(describe(state))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun describe(state: TransportState): String = when (state) {
        is TransportState.Connected -> "Connected to ${state.hostName}"
        TransportState.Advertising -> "Waiting for a host. Open Gametap to let one connect"
        is TransportState.Error -> state.message
        TransportState.Idle, TransportState.Unsupported -> "Starting"
    }

    private companion object {
        const val TAG = "GametapService"
        const val CHANNEL_ID = "gamepad"
        const val NOTIFICATION_ID = 1
    }
}

/** Starts [GamepadService] once the gamepad is on. */
class AndroidKeepAlive(context: Context) : GamepadKeepAlive {
    private val appContext = context.applicationContext

    override fun start() {
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, GamepadService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses from the background; the gamepad still works while on screen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("GametapService", "not allowed to start from the background", e)
            } else {
                throw e
            }
        }
    }
}
