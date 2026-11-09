@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.robbieadi.gametap.platform

import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.hid.HidDescriptor
import io.github.robbieadi.gametap.hid.HidReportEncoder
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
import platform.Foundation.NSMakeRange
import platform.Foundation.dataWithBytes
import platform.Foundation.subdataWithRange
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * HID over GATT from a CoreBluetooth peripheral. All CoreBluetooth calls run on the main queue.
 *
 * Platform constraints this follows (not yet confirmed on hardware, see the README):
 * - SIG UUIDs use the full 128-bit form; iOS is reported to reject the 16-bit form as reserved.
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
    private var reportMap: NSData? = null
    private val staticValues = HashMap<String, NSData>()
    private val pendingServices = HashSet<String>()

    private var latestReport: ByteArray = HidReportEncoder.encode(GamepadState())
    private var reportPending = false

    fun start(descriptor: ByteArray) {
        onMain {
            if (manager != null && mutableState.value !is TransportState.Error) return@onMain
            tearDown()
            reportMap = descriptor.toNSData()
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
        reportPending = false
        pendingServices.clear()
    }

    /** Queues the report. Only the newest report is kept while the radio is backed up. */
    fun send(data: ByteArray): Boolean {
        onMain {
            latestReport = data
            flushReport()
        }
        return true
    }

    private fun flushReport() {
        val peripheral = manager ?: return
        val characteristic = reportCharacteristic ?: return
        val sent = peripheral.updateValue(latestReport.toNSData(), characteristic, null)
        reportPending = !sent
    }

    private fun publishServices(peripheral: CBPeripheralManager) {
        if (reportCharacteristic != null) return
        val hidService = buildHidService()
        val services = listOf(hidService, buildDeviceInfoService(), buildBatteryService())
        pendingServices.clear()
        services.forEach { pendingServices.add(it.UUID.UUIDString) }
        services.forEach(peripheral::addService)
    }

    private fun buildHidService(): CBMutableService {
        val hidInformation = staticCharacteristic(
            HID_INFORMATION,
            bytes(0x11, 0x01, 0x00, 0x02), // bcdHID 0x0111, country 0, flags 0x02
            CBAttributePermissionsReadable,
        )
        val map = CBMutableCharacteristic(
            uUID(REPORT_MAP),
            CBCharacteristicPropertyRead,
            null,
            CBAttributePermissionsReadEncryptionRequired,
        )
        reportMap?.let { staticValues[map.UUID.UUIDString] = it }

        val report = CBMutableCharacteristic(
            uUID(REPORT),
            CBCharacteristicPropertyRead or CBCharacteristicPropertyNotifyEncryptionRequired,
            null,
            CBAttributePermissionsReadEncryptionRequired,
        )
        report.setDescriptors(
            listOf(
                CBMutableDescriptor(uUID(REPORT_REFERENCE), bytes(HidDescriptor.REPORT_ID, REPORT_TYPE_INPUT).toNSData()),
            ),
        )
        reportCharacteristic = report

        val controlPoint = CBMutableCharacteristic(
            uUID(HID_CONTROL_POINT),
            CBCharacteristicPropertyWriteWithoutResponse,
            null,
            CBAttributePermissionsWriteable or CBAttributePermissionsWriteEncryptionRequired,
        )
        val protocolMode = CBMutableCharacteristic(
            uUID(PROTOCOL_MODE),
            CBCharacteristicPropertyRead or CBCharacteristicPropertyWriteWithoutResponse,
            null,
            CBAttributePermissionsReadEncryptionRequired or CBAttributePermissionsWriteEncryptionRequired,
        )
        staticValues[protocolMode.UUID.UUIDString] = bytes(PROTOCOL_MODE_REPORT).toNSData()

        return CBMutableService(uUID(HID_SERVICE), true).apply {
            setCharacteristics(listOf(hidInformation, map, report, controlPoint, protocolMode))
        }
    }

    private fun buildDeviceInfoService(): CBMutableService {
        val pnpId = staticCharacteristic(
            PNP_ID,
            // USB vendor source, pid.codes test IDs, version 1.0. Replace before release.
            bytes(0x02, 0x09, 0x12, 0x01, 0x00, 0x00, 0x01),
            CBAttributePermissionsReadable,
        )
        val manufacturer = staticCharacteristic(
            MANUFACTURER_NAME,
            "Gametap".encodeToByteArray(),
            CBAttributePermissionsReadable,
        )
        return CBMutableService(uUID(DEVICE_INFO_SERVICE), true).apply {
            setCharacteristics(listOf(pnpId, manufacturer))
        }
    }

    private fun buildBatteryService(): CBMutableService {
        val level = staticCharacteristic(BATTERY_LEVEL, bytes(100), CBAttributePermissionsReadable)
        return CBMutableService(uUID(BATTERY_SERVICE), true).apply {
            setCharacteristics(listOf(level))
        }
    }

    /** Values are served from read requests, since iOS restricts characteristics with cached values. */
    private fun staticCharacteristic(uuid: String, value: ByteArray, permissions: ULong): CBMutableCharacteristic {
        val characteristic = CBMutableCharacteristic(uUID(uuid), CBCharacteristicPropertyRead, null, permissions)
        staticValues[characteristic.UUID.UUIDString] = value.toNSData()
        return characteristic
    }

    private fun startAdvertising(peripheral: CBPeripheralManager) {
        peripheral.startAdvertising(
            mapOf<Any?, Any?>(
                CBAdvertisementDataLocalNameKey to "Gametap",
                CBAdvertisementDataServiceUUIDsKey to listOf(uUID(HID_SERVICE)),
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
        if (error != null && uuid == uUID(HID_SERVICE).UUIDString) {
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
        flushReport()
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
        val value = if (uuid == reportCharacteristic?.UUID?.UUIDString) {
            latestReport.toNSData()
        } else {
            staticValues[uuid]
        }
        val offset = request.offset
        if (value == null || offset > value.length) {
            peripheral.respondToRequest(request, CBATTErrorInvalidOffset)
            return
        }
        // Report Map is longer than one ATT packet, so hosts read it in offset chunks.
        request.value = value.subdataWithRange(NSMakeRange(offset, value.length - offset))
        peripheral.respondToRequest(request, CBATTErrorSuccess)
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
        // Control Point and Protocol Mode have no effect here: this device has no sleep or boot mode.
        (didReceiveWriteRequests.firstOrNull() as? CBATTRequest)?.let {
            peripheral.respondToRequest(it, CBATTErrorSuccess)
        }
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
        if (reportPending) flushReport()
    }
}

// File level, because a subclass of an Objective-C type cannot have companion fields.
private const val HID_SERVICE = "1812"
private const val DEVICE_INFO_SERVICE = "180A"
private const val BATTERY_SERVICE = "180F"
private const val HID_INFORMATION = "2A4A"
private const val REPORT_MAP = "2A4B"
private const val HID_CONTROL_POINT = "2A4C"
private const val REPORT = "2A4D"
private const val PROTOCOL_MODE = "2A4E"
private const val PNP_ID = "2A50"
private const val MANUFACTURER_NAME = "2A29"
private const val BATTERY_LEVEL = "2A19"
private const val REPORT_REFERENCE = "2908"
private const val REPORT_TYPE_INPUT = 0x01
private const val PROTOCOL_MODE_REPORT = 0x01

private fun uUID(short: String): CBUUID = CBUUID.UUIDWithString("0000$short-0000-1000-8000-00805F9B34FB")

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.dataWithBytes(it.addressOf(0), size.convert())
}
