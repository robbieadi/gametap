package io.github.robbieadi.gametap.platform

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Publishes [HogpProfile] from an Android GATT server. Permissions come from [BluetoothAccess].
 *
 * Classic Bluetooth HID was dropped because Samsung phones kept that link in sniff mode, a power
 * saving state that delayed reports by up to 700 ms. Low Energy has no sniff mode: the host polls
 * at a fixed connection interval and a report goes out at the next poll.
 *
 * - The host is reported as connected once notifications are on for the input report. Android
 *   does not keep that setting between connections, so it is saved per bonded host here: BlueZ
 *   writes it on every connect, but Windows reconnects a bonded gamepad without writing it again,
 *   and Gametap then sent nothing. It is restored 1 s after connecting, unless the host has
 *   started pairing again by then (see [KNOWN_HOST_WAIT_MS]); a bonded host with nothing saved
 *   counts as on.
 * - [stop] drops the link and stops advertising but keeps the services. The app no longer stops
 *   the gamepad (see GamepadSession); only a crash calls this, before exiting.
 *   Removing the services made Windows delete the gamepad device, even with the link already
 *   down: Windows still connects to a stopped phone every 10 to 30 s, found no HID service, and
 *   stopped connecting at all.
 * - The app also opens a GATT client back to the host on the same link and asks for high
 *   priority, which requests an 11.25 to 15 ms connection interval. Android gives a GATT server no
 *   way to ask for that itself. On Linux this halved the spread of report delays. It opens only
 *   on an encrypted link with no pairing under way: when the host turns notifications on, when
 *   pairing with it completes, or 1 s after a bonded host connects without pairing again (Windows
 *   reconnects without writing anything). Opened while a host was pairing, also a bonded host
 *   pairing again, Android's first read through it (the host's database hash) got no answer from
 *   BlueZ, and after 30 s Android dropped the link and stopped handing later connections to
 *   Gametap until Bluetooth was restarted.
 */
