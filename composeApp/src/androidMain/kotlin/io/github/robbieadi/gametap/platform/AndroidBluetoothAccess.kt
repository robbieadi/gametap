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
    private var discoverableLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private var pendingEnable: CompletableDeferred<Unit>? = null

    override val needsDiscoverability: Boolean = true

    /** Call from Activity.onCreate: result launchers must be registered before the activity starts. */
    fun attach(activity: ComponentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results -> pendingPermission?.complete(results.values.all { it }) }
        enableLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { pendingEnable?.complete(Unit) }
        discoverableLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {}
    }

    override suspend fun prepare(): Boolean {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return true // The transport reports Unsupported.
        if (!hasPermissions() && !requestPermissions()) return false
        if (!isEnabled(adapter)) requestEnable()
        return isEnabled(adapter)
    }

    override fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        discoverableLauncher?.launch(intent)
    }

    private fun hasPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun requestPermissions(): Boolean {
        val launcher = permissionLauncher ?: return false
        val result = CompletableDeferred<Boolean>()
        pendingPermission = result
        launcher.launch(REQUIRED_PERMISSIONS)
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
        const val DISCOVERABLE_SECONDS = 300

        val REQUIRED_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                emptyArray() // BLUETOOTH and BLUETOOTH_ADMIN are granted at install time.
            }
    }
}
