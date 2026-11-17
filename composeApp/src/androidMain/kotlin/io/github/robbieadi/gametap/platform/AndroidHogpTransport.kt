package io.github.robbieadi.gametap.platform

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.hid.HidReportEncoder
import io.github.robbieadi.gametap.hogp.HogpAccess
import io.github.robbieadi.gametap.hogp.HogpCharacteristic
import io.github.robbieadi.gametap.hogp.HogpProfile
import io.github.robbieadi.gametap.hogp.HogpRead
import io.github.robbieadi.gametap.hogp.LatestReportQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Publishes [HogpProfile] from an Android GATT server. Permissions come from [BluetoothAccess].
 *
 * Classic Bluetooth HID was dropped because Samsung phones kept that link in sniff mode, a power
 * saving state that delayed reports by up to 700 ms. Low Energy has no sniff mode: the host polls
 * at a fixed connection interval and a report goes out at the next poll.
 *
 * - The host is reported as connected once it enables notifications on the input report. Android
 *   does not keep that setting between connections; BlueZ writes it on every connect.
 * - The app also opens a GATT client back to the host on the same link and asks for high
 *   priority, which requests an 11.25 to 15 ms connection interval. Android gives a GATT server no
 *   way to ask for that itself. On Linux this halved the spread of report delays.
 */