@SuppressLint("MissingPermission")
class AndroidHogpTransport(context: Context) : HidTransport {
    private val appContext = context.applicationContext
    private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)
    private val lock = Any()

    private val mutableState = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = mutableState.asStateFlow()

    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising = false
    private var advertisingSet: AdvertisingSet? = null
    private var advertisingSetStarting = false
    private var reportCharacteristic: BluetoothGattCharacteristic? = null

    // By characteristic, not UUID: the input and output reports share the Report UUID.
    private val staticValues = ArrayList<Pair<BluetoothGattCharacteristic, ByteArray>>()
    private val reportReferences = ArrayList<Pair<BluetoothGattCharacteristic, ByteArray>>()
    private val pendingServices = ArrayDeque<BluetoothGattService>()

    private var host: BluetoothDevice? = null
    private var subscribed = false
    private var priorityClient: BluetoothGatt? = null

    private var latestReport: ByteArray = HidReportEncoder.encode(GamepadState())

    // Android allows one notification in flight; onNotificationSent confirms each one.
    private val reports = LatestReportQueue(waitsForCompletion = true, trySend = ::notifyHost)

    // While the app is away the host stays connected, but nothing new is invited: no advertising.
    private var foreground = true

    // Between Stop and Start: the server and its services stay, but no host is kept or invited.
    private var stopped = false

    // Completed when the next link goes down, for stop.
    private var linkGone: CompletableDeferred<Unit>? = null

    // Notification setting per bonded host address, which a bond keeps stable.
    private val subscriptions = appContext.getSharedPreferences("hogp_subscriptions", Context.MODE_PRIVATE)

    // The connected host while a link is up, kept across the process, and the last host that
    // connected. A connected host still saved at startup means the process ended under it.
    private val hosts = appContext.getSharedPreferences("hogp_hosts", Context.MODE_PRIVATE)

    private val mutableForgottenHost = MutableStateFlow(hosts.getString(KEY_CONNECTED, null)?.let { ForgottenHost(it, lostServices = true) })
    override val forgottenHost: StateFlow<ForgottenHost?> = mutableForgottenHost.asStateFlow()

    private val mutableWifiClash = MutableStateFlow(false)
    override val wifiClash: StateFlow<Boolean> = mutableWifiClash.asStateFlow()

    override fun dismissWifiClash() {
        mutableWifiClash.value = false
    }

    /**
     * Whether the phone is connected to Wi-Fi on 2.4 GHz. Phones share one radio between Wi-Fi and
     * Bluetooth; on the Redmi Pad SE, 2.4 GHz Wi-Fi made the Windows link time out 4 to 9 times in
     * 10 minutes, and with Wi-Fi off or on 5 GHz it did not drop once in 10 minutes.
     */
    @Suppress("DEPRECATION")
    private fun onWifi24Ghz(): Boolean {
        val frequency = runCatching { wifiManager?.connectionInfo?.frequency }.getOrNull() ?: return false
        return frequency in 2400..2500
    }

    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)

    // Runs while advertising with no host: a paired host that does not come back may have forgotten us.
    private val reconnectCheck = Runnable {
        synchronized(lock) {
            if (host != null || !advertising || stopped) return@Runnable
            val address = hosts.getString(KEY_LAST_ADDRESS, null) ?: return@Runnable
            val bonded = manager?.adapter?.bondedDevices?.any { it.address == address } == true
            if (bonded && mutableForgottenHost.value == null) {
                mutableForgottenHost.value = ForgottenHost(hosts.getString(KEY_LAST_NAME, null) ?: address, lostServices = false)
            }
        }
    }

    override suspend fun start(descriptor: ByteArray) {
        synchronized(lock) {
            if (server != null) {
                if (!stopped) return
                stopped = false
                Log.d(TAG, "start, server kept")
                mutableState.value = TransportState.Advertising
                if (foreground) startAdvertisingLocked()
                return
            }
            stopped = false
            val adapter = manager?.adapter
            if (adapter != null && !adapter.isEnabled) {
                // Android has no advertiser while the radio is off, which would read as unsupported.
                mutableState.value = TransportState.Error(BLUETOOTH_OFF)
                return
            }
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
            reportReferences.clear()
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

    /**
     * Drops the host and stops advertising, but keeps the server and its services: closing the
     * server removed the HID service while the host was still connected, and Windows then deleted
     * the gamepad device.
     */
    /** Returns once the host's link is down, or after [EXIT_TIMEOUT_MS]. */
    override suspend fun stop() {
        // A second caller, such as Recents during a Stop, waits for the same link.
        val linkDown = synchronized(lock) {
            if (stopLocked() != null) CompletableDeferred<Unit>().also { linkGone = it } else linkGone
        } ?: return
        val dropped = withTimeoutOrNull(EXIT_TIMEOUT_MS) { linkDown.await() } != null
        Log.d(TAG, "stopped, link ${if (dropped) "down" else "still up after $EXIT_TIMEOUT_MS ms"}")
    }

    /** Returns the host being disconnected, if there was one. */
    private fun stopLocked(): BluetoothDevice? {
        if (server == null || stopped) return null
        stopped = true
        stopAdvertisingLocked()
        val leaving = host
        leaving?.let(::disconnectLocked)
        hosts.edit().remove(KEY_CONNECTED).commit() // Disconnected first, not under the host.
        host = null
        subscribed = false
        reports.reset()
        mutableState.value = TransportState.Idle
        return leaving
    }

    /**
     * [stop], blocking, for when the process is about to end: ending first would remove the
     * services while the host is still connected. Bluetooth callbacks arrive on a binder thread,
     * so this may block the main thread.
     */
    fun stopBeforeExit() = runBlocking { stop() }

    /**
     * cancelConnection only drops links the server opened itself, so open one on the same link
     * first. The priority client also holds the link, so it goes too.
     */
    private fun disconnectLocked(device: BluetoothDevice) {
        val gattServer = server ?: return
        closePriorityClientLocked()
        gattServer.connect(device, false)
        gattServer.cancelConnection(device)
        Log.d(TAG, "disconnecting ${device.address}")
    }

    /**
     * Drops a link a host opened while stopped. Cancelling straight after connect, as
     * [disconnectLocked] does, left such a link up for 5 s; 300 ms later it went in 2.3 to 2.5 s.
     * Windows still loads the gamepad 0.7 s after the link comes up, so Steam still shows it.
     */
    private fun rejectLocked(device: BluetoothDevice) {
        val gattServer = server ?: return
        gattServer.connect(device, false)
        Log.d(TAG, "rejecting ${device.address}, cancel in $REJECT_DELAY_MS ms")
        mainHandler.postDelayed({
            synchronized(lock) {
                if (stopped && server === gattServer) gattServer.cancelConnection(device)
            }
        }, REJECT_DELAY_MS)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Away, the host stays connected with every control released (the gamepad screen does that),
     * and the link asks for a slower, cheaper interval. Back in front, it asks for the fast one
     * again, or advertises if the host left meanwhile.
     */
    override fun setForeground(foreground: Boolean) {
        synchronized(lock) {
            if (this.foreground == foreground) return
            this.foreground = foreground
            if (server == null || stopped) return
            if (host == null) {
                if (foreground) startAdvertisingLocked() else stopAdvertisingLocked()
            } else {
                val priority = if (foreground) BluetoothGatt.CONNECTION_PRIORITY_HIGH else BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
                val requested = priorityClient?.requestConnectionPriority(priority)
                Log.d(TAG, "${if (foreground) "foreground" else "background"}, connection priority $priority requested: $requested")
            }
        }
    }

    /** Forgets the server and the host. The caller sets the state. */
    private fun releaseLocked() {
        server = null
        stopped = false
        advertiser = null
        advertising = false
        host = null
        subscribed = false
        reportCharacteristic = null
        reports.reset()
    }

    /**
     * Without this the app kept showing the last state, such as connected, after Bluetooth was
     * turned off. The stack drops the server and links itself; coming back on returns to Idle,
     * which restarts the gamepad if it was running.
     */
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> synchronized(lock) {
                    stopAdvertisingLocked()
                    dropAdvertisingSetLocked()
                    val gattServer = server ?: return
                    Log.d(TAG, "Bluetooth turning off")
                    // The services go with Bluetooth, under the host if one is connected.
                    host?.let { mutableForgottenHost.value = ForgottenHost(it.name ?: it.address, lostServices = true) }
                    hosts.edit().remove(KEY_CONNECTED).apply()
                    closePriorityClientLocked()
                    gattServer.close()
                    releaseLocked()
                    mutableState.value = TransportState.Error(BLUETOOTH_OFF)
                }
                BluetoothAdapter.STATE_ON -> synchronized(lock) {
                    val current = mutableState.value
                    if (server == null && current is TransportState.Error && current.message == BLUETOOTH_OFF) {
                        Log.d(TAG, "Bluetooth back on")
                        mutableState.value = TransportState.Idle
                    }
                }
            }
        }
    }

    /**
     * A host that is unpaired, or pairs again, forgets its notification setting, so a new pairing
     * starts clean. Pairing with the connected host also decides when the priority client opens.
     */
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_NONE -> subscriptions.edit().remove(device.address).apply()
                // A bonded host pairing again, for example after removing the phone on the PC.
                // The new pairing turns notifications on again itself.
                BluetoothDevice.BOND_BONDING -> {
                    subscriptions.edit().remove(device.address).apply()
                    synchronized(lock) { if (device == host) mainHandler.removeCallbacks(knownHostCheck) }
                }
                BluetoothDevice.BOND_BONDED -> synchronized(lock) {
                    if (device == host) openPriorityClientLocked(device)
                }
            }
        }
    }

    /**
     * For a bonded host that has not started pairing again: restores its notification setting
     * and opens the priority client. See [KNOWN_HOST_WAIT_MS].
     *
     * With nothing saved for the host, notifications count as on. That happens when Gametap's data
     * was cleared, for example by reinstalling, while the host stayed paired: Windows never writes
     * the setting again on reconnect, so it showed a connected controller that got no input until
     * it was paired again. A host that turned notifications off is still respected.
     */
    private val knownHostCheck = Runnable {
        synchronized(lock) {
            val device = host ?: return@Runnable
            if (stopped || device.bondState != BluetoothDevice.BOND_BONDED) return@Runnable
            val known = subscriptions.contains(device.address)
            val on = !known || subscriptions.getBoolean(device.address, false)
            Log.d(TAG, "notifications ${if (known) "saved as" else "not saved, taken as"} ${if (on) "on" else "off"} for ${device.address}")
            if (on && !subscribed) onSubscribedLocked(device)
            openPriorityClientLocked(device)
        }
    }

    // After the receivers: initializers run in declaration order.
    init {
        register(adapterReceiver, BluetoothAdapter.ACTION_STATE_CHANGED)
        register(bondReceiver, BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        // A crash would otherwise end the process with the host still connected.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { stopBeforeExit() }
            previous?.uncaughtException(thread, error)
        }
    }

    // System broadcasts still reach a receiver that is not exported.
    private fun register(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    /** Called under [lock] when [device] has turned notifications on, or arrives with them saved. */
    private fun onSubscribedLocked(device: BluetoothDevice) {
        hosts.edit()
            .putString(KEY_LAST_ADDRESS, device.address)
            .putString(KEY_LAST_NAME, device.name ?: device.address)
            .apply()
        mutableForgottenHost.value = null
        if (mutableWifiClash.value && !onWifi24Ghz()) mutableWifiClash.value = false
        subscribed = true
        reports.reset()
        mutableState.value = TransportState.Connected(device.name ?: device.address)
        reports.offer(latestReport) // Hosts can stall until a first report arrives.
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
        if (!subscribed || stopped) return false
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = gattServer.notifyCharacteristicChanged(device, characteristic, false, report)
            code == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.value = report
            gattServer.notifyCharacteristicChanged(device, characteristic, false)
        }
        if (accepted) {
            mainHandler.removeCallbacks(lostConfirmation)
            if (unconfirmed) {
                // Not in flight: the next report may go as soon as it is offered.
                mainHandler.post(markReady)
            } else {
                mainHandler.postDelayed(lostConfirmation, CONFIRMATION_WAIT_MS)
            }
        }
        return accepted
    }

    /**
     * Confirmations can stop for good. Android's GATT service holds back onNotificationSent while
     * a server is congested and releases it when congestion clears; when a link dropped while
     * congested, the clear never came, so Gametap's server stayed congested for the rest of the
     * process and no confirmation reached it again, across every later reconnect. [reports]
     * waited for one, so the gamepad showed connected but sent nothing. Seen three times on the
     * Redmi Pad SE with Windows, each congestion about 1 s before Windows dropped the link.
     *
     * After [MISSED_CONFIRMATIONS] misses in a row Gametap stops waiting; [ReportSender] already
     * spaces reports, and in that mode about 57 reports a second went out with none refused. A
     * confirmation that does arrive turns waiting back on.
     */
    private var unconfirmed = false
    private var missedConfirmations = 0

    private val lostConfirmation = Runnable {
        synchronized(lock) {
            missedConfirmations++
            if (!unconfirmed && missedConfirmations >= MISSED_CONFIRMATIONS) {
                unconfirmed = true
                Log.w(TAG, "notifications are not being confirmed; sending without waiting")
            }
            reports.onReady()
        }
    }

    private val markReady = Runnable { synchronized(lock) { reports.onReady() } }

    private fun toGatt(spec: HogpCharacteristic): BluetoothGattCharacteristic {
        val encrypted = spec.access == HogpAccess.Encrypted
        var properties = 0
        var permissions = 0
        if (spec.readable) {
            properties = properties or BluetoothGattCharacteristic.PROPERTY_READ
            permissions = permissions or
                if (encrypted) BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED else BluetoothGattCharacteristic.PERMISSION_READ
        }
        if (spec.writableWithoutResponse || spec.writable) {
            if (spec.writableWithoutResponse) properties = properties or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            if (spec.writable) properties = properties or BluetoothGattCharacteristic.PROPERTY_WRITE
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
        spec.reportReference?.let {
            characteristic.addDescriptor(BluetoothGattDescriptor(uuid(HogpProfile.REPORT_REFERENCE), descriptorRead))
            reportReferences += characteristic to it
        }
        spec.value?.let { staticValues += characteristic to it }
        if (spec.uuid == HogpProfile.REPORT && spec.notifies) reportCharacteristic = characteristic
        return characteristic
    }

    /**
     * The entry for [characteristic]. Android hands back the objects given to addService, so
     * identity finds it; UUID and properties are the fallback, and they also tell the input report
     * (notify) from the output report (write) apart.
     */
    private fun <T> List<Pair<BluetoothGattCharacteristic, T>>.valueFor(characteristic: BluetoothGattCharacteristic): T? =
        (firstOrNull { it.first === characteristic }
            ?: firstOrNull { it.first.uuid == characteristic.uuid && it.first.properties == characteristic.properties })?.second

    private fun isInputReport(characteristic: BluetoothGattCharacteristic): Boolean {
        val input = reportCharacteristic ?: return false
        return characteristic === input || (characteristic.uuid == input.uuid && characteristic.properties == input.properties)
    }

    /**
     * One advertising set, created on the first advertisement and then only switched on and off,
     * also across Stop and Start, until Bluetooth goes off. Windows reconnects to the last address
     * it saw, and a new set gets a new private address; a kept set may keep its address until
     * Android rotates it. Apps cannot choose the address itself.
     */
    private fun startAdvertisingLocked() {
        if (advertising) return
        advertising = true
        mainHandler.removeCallbacks(reconnectCheck)
        mainHandler.postDelayed(reconnectCheck, RECONNECT_WAIT_MS)
        advertisingSet?.let {
            it.enableAdvertising(true, 0, 0)
            return
        }
        if (advertisingSetStarting) return
        val leAdvertiser = advertiser ?: run {
            advertising = false
            return
        }
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(true)
            .setScannable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(uuid(HogpProfile.HID_SERVICE)))
            .build()
        // The phone's own name, since Android has no per-app advertised name.
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        advertisingSetStarting = true
        leAdvertiser.startAdvertisingSet(parameters, data, scanResponse, null, null, advertisingSetCallback)
    }

    private fun stopAdvertisingLocked() {
        mainHandler.removeCallbacks(reconnectCheck)
        if (!advertising) return
        advertising = false
        // Bluetooth may already be going off here, and the call may throw then; the stack drops
        // the advertisement itself in that case.
        runCatching { advertisingSet?.enableAdvertising(false, 0, 0) }
    }

    /** Only when Bluetooth goes off: the set does not survive it. */
    private fun dropAdvertisingSetLocked() {
        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(advertisingSetCallback) }
        advertisingSet = null
        advertisingSetStarting = false
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

    private val advertisingSetCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            Log.d(TAG, "advertising set started, status $status")
            synchronized(lock) {
                advertisingSetStarting = false
                if (status != ADVERTISE_SUCCESS || advertisingSet == null) {
                    advertising = false
                    mutableState.value = TransportState.Error("Could not advertise (error $status)")
                    return
                }
                this@AndroidHogpTransport.advertisingSet = advertisingSet
                // Stopped while the set was being created: keep the set, switched off.
                if (!advertising) advertisingSet.enableAdvertising(false, 0, 0)
                else if (host == null && server != null) mutableState.value = TransportState.Advertising
            }
        }

        override fun onAdvertisingEnabled(advertisingSet: AdvertisingSet, enable: Boolean, status: Int) {
            Log.d(TAG, "advertising ${if (enable) "on" else "off"}, status $status")
            synchronized(lock) {
                if (!enable) return
                if (status != ADVERTISE_SUCCESS) {
                    advertising = false
                    mutableState.value = TransportState.Error("Could not advertise (error $status)")
                } else if (host == null && server != null) {
                    mutableState.value = TransportState.Advertising
                }
            }
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
            synchronized(lock) {
                if (this@AndroidHogpTransport.advertisingSet === advertisingSet) this@AndroidHogpTransport.advertisingSet = null
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
                if (next != null) gattServer.addService(next) else if (foreground) startAdvertisingLocked()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "connection ${device.address} status $status state $newState")
            synchronized(lock) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    linkGone?.complete(Unit)
                    linkGone = null
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (server == null) return
                        if (stopped) {
                            // A bonded host can still reach the phone; Stop means no host.
                            rejectLocked(device)
                            return
                        }
                        if (host != null && host != device) return // One host at a time.
                        host = device
                        // commit, not apply: the process may end any moment from here on.
                        hosts.edit().putString(KEY_CONNECTED, device.name ?: device.address).commit()
                        subscribed = false
                        reports.reset()
                        stopAdvertisingLocked()
                        mainHandler.removeCallbacks(knownHostCheck)
                        mainHandler.postDelayed(knownHostCheck, KNOWN_HOST_WAIT_MS)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> if (device == host) {
                        if (subscribed && !stopped && onWifi24Ghz()) {
                            Log.d(TAG, "link dropped while on 2.4 GHz Wi-Fi")
                            mutableWifiClash.value = true
                        }
                        hosts.edit().remove(KEY_CONNECTED).apply()
                        host = null
                        subscribed = false
                        reports.reset()
                        mainHandler.removeCallbacks(knownHostCheck)
                        closePriorityClientLocked()
                        if (server != null && !stopped) {
                            mutableState.value = TransportState.Advertising
                            // Away, wait for the app to come back before inviting a host.
                            if (foreground) startAdvertisingLocked()
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
                if (isInputReport(characteristic)) latestReport else staticValues.valueFor(characteristic)
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
                uuid(HogpProfile.REPORT_REFERENCE) -> synchronized(lock) { reportReferences.valueFor(descriptor.characteristic) }
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
                    if (device == host && !stopped) {
                        if (device.bondState == BluetoothDevice.BOND_BONDED) {
                            subscriptions.edit().putBoolean(device.address, enabled).apply()
                        }
                        if (enabled) {
                            onSubscribedLocked(device)
                            // Android accepts this write only on an encrypted link.
                            openPriorityClientLocked(device)
                        } else {
                            subscribed = false
                            reports.reset()
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
            mainHandler.removeCallbacks(lostConfirmation)
            synchronized(lock) {
                missedConfirmations = 0
                if (unconfirmed) {
                    unconfirmed = false
                    Log.d(TAG, "notifications are confirmed again; waiting for each")
                }
                reports.onReady()
            }
        }
    }

    private companion object {
        const val TAG = "GametapHogp"
        const val BLUETOOTH_OFF = "Bluetooth is off"

        // The link went down 1.1 s after Disconnect on the Redmi Pad SE.
        const val EXIT_TIMEOUT_MS = 2_000L

        const val REJECT_DELAY_MS = 300L

        // Windows reconnected 0.2 to 38 s after advertising began, or after its radio came back.
        const val RECONNECT_WAIT_MS = 60_000L

        // The stack confirmed within 1 ms of each send, also at the slow background interval.
        const val CONFIRMATION_WAIT_MS = 100L
        const val MISSED_CONFIRMATIONS = 3

        /**
         * How long a bonded host has to start pairing again before it is treated as known. A host
         * that pairs again started 0.13 to 0.23 s after connecting (four times, BlueZ). Before
         * that, nothing may go out: input reports sent from 0.08 s after connecting, restored
         * from the old bond, made BlueZ start its GATT client unencrypted, mid-pairing; its HID
         * reads were refused and it never created the gamepad.
         */
        const val KNOWN_HOST_WAIT_MS = 1_000L

        const val KEY_CONNECTED = "connected"
        const val KEY_LAST_ADDRESS = "last_address"
        const val KEY_LAST_NAME = "last_name"

        fun uuid(short: Int): UUID = UUID.fromString(HogpProfile.uuidString(short))
    }
}
