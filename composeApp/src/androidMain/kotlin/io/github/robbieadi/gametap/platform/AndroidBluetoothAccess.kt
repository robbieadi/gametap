package io.github.robbieadi.gametap.platform

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

class AndroidBluetoothAccess(context: Context) : BluetoothAccess {
    private val appContext = context.applicationContext
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var enableLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private var pendingEnable: CompletableDeferred<Unit>? = null

    /** Call from Activity.onCreate: result launchers must be registered before the activity starts. */
    fun attach(activity: ComponentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results -> pendingPermission?.complete(REQUIRED_PERMISSIONS.all { results[it] ?: hasPermission(it) }) }
        enableLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { pendingEnable?.complete(Unit) }
    }

    override suspend fun prepare(): Boolean {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return true // The transport reports Unsupported.
        val missing = (REQUIRED_PERMISSIONS + OPTIONAL_PERMISSIONS).filterNot(::hasPermission)
        if (missing.isNotEmpty() && !requestPermissions(missing.toTypedArray())) return false
        if (!isEnabled(adapter)) requestEnable()
        return isEnabled(adapter)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    /** True if the required ones are granted; the optional ones may be refused. */
    private suspend fun requestPermissions(permissions: Array<String>): Boolean {
        val launcher = permissionLauncher ?: return false
        val result = CompletableDeferred<Boolean>()
        pendingPermission = result
        launcher.launch(permissions)
        return result.await()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestEnable() {
        val launcher = enableLauncher ?: return
        val result = CompletableDeferred<Unit>()
        pendingEnable = result
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        result.await()
    }

    private fun isEnabled(adapter: BluetoothAdapter): Boolean = adapter.isEnabled

    private companion object {
        val REQUIRED_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                emptyArray() // BLUETOOTH and BLUETOOTH_ADMIN are granted at install time.
            }

        // Shows the gamepad's notification. Without it the gamepad still runs; Android asks at
        // most twice, then refuses by itself.
        val OPTIONAL_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
    }
}
