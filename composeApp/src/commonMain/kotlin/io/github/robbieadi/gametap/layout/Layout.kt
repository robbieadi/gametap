package io.github.robbieadi.gametap.layout

import io.github.robbieadi.gametap.domain.Button
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every length is a fraction of the screen height. Only the anchor ties a control to the width,
// so another aspect ratio changes the gaps between the anchor groups, not the controls themselves.
//
// The @SerialName values are the stored format. Rename Kotlin names freely, never these.

@Serializable
enum class Anchor {
    /** [Position.x] runs from the left edge to the control's center. */
    @SerialName("left") LEFT,

    /** [Position.x] is a signed offset from the middle of the screen, positive to the right. */
    @SerialName("center") CENTER,

    /** [Position.x] runs from the right edge to the control's center. */
    @SerialName("right") RIGHT,
}

/** Where a control's center sits. [y] runs down from the top edge. */
@Serializable
data class Position(val anchor: Anchor, val x: Float, val y: Float)

@Serializable
sealed interface Shape {
    @Serializable
    @SerialName("circle")
    data class Circle(val r: Float) : Shape

    @Serializable
    @SerialName("rect")
    data class Rect(val halfW: Float, val halfH: Float) : Shape
}

@Serializable
enum class Side {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

@Serializable
enum class DpadDirection {
    @SerialName("up") UP,
    @SerialName("down") DOWN,
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

/** What a button sends while held. */
@Serializable
sealed interface ButtonAction {
    @Serializable
    @SerialName("button")
    data class Pad(val button: Button) : ButtonAction

    @Serializable
    @SerialName("dpad")
    data class Dpad(val direction: DpadDirection) : ButtonAction

    /** The trigger fully pulled while held. */
    @Serializable
    @SerialName("trigger")
    data class Trigger(val side: Side) : ButtonAction
}

/**
 * Each kind stores only the size fields it allows. [shape] is the touch area, the drawn outline
 * and what validation checks, so the three always agree. Kinds with a fixed shape compute it,
 * and a computed property has no backing field, so it is not serialized.
 */
@Serializable
sealed interface GamepadControl {
    /** Stable across edits and saves. */
    val id: String
    val position: Position
    val shape: Shape

    @Serializable
    @SerialName("button")
    data class ButtonControl(
        override val id: String,
        override val position: Position,
        override val shape: Shape,
        val action: ButtonAction,
        /** Text, emoji or both. */
        val label: String,
    ) : GamepadControl

    @Serializable
    @SerialName("stick")
    data class StickControl(
        override val id: String,
        override val position: Position,
        val side: Side,
        /** The drawn base and the full-deflection distance. */
        val radius: Float,
        /** The touch area, larger than [radius] so an imprecise thumb still catches the stick. */
        val grabRadius: Float,
    ) : GamepadControl {
        override val shape: Shape get() = Shape.Circle(grabRadius)
    }

    /** Analog trigger: how far the finger drags sets how far the trigger is pulled. */
    @Serializable
    @SerialName("throttle")
    data class ThrottleControl(
        override val id: String,
        override val position: Position,
        val side: Side,
        val halfW: Float,
        val halfH: Float,
        val label: String,
    ) : GamepadControl {
        override val shape: Shape get() = Shape.Rect(halfW, halfH)
    }

    /** One-finger cross that also reads diagonals. */
    @Serializable
    @SerialName("dpad")
    data class DpadControl(
        override val id: String,
        override val position: Position,
        val radius: Float,
    ) : GamepadControl {
        override val shape: Shape get() = Shape.Circle(radius)
    }

    /** Exactly one per profile. A long press opens the app menu. It sends nothing to the host. */
    @Serializable
    @SerialName("menu")
    data class MenuControl(
        override val id: String,
        override val position: Position,
        override val shape: Shape,
        val label: String = "☰",
    ) : GamepadControl
}

@Serializable
data class LayoutProfile(
    val id: String,
    val name: String,
    val controls: List<GamepadControl>,
)

/** Everything the layout file holds. [version] lets a later format migrate older files. */
@Serializable
data class LayoutStore(
    val version: Int = CURRENT_VERSION,
    val activeProfileId: String,
    val profiles: List<LayoutProfile>,
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}
