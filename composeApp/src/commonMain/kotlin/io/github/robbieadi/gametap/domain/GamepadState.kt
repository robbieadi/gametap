package io.github.robbieadi.gametap.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gamepad buttons and their HID report bits.
 *
 * The gaps are deliberate. Linux and Android give each HID button slot a fixed role:
 * A, B, C, X, Y, Z, L1, R1, L2, R2, Select, Start, Mode, L3, R3. Bits 2, 5, 8, 9 and 15 stay
 * unused because this pad has no C or Z button and its triggers are analog axes.
 */
@Serializable
enum class Button(val bit: Int) {
    @SerialName("a") A(0),
    @SerialName("b") B(1),
    @SerialName("x") X(3),
    @SerialName("y") Y(4),
    @SerialName("lb") LB(6),
    @SerialName("rb") RB(7),
    @SerialName("select") SELECT(10),
    @SerialName("start") START(11),
    @SerialName("home") HOME(12),
    @SerialName("l3") L3(13),
    @SerialName("r3") R3(14);

    val mask: Int get() = 1 shl bit
}

/** Stick position, each axis in -1..1. Up and left are negative, as in HID. */
data class Stick(val x: Float = 0f, val y: Float = 0f) {
    companion object {
        val Center = Stick()
    }
}

data class Dpad(
    val up: Boolean = false,
    val down: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
) {
    val isNeutral: Boolean get() = !up && !down && !left && !right
}

/** Raw input as the player touches it. Stick response curves are applied at send time. */
data class GamepadState(
    val buttons: Int = 0,
    val dpad: Dpad = Dpad(),
    val leftStick: Stick = Stick.Center,
    val rightStick: Stick = Stick.Center,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
) {
    fun isPressed(button: Button): Boolean = buttons and button.mask != 0
}
