package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.hid.HidDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HidDescriptorTest {
    @Test
    fun descriptorMatchesGoldenBytes() {
        val golden = "05 01 09 05 A1 01 85 01 " +
            "05 09 19 01 29 10 15 00 25 01 75 01 95 10 81 02 " +
            "05 01 09 39 15 00 25 07 35 00 46 3B 01 65 14 75 04 95 01 81 42 " +
            "65 00 45 00 75 04 95 01 81 03 " +
            "09 30 09 31 09 32 09 35 15 00 26 FF 00 75 08 95 04 81 02 " +
            "05 02 09 C5 09 C4 95 02 81 02 C0"
        val actual = HidDescriptor.bytes.joinToString(" ") {
            (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
        assertEquals(golden, actual)
    }

    @Test
    fun axesUseUsagesThatHostsDoNotCross() {
        val bytes = HidDescriptor.bytes.map { it.toInt() and 0xFF }
        val usages = mutableListOf<Pair<Int, Int>>()
        var page = 0
        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i]
            val length = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            when (prefix and 0xFC) {
                0x04 -> page = bytes[i + 1]
                0x08 -> usages += page to bytes[i + 1]
            }
            i += 1 + length
        }
        val genericDesktop = 0x01
        val simulation = 0x02
        val rx = genericDesktop to 0x33
        val ry = genericDesktop to 0x34
        assertFalse(rx in usages || ry in usages, "SDL would read Rx/Ry as the right stick")
        assertEquals(
            listOf(
                genericDesktop to 0x30, genericDesktop to 0x31, // Left stick: X, Y
                genericDesktop to 0x32, genericDesktop to 0x35, // Right stick: Z, Rz
                simulation to 0xC5, simulation to 0xC4,         // Triggers: Brake, Accelerator
            ),
            usages.filter { it != genericDesktop to 0x05 && it != genericDesktop to 0x39 },
        )
    }

    @Test
    fun inputBitsAddUpToTheNineByteReport() {
        // Report Size and Report Count are global items; each Input item uses their current values.
        val bytes = HidDescriptor.bytes.map { it.toInt() and 0xFF }
        var size = 0
        var count = 0
        var bits = 0
        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i]
            val length = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            when (prefix and 0xFC) {
                0x74 -> size = bytes[i + 1]
                0x94 -> count = bytes[i + 1]
                0x80 -> bits += size * count
            }
            i += 1 + length
        }
        assertEquals(9 * 8, bits)
    }
}