@SuppressLint("MissingPermission")
class AndroidHogpTransport(context: Context) : HidTransport {
    private val appContext = context.applicationContext
    private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)
    private val lock = Any()

    private val mutableState = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = mutableState.asStateFlow()

    // A GATT server would keep running in the background. Stopping keeps the behaviour users had
    // with classic HID, which Android dropped about 2 s after the app left the screen.
    override val stopsInBackground: Boolean get() = true

    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising = false
    private var reportCharacteristic: BluetoothGattCharacteristic? = null
    private val staticValues = HashMap<UUID, ByteArray>()
    private val pendingServices = ArrayDeque<BluetoothGattService>()

    private var host: BluetoothDevice? = null
    private var subscribed = false
    private var priorityClient: BluetoothGatt? = null

    private var latestReport: ByteArray = HidReportEncoder.encode(GamepadState())

    // Android allows one notification in flight; onNotificationSent confirms each one.
    private val reports = LatestReportQueue(waitsForCompletion = true, trySend = ::notifyHost)

    override suspend fun start(descriptor: ByteArray) {
        synchronized(lock) {
            if (server != null) return
            val adapter = manager?.adapter
            val leAdvertiser = adapter?.bluetoothLeAdvertiser
            if (adapter == null || leAdvertiser == null || !adapter.isMultipleAdvertisementSupported) {
                mutableState.value = TransportState.Unsupported
                return
            }
            val gattServer = manager.openGattServer(appContext, serverCallback)
            if (gattServer == null) {
                mutableState.value = TransportState.Error("Could not open a GATT server")
                return
            }
            server = gattServer
            advertiser = leAdvertiser
            staticValues.clear()
            pendingServices.clear()
            HogpProfile.services(descriptor).forEach { service ->
                pendingServices.addLast(
                    BluetoothGattService(uuid(service.uuid), BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                        service.characteristics.forEach { addCharacteristic(toGatt(it)) }
                    },
                )
            }
            // Android accepts one service at a time; the rest follow from onServiceAdded.
            gattServer.addService(pendingServices.removeFirst())
        }
    }

    override suspend fun stop() {
        synchronized(lock) {
            val gattServer = server ?: return
            stopAdvertisingLocked()
            closePriorityClientLocked()
            host?.let(gattServer::cancelConnection)
            gattServer.clearServices()
            gattServer.close()
            server = null
            advertiser = null
            host = null
            subscribed = false
            reportCharacteristic = null
            reports.reset()
            mutableState.value = TransportState.Idle
        }
    }

    override fun sendReport(reportId: Int, data: ByteArray): Boolean = synchronized(lock) {
        latestReport = data
        reports.offer(data)
        host != null && subscribed
    }

    /** Called by [reports] under [lock]. */
    @Suppress("DEPRECATION")
    private fun notifyHost(report: ByteArray): Boolean {
        val gattServer = server ?: return false
        val device = host ?: return false
        val characteristic = reportCharacteristic ?: return false
        if (!subscribed) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattServer.notifyCharacteristicChanged(device, characteristic, false, report) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.value = report
            gattServer.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    private fun toGatt(spec: HogpCharacteristic): BluetoothGattCharacteristic {
        val encrypted = spec.access == HogpAccess.Encrypted
        var properties = 0
        var permissions = 0
        if (spec.readable) {
            properties = properties or BluetoothGattCharacteristic.PROPERTY_READ
            permissions = permissions or
                if (encrypted) BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED else BluetoothGattCharacteristic.PERMISSION_READ
        }
        if (spec.writableWithoutResponse) {
            properties = properties or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            permissions = permissions or
                if (encrypted) BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED else BluetoothGattCharacteristic.PERMISSION_WRITE
        }
        if (spec.notifies) properties = properties or BluetoothGattCharacteristic.PROPERTY_NOTIFY

        val characteristic = BluetoothGattCharacteristic(uuid(spec.uuid), properties, permissions)
        val descriptorRead =
            if (encrypted) BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED else BluetoothGattDescriptor.PERMISSION_READ
        val descriptorWrite =
            if (encrypted) BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED else BluetoothGattDescriptor.PERMISSION_WRITE
        if (spec.notifies) {
            characteristic.addDescriptor(BluetoothGattDescriptor(uuid(HogpProfile.CLIENT_CONFIG), descriptorRead or descriptorWrite))
        }
        if (spec.reportReference != null) {
            characteristic.addDescriptor(BluetoothGattDescriptor(uuid(HogpProfile.REPORT_REFERENCE), descriptorRead))
        }
        spec.value?.let { staticValues[characteristic.uuid] = it }
        if (spec.uuid == HogpProfile.REPORT) reportCharacteristic = characteristic
        return characteristic
    }

    private fun startAdvertisingLocked() {
        val leAdvertiser = advertiser ?: return
        if (advertising) return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(uuid(HogpProfile.HID_SERVICE)))
            .build()
        // The phone's own name, since Android has no per-app advertised name.
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        advertising = true
        leAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private fun stopAdvertisingLocked() {
        if (!advertising) return
        advertising = false
        advertiser?.stopAdvertising(advertiseCallback)
    }

    private fun openPriorityClientLocked(device: BluetoothDevice) {
        if (priorityClient != null) return
        priorityClient = device.connectGatt(appContext, false, priorityCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun closePriorityClientLocked() {
        priorityClient?.disconnect()
        priorityClient?.close()
        priorityClient = null
    }

    private fun respond(gattServer: BluetoothGattServer, device: BluetoothDevice, requestId: Int, offset: Int, read: HogpRead) {
        when (read) {
            is HogpRead.Value -> gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, read.bytes)
            HogpRead.InvalidOffset -> gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            HogpRead.NotReadable -> gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "advertising")
            synchronized(lock) {
                if (host == null) mutableState.value = TransportState.Advertising
            }
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "advertising failed: $errorCode")
            synchronized(lock) {
                advertising = false
                if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                    mutableState.value = TransportState.Error("Could not advertise (error $errorCode)")
                }
            }
        }
    }

    private val priorityCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val requested = gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                Log.d(TAG, "priority client connected, high priority requested: $requested")
            }
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            synchronized(lock) {
                val gattServer = server ?: return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "service ${service.uuid} not added: $status")
                    if (service.uuid == uuid(HogpProfile.HID_SERVICE)) {
                        pendingServices.clear()
                        mutableState.value = TransportState.Error("Android refused the HID service ($status)")
                        return
                    }
                }
                val next = pendingServices.removeFirstOrNull()
                if (next != null) gattServer.addService(next) else startAdvertisingLocked()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "connection ${device.address} status $status state $newState")
            synchronized(lock) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (host != null && host != device) return // One host at a time.
                        host = device
                        subscribed = false
                        reports.reset()
                        stopAdvertisingLocked()
                        openPriorityClientLocked(device)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> if (device == host) {
                        host = null
                        subscribed = false
                        reports.reset()
                        closePriorityClientLocked()
                        if (server != null) {
                            mutableState.value = TransportState.Advertising
                            startAdvertisingLocked()
                        }
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val gattServer = server ?: return
            val value = synchronized(lock) {
                if (characteristic.uuid == reportCharacteristic?.uuid) latestReport else staticValues[characteristic.uuid]
            }
            respond(gattServer, device, requestId, offset, HogpProfile.readFrom(value, offset))
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val gattServer = server ?: return
            val value = when (descriptor.uuid) {
                uuid(HogpProfile.REPORT_REFERENCE) -> HogpProfile.inputReportReference
                uuid(HogpProfile.CLIENT_CONFIG) -> synchronized(lock) {
                    if (subscribed && device == host) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                }
                else -> null
            }
            respond(gattServer, device, requestId, offset, HogpProfile.readFrom(value, offset))
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val gattServer = server ?: return
            if (descriptor.uuid == uuid(HogpProfile.CLIENT_CONFIG)) {
                val enabled = value != null && value.isNotEmpty() && (value[0].toInt() and 0x01) != 0
                Log.d(TAG, "notifications ${if (enabled) "on" else "off"} for ${device.address}")
                synchronized(lock) {
                    if (device == host) {
                        subscribed = enabled
                        reports.reset()
                        if (enabled) {
                            mutableState.value = TransportState.Connected(device.name ?: device.address)
                            reports.offer(latestReport) // Hosts can stall until a first report arrives.
                        } else {
                            mutableState.value = TransportState.Advertising
                        }
                    }
                }
            }
            if (responseNeeded) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            // Control Point and Protocol Mode have no effect: no suspend state and no boot mode.
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(lock) { reports.onReady() }
        }
    }

    private companion object {
        const val TAG = "GametapHogp"

        fun uuid(short: Int): UUID = UUID.fromString(HogpProfile.uuidString(short))
    }
}
