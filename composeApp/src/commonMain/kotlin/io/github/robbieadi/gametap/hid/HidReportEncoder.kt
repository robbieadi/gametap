package io.github.robbieadi.gametap.hid

import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadState
import kotlin.math.roundToInt

/**
 * Encodes [GamepadState] into the 16-byte payload of report 1 (the report ID is not included), in
 * the Xbox Wireless Controller's layout, see [HidDescriptor]. Multi-byte fields are little-endian.
 */
object HidReportEncoder {
    const val REPORT_SIZE: Int = 16
    const val HAT_CENTERED: Int = 0
    const val STICK_CENTER: Int = 0x8000
    const val TRIGGER_MAX: Int = 1023

    fun encode(state: GamepadState): ByteArray {
        val report = ByteArray(REPORT_SIZE)
        report.put16(0, stick(state.leftStick.x))
        report.put16(2, stick(state.leftStick.y))
        report.put16(4, stick(state.rightStick.x))
        report.put16(6, stick(state.rightStick.y))
        report.put16(8, trigger(state.leftTrigger))
        report.put16(10, trigger(state.rightTrigger))
        report[12] = hat(state.dpad).toByte()
        // The button bits are the Xbox controller's: A B _ X Y _ LB RB _ _ View Menu Xbox L3 R3.
        report.put16(13, state.buttons and 0x7FFF)
        report[15] = 0 // Share: Gametap has no Share control.
        return report
    }

    /** 1-8 clockwise from north, [HAT_CENTERED] for none. Opposite directions cancel out. */
    fun hat(dpad: Dpad): Int {
        val dx = (if (dpad.right) 1 else 0) - (if (dpad.left) 1 else 0)
        val dy = (if (dpad.down) 1 else 0) - (if (dpad.up) 1 else 0)
        return when {
            dx == 0 && dy < 0 -> 1
            dx > 0 && dy < 0 -> 2
            dx > 0 && dy == 0 -> 3
            dx > 0 && dy > 0 -> 4
            dx == 0 && dy > 0 -> 5
            dx < 0 && dy > 0 -> 6
            dx < 0 && dy == 0 -> 7
            dx < 0 && dy < 0 -> 8
            else -> HAT_CENTERED
        }
    }

    /** Maps -1..1 to 0..65535. Exact center (32767.5) rounds up to [STICK_CENTER]. */
    private fun stick(value: Float): Int = (value.coerceIn(-1f, 1f) * 32767.5f + 32767.5f).roundToInt()

    private fun trigger(value: Float): Int = (value.coerceIn(0f, 1f) * TRIGGER_MAX).roundToInt()

    private fun ByteArray.put16(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = (value shr 8 and 0xFF).toByte()
    }
}
