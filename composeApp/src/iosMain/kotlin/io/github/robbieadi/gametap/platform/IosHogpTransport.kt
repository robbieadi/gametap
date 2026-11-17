@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.robbieadi.gametap.platform

import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.hid.HidReportEncoder
import io.github.robbieadi.gametap.hogp.HogpAccess
import io.github.robbieadi.gametap.hogp.HogpCharacteristic
import io.github.robbieadi.gametap.hogp.HogpProfile
import io.github.robbieadi.gametap.hogp.HogpRead
import io.github.robbieadi.gametap.hogp.LatestReportQueue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsReadEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyNotifyEncryptionRequired
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableDescriptor
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.dataWithBytes
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Publishes [HogpProfile] from a CoreBluetooth peripheral. All CoreBluetooth calls run on the main
 * queue.
 *
 * - Hosts can stall after subscribing until an input report arrives, so one is sent at once.
 * - Advertising only carries a name and service UUIDs, so the gamepad appearance cannot be sent.
 * - iOS gives no host name, so the connected state shows a short central identifier.
 */
class IosHogpTransport : HidTransport {
    // CBPeripheralManager holds its delegate weakly, so this strong reference keeps it alive.
    private val peripheral = HogpPeripheral()

    override val state: StateFlow<TransportState> = peripheral.state

    override suspend fun start(descriptor: ByteArray) = peripheral.start(descriptor)

    override suspend fun stop() = peripheral.stop()

    override fun sendReport(reportId: Int, data: ByteArray): Boolean = peripheral.send(data)
}

/** Separate class because a Kotlin class cannot extend NSObject and a Kotlin interface together. */
private class HogpPeripheral : NSObject(), CBPeripheralManagerDelegateProtocol {
    private val mutableState = MutableStateFlow<TransportState>(TransportState.Idle)
    val state: StateFlow<TransportState> = mutableState.asStateFlow()

    private var manager: CBPeripheralManager? = null
    private var reportCharacteristic: CBMutableCharacteristic? = null
    private var reportMap: ByteArray = ByteArray(0)
    private val staticValues = HashMap<String, ByteArray>()
    private val pendingServices = HashSet<String>()

    private var latestReport: ByteArray = HidReportEncoder.encode(GamepadState())

    // updateValue refuses a report when iOS's queue is full and calls
    // peripheralManagerIsReadyToUpdateSubscribers once there is room; accepted reports need no
    // confirmation.
    private val reports = LatestReportQueue(waitsForCompletion = false) { report ->
        val peripheral = manager
        val characteristic = reportCharacteristic
        peripheral != null && characteristic != null && peripheral.updateValue(report.toNSData(), characteristic, null)
    }

    fun start(descriptor: ByteArray) {
        onMain {
            if (manager != null && mutableState.value !is TransportState.Error) return@onMain
            tearDown()
            reportMap = descriptor
            // The delegate's state callback builds the GATT tree once Bluetooth is on.
            manager = CBPeripheralManager(delegate = this, queue = null)
        }
    }

    fun stop() {
        onMain {
            tearDown()
            mutableState.value = TransportState.Idle
        }
    }

    private fun tearDown() {
        manager?.stopAdvertising()
        manager?.removeAllServices()
        manager?.delegate = null
        manager = null
        reportCharacteristic = null
        reports.reset()
        pendingServices.clear()
    }

    fun send(data: ByteArray): Boolean {
        onMain {
            latestReport = data
            reports.offer(data)
        }
        return true
    }

    private fun publishServices(peripheral: CBPeripheralManager) {
        if (reportCharacteristic != null) return
        staticValues.clear()
        val services = HogpProfile.services(reportMap).map { service ->
            CBMutableService(cbUuid(service.uuid), true).apply {
                setCharacteristics(service.characteristics.map(::toCoreBluetooth))
            }
        }
        pendingServices.clear()
        services.forEach { pendingServices.add(it.UUID.UUIDString) }
        services.forEach(peripheral::addService)
    }

