package io.github.robbieadi.gametap.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Marks every touch on this element as consumed once its children (buttons, rows) have seen it.
 * The gamepad screen skips consumed touch-downs, so a touch on an overlay never also presses a
 * control underneath.
 */
fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}
