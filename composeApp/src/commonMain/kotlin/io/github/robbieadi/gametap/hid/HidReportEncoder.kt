package io.github.robbieadi.gametap.hid

import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadState
import kotlin.math.roundToInt

/** Encodes [GamepadState] into the 9-byte payload of report 1 (the report ID is not included). */
object HidReportEncoder {
    const val REPORT_SIZE: Int = 9
    const val HAT_CENTERED: Int = 8

    fun encode(state: GamepadState): ByteArray = byteArrayOf(
        (state.buttons and 0xFF).toByte(),
        (state.buttons shr 8 and 0xFF).toByte(),
        hat(state.dpad).toByte(),
        axis(state.leftStick.x),
        axis(state.leftStick.y),
        axis(state.rightStick.x),
        axis(state.rightStick.y),
        trigger(state.leftTrigger),
        trigger(state.rightTrigger),
    )

    /** 0-7 clockwise from north, [HAT_CENTERED] for none. Opposite directions cancel out. */
    fun hat(dpad: Dpad): Int {
        val dx = (if (dpad.right) 1 else 0) - (if (dpad.left) 1 else 0)
        val dy = (if (dpad.down) 1 else 0) - (if (dpad.up) 1 else 0)
        return when {
            dx == 0 && dy < 0 -> 0
            dx > 0 && dy < 0 -> 1
            dx > 0 && dy == 0 -> 2
            dx > 0 && dy > 0 -> 3
            dx == 0 && dy > 0 -> 4
            dx < 0 && dy > 0 -> 5
            dx < 0 && dy == 0 -> 6
            dx < 0 && dy < 0 -> 7
            else -> HAT_CENTERED
        }
    }

    /** Maps -1..1 to 0..255. Exact center (127.5) rounds up to 128. */
    private fun axis(value: Float): Byte =
        (value.coerceIn(-1f, 1f) * 127.5f + 127.5f).roundToInt().toByte()

    private fun trigger(value: Float): Byte =
        (value.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
}