    /** Values are served from read requests, since iOS restricts characteristics with cached values. */
    private fun toCoreBluetooth(spec: HogpCharacteristic): CBMutableCharacteristic {
        val encrypted = spec.access == HogpAccess.Encrypted
        var properties = 0uL
        var permissions = 0uL
        if (spec.readable) {
            properties = properties or CBCharacteristicPropertyRead
            permissions = permissions or
                if (encrypted) CBAttributePermissionsReadEncryptionRequired else CBAttributePermissionsReadable
        }
        if (spec.writableWithoutResponse) {
            properties = properties or CBCharacteristicPropertyWriteWithoutResponse
            permissions = permissions or
                if (encrypted) CBAttributePermissionsWriteEncryptionRequired else CBAttributePermissionsWriteable
        }
        if (spec.notifies) {
            // iOS adds the Client Characteristic Configuration descriptor itself.
            properties = properties or
                if (encrypted) CBCharacteristicPropertyNotifyEncryptionRequired else CBCharacteristicPropertyNotify
        }
        val characteristic = CBMutableCharacteristic(cbUuid(spec.uuid), properties, null, permissions)
        spec.reportReference?.let {
            characteristic.setDescriptors(listOf(CBMutableDescriptor(cbUuid(HogpProfile.REPORT_REFERENCE), it.toNSData())))
        }
        spec.value?.let { staticValues[characteristic.UUID.UUIDString] = it }
        if (spec.uuid == HogpProfile.REPORT) reportCharacteristic = characteristic
        return characteristic
    }

    private fun startAdvertising(peripheral: CBPeripheralManager) {
        peripheral.startAdvertising(
            mapOf<Any?, Any?>(
                CBAdvertisementDataLocalNameKey to HogpProfile.NAME,
                CBAdvertisementDataServiceUUIDsKey to listOf(cbUuid(HogpProfile.HID_SERVICE)),
            ),
        )
    }

    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        when (peripheral.state) {
            CBManagerStatePoweredOn -> publishServices(peripheral)
            CBManagerStatePoweredOff -> {
                // Published services do not survive a power cycle, so rebuild them when it returns.
                peripheral.removeAllServices()
                reportCharacteristic = null
                reports.reset()
                mutableState.value = TransportState.Error("Bluetooth is off")
            }
            CBManagerStateUnauthorized -> mutableState.value = TransportState.Error("Bluetooth access is not allowed")
            CBManagerStateUnsupported -> mutableState.value = TransportState.Unsupported
            else -> Unit
        }
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        val uuid = didAddService.UUID.UUIDString
        pendingServices.remove(uuid)
        if (error != null && uuid == cbUuid(HogpProfile.HID_SERVICE).UUIDString) {
            pendingServices.clear()
            mutableState.value = TransportState.Error("iOS refused the HID service: ${error.localizedDescription}")
            return
        }
        // Device Information and Battery are optional: iOS may reserve them, and hosts still work.
        if (pendingServices.isEmpty()) startAdvertising(peripheral)
    }

    override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
        mutableState.value = if (error == null) {
            TransportState.Advertising
        } else {
            TransportState.Error("Could not advertise: ${error.localizedDescription}")
        }
    }

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeToCharacteristic: CBCharacteristic,
    ) {
        mutableState.value = TransportState.Connected("host ${central.identifier.UUIDString.take(8)}")
        reports.reset()
        reports.offer(latestReport)
    }

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        central: CBCentral,
        didUnsubscribeFromCharacteristic: CBCharacteristic,
    ) {
        mutableState.value = TransportState.Advertising
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
        val request = didReceiveReadRequest
        val uuid = request.characteristic.UUID.UUIDString
        val value = if (uuid == reportCharacteristic?.UUID?.UUIDString) latestReport else staticValues[uuid]
        when (val read = HogpProfile.readFrom(value, request.offset.toInt())) {
            is HogpRead.Value -> {
                request.value = read.bytes.toNSData()
                peripheral.respondToRequest(request, CBATTErrorSuccess)
            }
            HogpRead.InvalidOffset -> peripheral.respondToRequest(request, CBATTErrorInvalidOffset)
            HogpRead.NotReadable -> peripheral.respondToRequest(request, CBATTErrorReadNotPermitted)
        }
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
        // Control Point and Protocol Mode have no effect here: this device has no sleep or boot mode.
        (didReceiveWriteRequests.firstOrNull() as? CBATTRequest)?.let {
            peripheral.respondToRequest(it, CBATTErrorSuccess)
        }
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
        reports.onReady()
    }
}

private fun cbUuid(short: Int): CBUUID = CBUUID.UUIDWithString(HogpProfile.uuidString(short))

private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData() // addressOf(0) needs at least one byte.
    return usePinned { NSData.dataWithBytes(it.addressOf(0), size.convert()) }
}
