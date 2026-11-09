package io.github.robbieadi.gametap.domain

data class GamepadSettings(
    val deadzone: Float = 0.08f,
    val curveExponent: Float = 1f,
    val hapticsEnabled: Boolean = true,
    val floatingSticks: Boolean = false,
) {
    val stickCurve: DeadzoneCurve get() = DeadzoneCurve(deadzone, curveExponent)
}
