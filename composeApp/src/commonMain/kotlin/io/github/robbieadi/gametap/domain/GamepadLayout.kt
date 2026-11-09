package io.github.robbieadi.gametap.domain

import kotlin.math.abs

/**
 * Hit region of a control. The center is a fraction of the screen width and height.
 * The half sizes are fractions of the height, so controls keep their shape on any aspect ratio.
 */
data class Region(val cx: Float, val cy: Float, val halfW: Float, val halfH: Float = halfW) {
    fun contains(x: Float, y: Float, width: Float, height: Float): Boolean =
        abs(x - cx * width) <= halfW * height && abs(y - cy * height) <= halfH * height
}

enum class Side { LEFT, RIGHT }

sealed interface GamepadControl {
    val region: Region

    data class ButtonControl(
        val button: Button,
        val label: String,
        override val region: Region,
        val round: Boolean = true,
    ) : GamepadControl

    /** [radius] is the drawn base and the full-deflection distance, as a fraction of the height. */
    data class StickControl(
        val side: Side,
        val radius: Float,
        override val region: Region,
    ) : GamepadControl

    data class TriggerControl(
        val side: Side,
        val label: String,
        override val region: Region,
    ) : GamepadControl

    data class DpadControl(override val region: Region) : GamepadControl
}

/** Data model of the screen, kept separate from drawing so a layout editor can be added later. */
data class GamepadLayout(
    val controls: List<GamepadControl>,
    val settingsCorner: Region,
) {
    companion object {
        val Default: GamepadLayout = GamepadLayout(
            controls = listOf(
                GamepadControl.TriggerControl(Side.LEFT, "LT", Region(0.12f, 0.14f, 0.10f, 0.12f)),
                GamepadControl.ButtonControl(Button.LB, "LB", Region(0.26f, 0.10f, 0.12f, 0.08f), round = false),
                GamepadControl.TriggerControl(Side.RIGHT, "RT", Region(0.88f, 0.14f, 0.10f, 0.12f)),
                GamepadControl.ButtonControl(Button.RB, "RB", Region(0.74f, 0.10f, 0.12f, 0.08f), round = false),

                GamepadControl.StickControl(Side.LEFT, 0.17f, Region(0.14f, 0.52f, 0.21f, 0.23f)),
                GamepadControl.ButtonControl(Button.L3, "L3", Region(0.06f, 0.88f, 0.08f)),
                GamepadControl.DpadControl(Region(0.40f, 0.78f, 0.18f)),

                GamepadControl.StickControl(Side.RIGHT, 0.17f, Region(0.67f, 0.78f, 0.20f, 0.20f)),
                GamepadControl.ButtonControl(Button.R3, "R3", Region(0.94f, 0.88f, 0.08f)),

                GamepadControl.ButtonControl(Button.Y, "Y", Region(0.84f, 0.36f, 0.08f)),
                GamepadControl.ButtonControl(Button.X, "X", Region(0.785f, 0.52f, 0.08f)),
                GamepadControl.ButtonControl(Button.B, "B", Region(0.895f, 0.52f, 0.08f)),
                GamepadControl.ButtonControl(Button.A, "A", Region(0.84f, 0.68f, 0.08f)),

                GamepadControl.ButtonControl(Button.SELECT, "Select", Region(0.42f, 0.26f, 0.09f, 0.055f), round = false),
                GamepadControl.ButtonControl(Button.HOME, "Home", Region(0.50f, 0.42f, 0.075f)),
                GamepadControl.ButtonControl(Button.START, "Start", Region(0.58f, 0.26f, 0.09f, 0.055f), round = false),
            ),
            settingsCorner = Region(0f, 0f, 0.09f, 0.16f),
        )
    }
}
