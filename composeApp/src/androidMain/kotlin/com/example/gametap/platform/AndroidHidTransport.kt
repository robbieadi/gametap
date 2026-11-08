package com.example.gametap.platform

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.example.gametap.hid.HidDescriptor
import com.example.gametap.hid.HidReportEncoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/** Classic Bluetooth HID through the [BluetoothHidDevice] profile. Permissions come from [BluetoothAccess]. */
@SuppressLint("MissingPermission")
class AndroidHidTransport(context: Context) : HidTransport {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val mutableState = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = mutableState.asStateFlow()

    @Volatile override var isReconnecting: Boolean = false
        private set

    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var lastReport = neutralReport()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isReconnecting = registered && pluggedDevice != null
            if (!registered) {
                // Android also unregisters on its own, about 2 s after the app leaves the
                // foreground. Release the proxy too, or the next start() would return early.
                hid?.let(::closeProxy)
                mutableState.value = TransportState.Idle
                return
            }
            mutableState.value = TransportState.Advertising
            // Android hands back the last bonded host, so reconnect to it without any UI.
            if (pluggedDevice != null) hid?.connect(pluggedDevice)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    mutableState.value = TransportState.Connected(device.name ?: device.address)
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (device == host) {
                    host = null
                    if (hid != null) mutableState.value = TransportState.Advertising
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val valid = type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == HidDescriptor.REPORT_ID
            if (valid) {
                hid?.replyReport(device, type, id, lastReport)
            } else {
                hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    override suspend fun start(descriptor: ByteArray) {
        if (hid != null) return
        val bluetooth = adapter
        if (bluetooth == null) {
            mutableState.value = TransportState.Unsupported
            return
        }
        val proxy = connectProxy(bluetooth)
        if (proxy == null) {
            mutableState.value = TransportState.Unsupported
            return
        }
        hid = proxy
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Gametap",
            "Phone Bluetooth gamepad",
            "Gametap",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            descriptor,
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
        if (!proxy.registerApp(sdp, null, qos, callbackExecutor, callback)) {
            closeProxy(proxy)
            mutableState.value = TransportState.Unsupported
        }
    }

    override suspend fun stop() {
        val proxy = hid ?: return
        host?.let(proxy::disconnect)
        proxy.unregisterApp()
        closeProxy(proxy)
        mutableState.value = TransportState.Idle
    }

    override fun sendReport(reportId: Int, data: ByteArray): Boolean {
        lastReport = data
        val device = host ?: return false
        return hid?.sendReport(device, reportId, data) ?: false
    }

    private suspend fun connectProxy(bluetooth: BluetoothAdapter): BluetoothHidDevice? {
        val connected = CompletableDeferred<BluetoothHidDevice?>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                connected.complete(proxy as? BluetoothHidDevice)
            }

            override fun onServiceDisconnected(profile: Int) {
                connected.complete(null)
                host = null
                hid = null
                mutableState.value = TransportState.Idle
            }
        }
        if (!bluetooth.getProfileProxy(appContext, listener, BluetoothProfile.HID_DEVICE)) return null
        return withTimeoutOrNull(PROXY_TIMEOUT_MS) { connected.await() }
    }

    /** Safe to call from stop() and the unregister callback at once: only the first call closes. */
    private fun closeProxy(proxy: BluetoothHidDevice) {
        synchronized(this) {
            if (hid !== proxy) return
            host = null
            hid = null
        }
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
    }

    private companion object {
        const val PROXY_TIMEOUT_MS = 5_000L

        fun neutralReport() = HidReportEncoder.encode(com.example.gametap.domain.GamepadState())
    }
}
