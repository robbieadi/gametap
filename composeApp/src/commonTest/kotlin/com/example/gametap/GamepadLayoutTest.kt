package com.example.gametap

import com.example.gametap.domain.GamepadLayout
import com.example.gametap.domain.Region
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse

class GamepadLayoutTest {
    private fun overlaps(a: Region, b: Region, width: Float, height: Float): Boolean =
        abs(a.cx * width - b.cx * width) < (a.halfW + b.halfW) * height &&
            abs(a.cy * height - b.cy * height) < (a.halfH + b.halfH) * height

    @Test
    fun settingsCornerClearsEveryControlOnCommonAspectRatios() {
        val layout = GamepadLayout.Default
        for (aspect in listOf(16f / 9f, 18f / 9f, 19.5f / 9f, 21f / 9f)) {
            for (control in layout.controls) {
                assertFalse(overlaps(layout.settingsCorner, control.region, aspect * 1000f, 1000f), "$control at $aspect")
            }
        }
    }
}
