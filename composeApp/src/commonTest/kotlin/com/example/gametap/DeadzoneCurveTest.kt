package com.example.gametap

import com.example.gametap.domain.DeadzoneCurve
import com.example.gametap.domain.Stick
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadzoneCurveTest {
    @Test
    fun insideDeadzoneIsCentered() {
        assertEquals(Stick.Center, DeadzoneCurve(deadzone = 0.2f).apply(Stick(0.1f, 0.1f)))
    }

    @Test
    fun outputStartsFromZeroAtTheEdge() {
        val out = DeadzoneCurve(deadzone = 0.2f).apply(Stick(x = 0.21f))
        assertTrue(out.x in 0f..0.02f)
    }

    @Test
    fun fullDeflectionStaysFull() {
        val out = DeadzoneCurve(deadzone = 0.2f, exponent = 2f).apply(Stick(x = 1f))
        assertEquals(1f, out.x, 0.0001f)
    }

    @Test
    fun overshootIsLimitedToTheUnitCircle() {
        val out = DeadzoneCurve(deadzone = 0.1f).apply(Stick(2f, 2f))
        assertEquals(1f, hypot(out.x, out.y), 0.0001f)
    }

    @Test
    fun directionIsPreserved() {
        val out = DeadzoneCurve(deadzone = 0.1f, exponent = 2f).apply(Stick(0.3f, 0.6f))
        assertEquals(2f, out.y / out.x, 0.0001f)
    }

    @Test
    fun higherExponentIsFinerNearCenter() {
        val linear = DeadzoneCurve(deadzone = 0f, exponent = 1f).apply(Stick(x = 0.5f))
        val curved = DeadzoneCurve(deadzone = 0f, exponent = 2f).apply(Stick(x = 0.5f))
        assertEquals(0.5f, linear.x, 0.0001f)
        assertEquals(0.25f, curved.x, 0.0001f)
    }
}
