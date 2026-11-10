package io.github.robbieadi.gametap.layout

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.DpadControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import io.github.robbieadi.gametap.layout.GamepadControl.ThrottleControl

/**
 * The built-in profile. It fits every screen from 16:10 to 21:9 (see DefaultLayoutTest).
 * The D-pad, the sticks' touch areas and the left stick's height are what make 16:10 fit,
 * so check that test after moving any of them.
 */
object DefaultLayout {
    const val PROFILE_ID: String = "default"

    val profile: LayoutProfile = LayoutProfile(
        id = PROFILE_ID,
        name = "Default",
        controls = listOf(
            MenuControl("menu", left(0.07f, 0.07f), Shape.Circle(0.055f)),

            ThrottleControl("lt", left(0.267f, 0.14f), Side.LEFT, halfW = 0.10f, halfH = 0.12f, label = "LT"),
            button("lb", left(0.578f, 0.10f), Button.LB, "LB", Shape.Rect(0.12f, 0.08f)),
            ThrottleControl("rt", right(0.267f, 0.14f), Side.RIGHT, halfW = 0.10f, halfH = 0.12f, label = "RT"),
            button("rb", right(0.578f, 0.10f), Button.RB, "RB", Shape.Rect(0.12f, 0.08f)),

            StickControl("left_stick", left(0.311f, 0.48f), Side.LEFT, radius = 0.17f, grabRadius = 0.20f),
            button("l3", left(0.133f, 0.88f), Button.L3, "L3", Shape.Circle(0.08f)),
            DpadControl("dpad", center(-0.28f, 0.80f), radius = 0.17f),

            StickControl("right_stick", right(0.70f, 0.78f), Side.RIGHT, radius = 0.17f, grabRadius = 0.20f),
            button("r3", right(0.133f, 0.88f), Button.R3, "R3", Shape.Circle(0.08f)),

            button("y", right(0.356f, 0.36f), Button.Y, "Y", Shape.Circle(0.08f)),
            button("x", right(0.478f, 0.52f), Button.X, "X", Shape.Circle(0.08f)),
            button("b", right(0.233f, 0.52f), Button.B, "B", Shape.Circle(0.08f)),
            button("a", right(0.356f, 0.68f), Button.A, "A", Shape.Circle(0.08f)),

            button("select", center(-0.178f, 0.26f), Button.SELECT, "Select", Shape.Rect(0.09f, 0.055f)),
            button("home", center(0f, 0.42f), Button.HOME, "Home", Shape.Circle(0.075f)),
            button("start", center(0.178f, 0.26f), Button.START, "Start", Shape.Rect(0.09f, 0.055f)),
        ),
    )

    private fun left(x: Float, y: Float) = Position(Anchor.LEFT, x, y)
    private fun center(x: Float, y: Float) = Position(Anchor.CENTER, x, y)
    private fun right(x: Float, y: Float) = Position(Anchor.RIGHT, x, y)

    private fun button(id: String, position: Position, button: Button, label: String, shape: Shape) =
        ButtonControl(id, position, shape, ButtonAction.Pad(button), label)
}
