package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.hid.HidDescriptor
import io.github.robbieadi.gametap.hid.HidReportEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

class HidDescriptorTest {
    /** The Xbox Wireless Controller (1914) Report Map. Hosts' Xbox drivers expect exactly this. */
    @Test
    fun descriptorMatchesTheXboxControllerByteForByte() {
        val golden = "05 01 09 05 A1 01 85 01 09 01 A1 00 09 30 09 31 15 00 27 FF FF 00 00 95 " +
            "02 75 10 81 02 C0 09 01 A1 00 09 32 09 35 15 00 27 FF FF 00 00 95 02 75 " +
            "10 81 02 C0 05 02 09 C5 15 00 26 FF 03 95 01 75 0A 81 02 15 00 25 00 75 " +
            "06 95 01 81 03 05 02 09 C4 15 00 26 FF 03 95 01 75 0A 81 02 15 00 25 00 " +
            "75 06 95 01 81 03 05 01 09 39 15 01 25 08 35 00 46 3B 01 66 14 00 75 04 " +
            "95 01 81 42 75 04 95 01 15 00 25 00 35 00 45 00 65 00 81 03 05 09 19 01 " +
            "29 0F 15 00 25 01 75 01 95 0F 81 02 15 00 25 00 75 01 95 01 81 03 05 0C " +
            "0A B2 00 15 00 25 01 95 01 75 01 81 02 15 00 25 00 75 07 95 01 81 03 05 " +
            "0F 09 21 85 03 A1 02 09 97 15 00 25 01 75 04 95 01 91 02 15 00 25 00 75 " +
            "04 95 01 91 03 09 70 15 00 25 64 75 08 95 04 91 02 09 50 66 01 10 55 0E " +
            "15 00 26 FF 00 75 08 95 01 91 02 09 A7 15 00 26 FF 00 75 08 95 01 91 02 " +
            "65 00 55 00 09 7C 15 00 26 FF 00 75 08 95 01 91 02 C0 C0"
        val actual = HidDescriptor.bytes.joinToString(" ") {
            (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
        assertEquals(golden, actual)
    }

    @Test
    fun inputBitsAddUpToTheReportAndOutputBitsToTheOutputReport() {
        // Report Size and Report Count are global items; each Input or Output item uses their current values.
        val bytes = HidDescriptor.bytes.map { it.toInt() and 0xFF }
        var size = 0
        var count = 0
        var inputBits = 0
        var outputBits = 0
        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i]
            val length = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            when (prefix and 0xFC) {
                0x74 -> size = bytes[i + 1]
                0x94 -> count = bytes[i + 1]
                0x80 -> inputBits += size * count
                0x90 -> outputBits += size * count
            }
            i += 1 + length
        }
        assertEquals(HidReportEncoder.REPORT_SIZE * 8, inputBits)
        assertEquals(HidDescriptor.OUTPUT_REPORT_SIZE * 8, outputBits)
    }
}
