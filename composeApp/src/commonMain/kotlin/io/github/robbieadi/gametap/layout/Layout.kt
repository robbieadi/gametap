package io.github.robbieadi.gametap.layout

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.domain.DeadzoneCurve
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

    /** Controls gyro aiming. Sends nothing to the host itself. */
    @Serializable
    @SerialName("gyro")
    data class Gyro(val action: GyroButton) : ButtonAction
}

/**
 * The gyro mode a button or volume key sets for its profile. A profile uses one mode, on any
 * number of buttons and keys; without any, it has no gyro.
 */
@Serializable
enum class GyroButton {
    /** Gyro works while this is held, and pressing it sets the neutral angle. */
    @SerialName("hold") HOLD,

    /** Turns gyro on and off. It starts off with each profile, and turning it on sets the neutral angle. */
    @SerialName("toggle") TOGGLE,

    /** Gyro is always on, and pressing this makes the current angle neutral. */
    @SerialName("recenter") RECENTER,
}

/** Which way of moving the phone counts as horizontal tilt. */
@Serializable
enum class GyroHorizontal {
    /** Turning the phone like a steering wheel. */
    @SerialName("roll") ROLL,

    /** Turning the phone left and right around its vertical axis. */
    @SerialName("yaw") YAW,
}

@Serializable
enum class GyroAxis {
    /** Positive is to the right. */
    @SerialName("horizontal") HORIZONTAL,

    /**
     * Positive is up: the top edge tipping toward the player, so the back of the phone points
     * higher, like a camera looking up.
     */
    @SerialName("vertical") VERTICAL,
}

/** A gamepad input that gyro can drive. At most one binding per target. */
enum class GyroTarget { LEFT_STICK, RIGHT_STICK, LEFT_TRIGGER, RIGHT_TRIGGER }

/** One output driven by tilting the phone away from its neutral angle. */
@Serializable
sealed interface GyroBinding {
    /** Degrees of tilt past the deadzone that give full output. */
    val range: Float
    val target: GyroTarget

    /** A stick takes one or both axes. Tilting back (top edge toward the player) pushes it up. */
    @Serializable
    @SerialName("stick")
    data class StickBinding(
        val side: Side,
        val horizontal: Boolean = true,
        val vertical: Boolean = true,
        override val range: Float = DEFAULT_RANGE,
        val invertVertical: Boolean = false,
    ) : GyroBinding {
        override val target: GyroTarget get() = if (side == Side.LEFT) GyroTarget.LEFT_STICK else GyroTarget.RIGHT_STICK
    }

    /**
     * A trigger takes one axis and pulls on one side of it: tilting right or up, or with
     * [invert] left or down. Tilting the other way leaves it released.
     */
    @Serializable
    @SerialName("trigger")
    data class TriggerBinding(
        val side: Side,
        val axis: GyroAxis,
        val invert: Boolean = false,
        override val range: Float = DEFAULT_RANGE,
    ) : GyroBinding {
        override val target: GyroTarget get() = if (side == Side.LEFT) GyroTarget.LEFT_TRIGGER else GyroTarget.RIGHT_TRIGGER
    }

    companion object {
        const val DEFAULT_RANGE: Float = 30f
        const val MIN_RANGE: Float = 5f
        const val MAX_RANGE: Float = 90f
    }
}

@Serializable
data class GyroSetup(
    val bindings: List<GyroBinding> = emptyList(),
    val horizontal: GyroHorizontal = GyroHorizontal.ROLL,
    /** Degrees of tilt either side of neutral that send nothing, to hide hand tremor. */
    val deadzone: Float = DEFAULT_DEADZONE,
) {
    fun binding(target: GyroTarget): GyroBinding? = bindings.firstOrNull { it.target == target }

    companion object {
        /** Aiming with the right stick, for a layout's first gyro button. */
        val DEFAULT_BINDINGS: List<GyroBinding> = listOf(GyroBinding.StickBinding(Side.RIGHT))

        const val DEFAULT_DEADZONE: Float = 1f
        /** Below [GyroBinding.MIN_RANGE], so every binding has some travel. */
        const val MAX_DEADZONE: Float = 4f
    }
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
        /** Share of the travel that sends nothing, 0 to [MAX_DEADZONE]. */
        val deadzone: Float = DEFAULT_DEADZONE,
        /** Response curve: 1 is linear, higher is finer near the center, up to [MAX_CURVE]. */
        val curveExponent: Float = 1f,
        /** The stick centers where the thumb lands instead of at its drawn position. */
        val floating: Boolean = false,
    ) : GamepadControl {
        override val shape: Shape get() = Shape.Circle(grabRadius)

        /** Clamped, so an edited layout file cannot break the curve; validation reports it. */
        val curve: DeadzoneCurve
            get() = DeadzoneCurve(deadzone.coerceIn(0f, MAX_DEADZONE), curveExponent.coerceIn(MIN_CURVE, MAX_CURVE))

        val tuningIsValid: Boolean get() = deadzone in 0f..MAX_DEADZONE && curveExponent in MIN_CURVE..MAX_CURVE

        companion object {
            const val DEFAULT_DEADZONE: Float = 0.08f
            const val MAX_DEADZONE: Float = 0.4f
            const val MIN_CURVE: Float = 1f
            const val MAX_CURVE: Float = 3f
        }
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
}

/** The phone's hardware keys a profile can bind. */
enum class VolumeKey { UP, DOWN }

/**
 * What the volume keys send while held. A null key keeps changing the volume. Keys the platform
 * cannot capture, as on iOS, are kept with the profile for other phones.
 */
@Serializable
data class VolumeKeys(val up: ButtonAction? = null, val down: ButtonAction? = null) {
    operator fun get(key: VolumeKey): ButtonAction? = when (key) {
        VolumeKey.UP -> up
        VolumeKey.DOWN -> down
    }

    fun with(key: VolumeKey, action: ButtonAction?): VolumeKeys = when (key) {
        VolumeKey.UP -> copy(up = action)
        VolumeKey.DOWN -> copy(down = action)
    }

    val actions: List<ButtonAction> get() = listOfNotNull(up, down)

    val isEmpty: Boolean get() = up == null && down == null
}

@Serializable
data class LayoutProfile(
    val id: String,
    val name: String,
    val controls: List<GamepadControl>,
    /** Defaults to no bindings, so files written before gyro existed still read. */
    val gyro: GyroSetup = GyroSetup(),
    /** Defaults to none, so files written before volume keys existed still read. */
    val volumeKeys: VolumeKeys = VolumeKeys(),
    /** A short vibration on each press, where the phone can vibrate. */
    val haptics: Boolean = true,
) {
    /** What every button control and bound volume key sends. */
    val buttonActions: List<ButtonAction>
        get() = controls.mapNotNull { (it as? GamepadControl.ButtonControl)?.action } + volumeKeys.actions

    /**
     * How gyro is switched, from the gyro buttons and keys; null when there are none, so the
     * profile has no gyro. A profile mixing modes is invalid; until it is fixed, hold wins over
     * toggle and toggle over always on, as before modes were exclusive.
     */
    val gyroMode: GyroButton?
        get() = buttonActions.mapNotNullTo(HashSet()) { (it as? ButtonAction.Gyro)?.action }.minByOrNull { it.ordinal }

    /** The curve of the stick on [side], or the default where the layout has no such stick. */
    fun stickCurve(side: Side): DeadzoneCurve =
        controls.firstNotNullOfOrNull { (it as? GamepadControl.StickControl)?.takeIf { stick -> stick.side == side } }?.curve
            ?: DeadzoneCurve()
}

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
