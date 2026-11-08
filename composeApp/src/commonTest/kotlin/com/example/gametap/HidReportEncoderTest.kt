package com.example.gametap

import com.example.gametap.domain.Button
import com.example.gametap.domain.Dpad
import com.example.gametap.domain.GamepadState
import com.example.gametap.domain.Stick
import com.example.gametap.hid.HidReportEncoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HidReportEncoderTest {
    private fun encode(state: GamepadState) = HidReportEncoder.encode(state).map { it.toInt() and 0xFF }

    @Test
    fun neutralState() {
        assertContentEquals(listOf(0, 0, 8, 128, 128, 128, 128, 0, 0), encode(GamepadState()))
    }

    @Test
    fun everyButtonSetsExactlyItsBit() {
        // Slots hosts assign: A B C X Y Z L1 R1 L2 R2 Select Start Mode L3 R3.
        val expectedBits = mapOf(
            Button.A to 0, Button.B to 1, Button.X to 3, Button.Y to 4,
            Button.LB to 6, Button.RB to 7,
            Button.SELECT to 10, Button.START to 11, Button.HOME to 12,
            Button.L3 to 13, Button.R3 to 14,
        )
        assertEquals(Button.entries.toSet(), expectedBits.keys)
        expectedBits.forEach { (button, bit) ->
            val report = encode(GamepadState(buttons = button.mask))
            assertEquals(1 shl bit, report[0] or (report[1] shl 8), button.name)
        }
    }

    @Test
    fun hatCoversAllDirections() {
        val cases = mapOf(
            Dpad(up = true) to 0,
            Dpad(up = true, right = true) to 1,
            Dpad(right = true) to 2,
            Dpad(down = true, right = true) to 3,
            Dpad(down = true) to 4,
            Dpad(down = true, left = true) to 5,
            Dpad(left = true) to 6,
            Dpad(up = true, left = true) to 7,
            Dpad() to 8,
            Dpad(up = true, down = true) to 8,
            Dpad(left = true, right = true) to 8,
        )
        cases.forEach { (dpad, hat) -> assertEquals(hat, encode(GamepadState(dpad = dpad))[2], "$dpad") }
    }

    @Test
    fun sticksMapToBytePositions() {
        val report = encode(
            GamepadState(leftStick = Stick(-1f, 1f), rightStick = Stick(1f, -1f)),
        )
        assertEquals(listOf(0, 255, 255, 0), report.subList(3, 7))
    }

    @Test
    fun axesClampOutOfRangeValues() {
        val report = encode(
            GamepadState(leftStick = Stick(-3f, 3f), leftTrigger = 2f, rightTrigger = -1f),
        )
        assertEquals(listOf(0, 255), report.subList(3, 5))
        assertEquals(listOf(255, 0), report.subList(7, 9))
    }

    @Test
    fun axesRoundToNearest() {
        // 0.5 * 127.5 + 127.5 = 191.25
        assertEquals(191, encode(GamepadState(leftStick = Stick(x = 0.5f)))[3])
        // 0.5 * 255 = 127.5
        assertEquals(128, encode(GamepadState(leftTrigger = 0.5f))[7])
    }
}
