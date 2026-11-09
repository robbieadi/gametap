package com.example.gametap

import com.example.gametap.domain.GamepadControl
import com.example.gametap.domain.GamepadLayout
import com.example.gametap.domain.Region
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse

class GamepadLayoutTest {
    private val layout = GamepadLayout.Default

    /** 16:10 tablets up to 21:9 phones. 4:3 is too narrow for this layout. */
    private val aspects = listOf(16f / 10f, 16f / 9f, 18f / 9f, 19.5f / 9f, 21f / 9f)

    private fun overlaps(a: Region, b: Region, width: Float, height: Float): Boolean =
        abs(a.cx * width - b.cx * width) < (a.halfW + b.halfW) * height &&
            abs(a.cy * height - b.cy * height) < (a.halfH + b.halfH) * height

    @Test
    fun settingsCornerClearsEveryControl() {
        for (aspect in aspects) {
            for (control in layout.controls) {
                assertFalse(overlaps(layout.settingsCorner, control.region, aspect * 1000f, 1000f), "$control at $aspect")
            }
        }
    }

    /**
     * A button may overlap a stick, because buttons always win. Two buttons, or two of the
     * sticks, triggers and D-pad, must not: the first in the list would silently take the touch.
     */
    @Test
    fun controlsThatCompeteForATouchDoNotOverlap() {
        for (aspect in aspects) {
            for ((i, a) in layout.controls.withIndex()) {
                for (b in layout.controls.drop(i + 1)) {
                    val aButton = a is GamepadControl.ButtonControl
                    val bButton = b is GamepadControl.ButtonControl
                    if (aButton != bButton) continue
                    assertFalse(overlaps(a.region, b.region, aspect * 1000f, 1000f), "$a and $b at $aspect")
                }
            }
        }
    }
}
