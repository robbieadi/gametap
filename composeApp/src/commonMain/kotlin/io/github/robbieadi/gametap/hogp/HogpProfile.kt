package io.github.robbieadi.gametap.hogp

import io.github.robbieadi.gametap.hid.HidDescriptor

/** Whether a host must be paired, so the link is encrypted, before it can use an attribute. */
enum class HogpAccess { Open, Encrypted }

/**
 * One GATT characteristic. [access] applies to every operation it offers: read, notify and write.
 * The Client Characteristic Configuration descriptor that [notifies] needs is left to each
 * platform, because iOS adds it itself and Android needs it added by hand.
 */
class HogpCharacteristic(
    val uuid: Int,
    val access: HogpAccess,
    val readable: Boolean = false,
    val notifies: Boolean = false,
    val writableWithoutResponse: Boolean = false,
    /** Accepts writes that the host waits to have acknowledged. */
    val writable: Boolean = false,
    /** Served on reads. Null for the input report, whose value is the latest report. */
    val value: ByteArray? = null,
    /** Value of the Report Reference descriptor, for report characteristics only. */
    val reportReference: ByteArray? = null,
)

class HogpService(val uuid: Int, val characteristics: List<HogpCharacteristic>)

/** The outcome of a host reading an attribute, possibly from an offset. */
sealed interface HogpRead {
    class Value(val bytes: ByteArray) : HogpRead
    data object InvalidOffset : HogpRead
    data object NotReadable : HogpRead
}

/**
 * The HID over GATT layout both platforms publish: the HID service, Device Information and
 * Battery. Platform transports only turn this into their own GATT objects.
 */
object HogpProfile {
    const val HID_SERVICE = 0x1812
    const val DEVICE_INFO_SERVICE = 0x180A
    const val BATTERY_SERVICE = 0x180F

    const val HID_INFORMATION = 0x2A4A
    const val REPORT_MAP = 0x2A4B
    const val HID_CONTROL_POINT = 0x2A4C
    const val REPORT = 0x2A4D
    const val PROTOCOL_MODE = 0x2A4E
    const val PNP_ID = 0x2A50
    const val MANUFACTURER_NAME = 0x2A29
    const val BATTERY_LEVEL = 0x2A19

    const val CLIENT_CONFIG = 0x2902
    const val REPORT_REFERENCE = 0x2908

    /** Used as the manufacturer name and, on iOS, the advertised name. */
    const val NAME = "Gametap"

    /** bcdHID 0x0111, country 0, flags 0x02 (normally connectable). */
    val hidInformation: ByteArray = bytes(0x11, 0x01, 0x00, 0x02)

    /**
     * USB vendor ID source, then the Xbox Wireless Controller's IDs: Microsoft 045E, model 1914
     * 0B13, version 0x0509. Windows binds its XInput driver (xinputhid) to these IDs, and SDL,
     * Linux and macOS pick their Xbox drivers by them. Changing them makes hosts treat Gametap as a
     * generic HID gamepad again.
     */
    val pnpId: ByteArray = bytes(0x02, 0x5E, 0x04, 0x13, 0x0B, 0x09, 0x05)

    /** Report ID, then report type 0x01 for input. */
    val inputReportReference: ByteArray = bytes(HidDescriptor.REPORT_ID, 0x01)

    /** Report ID, then report type 0x02 for output. */
    val outputReportReference: ByteArray = bytes(HidDescriptor.OUTPUT_REPORT_ID, 0x02)

    /** Report protocol, not boot protocol. */
    private val reportProtocol: ByteArray = bytes(0x01)

    private val fullBatteryLevel: ByteArray = bytes(100)

    /** HID first: hosts and iOS treat it as the one service that must be published. */
    fun services(reportMap: ByteArray): List<HogpService> = listOf(
        HogpService(
            HID_SERVICE,
            listOf(
                HogpCharacteristic(HID_INFORMATION, HogpAccess.Open, readable = true, value = hidInformation),
                HogpCharacteristic(REPORT_MAP, HogpAccess.Encrypted, readable = true, value = reportMap),
                HogpCharacteristic(
                    REPORT,
                    HogpAccess.Encrypted,
                    readable = true,
                    notifies = true,
                    reportReference = inputReportReference,
                ),
                // Rumble. The Report Map declares it, so it gets a characteristic like the real
                // controller's; what hosts write is acknowledged and ignored.
                HogpCharacteristic(
                    REPORT,
                    HogpAccess.Encrypted,
                    readable = true,
                    writable = true,
                    writableWithoutResponse = true,
                    value = ByteArray(HidDescriptor.OUTPUT_REPORT_SIZE),
                    reportReference = outputReportReference,
                ),
                HogpCharacteristic(HID_CONTROL_POINT, HogpAccess.Encrypted, writableWithoutResponse = true),
                HogpCharacteristic(
                    PROTOCOL_MODE,
                    HogpAccess.Encrypted,
                    readable = true,
                    writableWithoutResponse = true,
                    value = reportProtocol,
                ),
            ),
        ),
        HogpService(
            DEVICE_INFO_SERVICE,
            listOf(
                HogpCharacteristic(PNP_ID, HogpAccess.Open, readable = true, value = pnpId),
                HogpCharacteristic(MANUFACTURER_NAME, HogpAccess.Open, readable = true, value = NAME.encodeToByteArray()),
            ),
        ),
        HogpService(
            BATTERY_SERVICE,
            listOf(HogpCharacteristic(BATTERY_LEVEL, HogpAccess.Open, readable = true, value = fullBatteryLevel)),
        ),
    )

    /**
     * The full 128-bit form of a Bluetooth SIG 16-bit UUID. iOS is reported to reject the 16-bit
     * form of reserved services, so both platforms use this one.
     */
    fun uuidString(short: Int): String =
        "0000${short.toString(16).uppercase().padStart(4, '0')}-0000-1000-8000-00805F9B34FB"

    /** Long values such as the Report Map are read in chunks; each read asks from [offset] on. */
    fun readFrom(value: ByteArray?, offset: Int): HogpRead = when {
        value == null -> HogpRead.NotReadable
        offset < 0 || offset > value.size -> HogpRead.InvalidOffset
        else -> HogpRead.Value(value.copyOfRange(offset, value.size))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
