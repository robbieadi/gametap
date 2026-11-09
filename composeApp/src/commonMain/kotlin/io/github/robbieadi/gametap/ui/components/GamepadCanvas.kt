package io.github.robbieadi.gametap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.domain.GamepadControl
import io.github.robbieadi.gametap.domain.GamepadLayout
import io.github.robbieadi.gametap.domain.GamepadState
import io.github.robbieadi.gametap.domain.Region
import io.github.robbieadi.gametap.domain.Side
import io.github.robbieadi.gametap.domain.Stick
import io.github.robbieadi.gametap.input.TouchPoint
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/** Draws the controls. State is read in the draw phase, so input changes redraw without recomposing. */
@Composable
fun GamepadCanvas(
    layout: GamepadLayout,
    state: State<GamepadState>,
    stickOrigins: State<Map<Side, TouchPoint>>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(color = GamepadColors.Label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Canvas(modifier) {
        val current = state.value
        val origins = stickOrigins.value
        drawSettingsHint()
        layout.controls.forEach { control ->
            when (control) {
                is GamepadControl.ButtonControl -> drawButton(
                    control, current.isPressed(control.button), measurer, labelStyle,
                )
                is GamepadControl.StickControl -> drawStick(
                    control,
                    if (control.side == Side.LEFT) current.leftStick else current.rightStick,
                    origins[control.side],
                )
                is GamepadControl.TriggerControl -> drawTrigger(
                    control,
                    if (control.side == Side.LEFT) current.leftTrigger else current.rightTrigger,
                    measurer,
                    labelStyle,
                )
                is GamepadControl.DpadControl -> drawDpad(control, current.dpad)
            }
        }
    }
}

/** Three dots in the corner that opens settings on a long press. */
private fun DrawScope.drawSettingsHint() {
    val dot = size.height * 0.008f
    repeat(3) { index ->
        drawCircle(
            GamepadColors.Outline,
            dot,
            Offset(size.height * 0.035f, size.height * (0.04f + index * 0.03f)),
        )
    }
}

private fun DrawScope.center(region: Region) = Offset(region.cx * size.width, region.cy * size.height)

private fun DrawScope.bounds(region: Region): Rect {
    val center = center(region)
    val halfW = region.halfW * size.height
    val halfH = region.halfH * size.height
    return Rect(center.x - halfW, center.y - halfH, center.x + halfW, center.y + halfH)
}

private fun DrawScope.drawLabel(text: String, center: Offset, measurer: TextMeasurer, style: TextStyle) {
    val result = measurer.measure(text, style)
    drawText(
        result,
        topLeft = Offset(center.x - result.size.width / 2f, center.y - result.size.height / 2f),
    )
}

private fun DrawScope.drawButton(
    control: GamepadControl.ButtonControl,
    pressed: Boolean,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val fill = if (pressed) GamepadColors.SurfacePressed else GamepadColors.Surface
    val rect = bounds(control.region)
    if (control.round) {
        val radius = rect.height / 2f
        drawCircle(fill, radius, rect.center)
        drawCircle(GamepadColors.Outline, radius, rect.center, style = Stroke(1.5f))
    } else {
        val corner = CornerRadius(rect.height / 3f)
        drawRoundRect(fill, rect.topLeft, rect.size, corner)
        drawRoundRect(GamepadColors.Outline, rect.topLeft, rect.size, corner, style = Stroke(1.5f))
    }
    drawLabel(control.label, rect.center, measurer, style)
}

private fun DrawScope.drawStick(control: GamepadControl.StickControl, stick: Stick, origin: TouchPoint?) {
    val radius = control.radius * size.height
    val base = origin?.let { Offset(it.x, it.y) } ?: center(control.region)
    drawCircle(GamepadColors.Surface, radius, base)
    drawCircle(GamepadColors.Outline, radius, base, style = Stroke(1.5f))
    val knob = Offset(base.x + stick.x * radius, base.y + stick.y * radius)
    drawCircle(GamepadColors.SurfacePressed, radius * 0.5f, knob)
}

private fun DrawScope.drawTrigger(
    control: GamepadControl.TriggerControl,
    value: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val rect = bounds(control.region)
    val corner = CornerRadius(rect.width / 6f)
    drawRoundRect(GamepadColors.Surface, rect.topLeft, rect.size, corner)
    if (value > 0f) {
        drawRoundRect(
            GamepadColors.Accent.copy(alpha = 0.55f),
            rect.topLeft,
            Size(rect.width, rect.height * value),
            corner,
        )
    }
    drawRoundRect(GamepadColors.Outline, rect.topLeft, rect.size, corner, style = Stroke(1.5f))
    drawLabel(control.label, rect.center, measurer, style)
}

private fun DrawScope.drawDpad(control: GamepadControl.DpadControl, dpad: Dpad) {
    val rect = bounds(control.region)
    val arm = rect.width / 3f
    fun fill(active: Boolean): Color = if (active) GamepadColors.SurfacePressed else GamepadColors.Surface
    val corner = CornerRadius(arm / 5f)
    fun drawArm(left: Float, top: Float, active: Boolean) {
        drawRoundRect(fill(active), Offset(left, top), Size(arm, arm), corner)
        drawRoundRect(GamepadColors.Outline, Offset(left, top), Size(arm, arm), corner, style = Stroke(1.5f))
    }
    drawArm(rect.left + arm, rect.top, dpad.up)
    drawArm(rect.left + arm, rect.bottom - arm, dpad.down)
    drawArm(rect.left, rect.top + arm, dpad.left)
    drawArm(rect.right - arm, rect.top + arm, dpad.right)
    drawRect(GamepadColors.Surface, Offset(rect.left + arm, rect.top + arm), Size(arm, arm))
}
