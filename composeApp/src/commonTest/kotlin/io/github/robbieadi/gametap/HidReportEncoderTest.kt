package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.domain.Stick
import io.github.robbieadi.gametap.hid.HidReportEncoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Offsets follow SDL's Xbox Bluetooth parser, which counts the report ID as byte 0: subtract 1. */
class HidReportEncoderTest {
    private fun encode(state: GamepadState) = HidReportEncoder.encode(state).map { it.toInt() and 0xFF }

    private fun List<Int>.u16(offset: Int) = this[offset] or (this[offset + 1] shl 8)

    @Test
    fun neutralState() {
        assertContentEquals(
            listOf(0x00, 0x80, 0x00, 0x80, 0x00, 0x80, 0x00, 0x80, 0, 0, 0, 0, 0, 0, 0, 0),
            encode(GamepadState()),
        )
    }

    @Test
    fun everyButtonSetsExactlyItsXboxBit() {
        // A B _ X Y _ LB RB _ _ View Menu Xbox L3 R3, as SDL reads bytes 14-15 of the report.
        val expectedBits = mapOf(
            Button.A to 0, Button.B to 1, Button.X to 3, Button.Y to 4,
            Button.LB to 6, Button.RB to 7,
            Button.SELECT to 10, Button.START to 11, Button.HOME to 12,
            Button.L3 to 13, Button.R3 to 14,
        )
        assertEquals(Button.entries.toSet(), expectedBits.keys)
        expectedBits.forEach { (button, bit) ->
            val report = encode(GamepadState(buttons = button.mask))
            assertEquals(1 shl bit, report.u16(13), button.name)
            assertEquals(0, report[15], "Share stays off for ${button.name}")
        }
    }

    @Test
    fun hatCoversAllDirections() {
        val cases = mapOf(
            Dpad(up = true) to 1,
            Dpad(up = true, right = true) to 2,
            Dpad(right = true) to 3,
            Dpad(down = true, right = true) to 4,
            Dpad(down = true) to 5,
            Dpad(down = true, left = true) to 6,
            Dpad(left = true) to 7,
            Dpad(up = true, left = true) to 8,
            Dpad() to 0,
            Dpad(up = true, down = true) to 0,
            Dpad(left = true, right = true) to 0,
        )
        cases.forEach { (dpad, hat) -> assertEquals(hat, encode(GamepadState(dpad = dpad))[12], "$dpad") }
    }

    @Test
    fun sticksAreSixteenBitLittleEndian() {
        val report = encode(GamepadState(leftStick = Stick(-1f, 1f), rightStick = Stick(1f, -1f)))
        assertEquals(listOf(0, 65535, 65535, 0), listOf(report.u16(0), report.u16(2), report.u16(4), report.u16(6)))
    }

    @Test
    fun triggersAreTenBit() {
        val report = encode(GamepadState(leftTrigger = 1f, rightTrigger = 0.5f))
        assertEquals(1023, report.u16(8))
        assertEquals(512, report.u16(10)) // 511.5 rounds up
    }

    @Test
    fun axesClampOutOfRangeValues() {
        val report = encode(GamepadState(leftStick = Stick(-3f, 3f), leftTrigger = 2f, rightTrigger = -1f))
        assertEquals(listOf(0, 65535), listOf(report.u16(0), report.u16(2)))
        assertEquals(listOf(1023, 0), listOf(report.u16(8), report.u16(10)))
    }

    @Test
    fun axesRoundToNearest() {
        // 0.5 * 32767.5 + 32767.5 = 49151.25
        assertEquals(49151, encode(GamepadState(leftStick = Stick(x = 0.5f))).u16(0))
    }
}
