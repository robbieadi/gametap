package com.example.gametap.domain

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * Radial deadzone plus a power response curve.
 *
 * Travel outside the deadzone is rescaled to 0..1, so output stays continuous at the
 * deadzone edge and still reaches full deflection. [exponent] 1 is linear; higher values
 * give finer control near the center.
 */
data class DeadzoneCurve(val deadzone: Float = 0.08f, val exponent: Float = 1f) {
    fun apply(stick: Stick): Stick {
        val magnitude = hypot(stick.x, stick.y)
        if (magnitude <= deadzone || magnitude == 0f) return Stick.Center
        val travel = (min(magnitude, 1f) - deadzone) / (1f - deadzone)
        val scale = travel.pow(exponent) / magnitude
        return Stick(stick.x * scale, stick.y * scale)
    }
}
