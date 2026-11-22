package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.hid.HidDescriptor
import io.github.robbieadi.gametap.hogp.HogpAccess
import io.github.robbieadi.gametap.hogp.HogpProfile
import io.github.robbieadi.gametap.hogp.HogpRead
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HogpProfileTest {
    private val reportMap = HidDescriptor.bytes
    private val services = HogpProfile.services(reportMap)
    private val hid = services.first()

    private fun characteristic(uuid: Int) = services.flatMap { it.characteristics }.single { it.uuid == uuid }
    private val reports = hid.characteristics.filter { it.uuid == HogpProfile.REPORT }
    private val inputReport = reports.single { it.notifies }
    private val outputReport = reports.single { !it.notifies }

    @Test
    fun hidServiceComesFirstThenDeviceInfoAndBattery() {
        assertEquals(
            listOf(HogpProfile.HID_SERVICE, HogpProfile.DEVICE_INFO_SERVICE, HogpProfile.BATTERY_SERVICE),
            services.map { it.uuid },
        )
        assertEquals(
            listOf(
                HogpProfile.HID_INFORMATION,
                HogpProfile.REPORT_MAP,
                HogpProfile.REPORT,
                HogpProfile.REPORT,
                HogpProfile.HID_CONTROL_POINT,
                HogpProfile.PROTOCOL_MODE,
            ),
            hid.characteristics.map { it.uuid },
        )
    }

    @Test
    fun reportMapIsTheDescriptorAndNeedsPairing() {
        val map = characteristic(HogpProfile.REPORT_MAP)
        assertContentEquals(reportMap, map.value)
        assertEquals(HogpAccess.Encrypted, map.access)
    }

    @Test
    fun inputReportNotifiesWithAReportReferenceAndHasNoFixedValue() {
        val report = inputReport
        assertTrue(report.readable)
        assertEquals(HogpAccess.Encrypted, report.access)
        assertNull(report.value)
        assertContentEquals(byteArrayOf(HidDescriptor.REPORT_ID.toByte(), 0x01), report.reportReference)
    }

    @Test
    fun outputReportTakesWritesAndReadsBackZeroes() {
        val report = outputReport
        assertTrue(report.writable)
        assertTrue(report.writableWithoutResponse)
        assertTrue(report.readable)
        assertEquals(HogpAccess.Encrypted, report.access)
        assertContentEquals(ByteArray(HidDescriptor.OUTPUT_REPORT_SIZE), report.value)
        assertContentEquals(byteArrayOf(HidDescriptor.OUTPUT_REPORT_ID.toByte(), 0x02), report.reportReference)
    }

    @Test
    fun onlyTheReportsHaveAReportReference() {
        val withReference = services.flatMap { it.characteristics }.filter { it.reportReference != null }
        assertEquals(listOf(HogpProfile.REPORT, HogpProfile.REPORT), withReference.map { it.uuid })
    }

    @Test
    fun fixedValues() {
        assertContentEquals(byteArrayOf(0x11, 0x01, 0x00, 0x02), characteristic(HogpProfile.HID_INFORMATION).value)
        // USB source, Microsoft 045E, Xbox Wireless Controller 0B13, version 0x0509: the IDs xinputhid binds to.
        assertContentEquals(byteArrayOf(0x02, 0x5E, 0x04, 0x13, 0x0B, 0x09, 0x05), characteristic(HogpProfile.PNP_ID).value)
        assertContentEquals("Gametap".encodeToByteArray(), characteristic(HogpProfile.MANUFACTURER_NAME).value)
        assertContentEquals(byteArrayOf(100), characteristic(HogpProfile.BATTERY_LEVEL).value)
        assertContentEquals(byteArrayOf(0x01), characteristic(HogpProfile.PROTOCOL_MODE).value)
    }

    @Test
    fun controlPointIsWriteOnly() {
        val controlPoint = characteristic(HogpProfile.HID_CONTROL_POINT)
        assertTrue(controlPoint.writableWithoutResponse)
        assertEquals(false, controlPoint.readable)
        assertNull(controlPoint.value)
    }

    @Test
    fun everyReadableCharacteristicExceptTheInputReportHasAValue() {
        services.flatMap { it.characteristics }
            .filter { it.readable && it !== inputReport }
            .forEach { assertNotNull(it.value, "0x${it.uuid.toString(16)}") }
    }

    @Test
    fun uuidStringIsTheFullSigForm() {
        assertEquals("00001812-0000-1000-8000-00805F9B34FB", HogpProfile.uuidString(0x1812))
        assertEquals("00002A4D-0000-1000-8000-00805F9B34FB", HogpProfile.uuidString(0x2A4D))
        assertEquals("00002908-0000-1000-8000-00805F9B34FB", HogpProfile.uuidString(0x2908))
    }

    @Test
    fun readFromReturnsTheRestOfTheValue() {
        val value = byteArrayOf(1, 2, 3, 4, 5)
        assertContentEquals(value, assertIs<HogpRead.Value>(HogpProfile.readFrom(value, 0)).bytes)
        assertContentEquals(byteArrayOf(4, 5), assertIs<HogpRead.Value>(HogpProfile.readFrom(value, 3)).bytes)
    }

    @Test
    fun readingAtTheEndGivesAnEmptyValue() {
        // Hosts finish a long read this way when the value is an exact multiple of the chunk size.
        val result = HogpProfile.readFrom(byteArrayOf(1, 2, 3), 3)
        assertEquals(0, assertIs<HogpRead.Value>(result).bytes.size)
    }

    @Test
    fun readingPastTheEndIsAnInvalidOffset() {
        assertEquals(HogpRead.InvalidOffset, HogpProfile.readFrom(byteArrayOf(1, 2, 3), 4))
        assertEquals(HogpRead.InvalidOffset, HogpProfile.readFrom(byteArrayOf(1, 2, 3), -1))
    }

    @Test
    fun readingWithoutAValueIsNotReadable() {
        assertEquals(HogpRead.NotReadable, HogpProfile.readFrom(null, 0))
    }

    @Test
    fun reportMapCanBeReadInChunks() {
        val chunk = 22 // ATT payload of a long read at the default MTU.
        val read = mutableListOf<Byte>()
        while (true) {
            val part = assertIs<HogpRead.Value>(HogpProfile.readFrom(reportMap, read.size)).bytes.take(chunk)
            read += part
            if (part.size < chunk) break
        }
        assertContentEquals(reportMap, read.toByteArray())
    }
}
