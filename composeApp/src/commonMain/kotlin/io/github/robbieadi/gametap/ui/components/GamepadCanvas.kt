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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robbieadi.gametap.domain.Dpad
import io.github.robbieadi.gametap.input.StickView
import io.github.robbieadi.gametap.input.TouchView
import io.github.robbieadi.gametap.layout.Area
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.area
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/**
 * Draws the controls in list order, so a later control is drawn on top, matching the tracker's
 * touch order. Each control fills exactly its touch area, except a stick, whose touch area is
 * larger than its drawn base. [view] is read in the draw phase, so touches redraw without recomposing.
 *
 * Controls in [flagged] get a warning outline around their touch area, drawn above everything, so
 * an overlap shows even where one control hides the other. For a stick this is the grab area,
 * which is what overlaps.
 */
@Composable
fun GamepadCanvas(
    layout: LayoutProfile,
    view: State<TouchView>,
    modifier: Modifier = Modifier,
    flagged: Set<String> = emptySet(),
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(color = GamepadColors.Label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Canvas(modifier) {
        val current = view.value
        val screen = ScreenSize(size.width, size.height)
        layout.controls.forEach { control ->
            val area = control.area(screen)
            when (control) {
                is GamepadControl.ButtonControl ->
                    drawButton(area, control.label, current.isPressed(control), measurer, labelStyle)
                is GamepadControl.MenuControl ->
                    drawButton(area, control.label, current.isPressed(control), measurer, labelStyle)
                is GamepadControl.StickControl ->
                    drawStick(area as Area.Circle, control.radius * size.height, current.sticks[control.id])
                is GamepadControl.ThrottleControl ->
                    drawThrottle(area as Area.Rect, current.throttles[control.id] ?: 0f, control.label, measurer, labelStyle)
                is GamepadControl.DpadControl ->
                    drawDpad(area as Area.Circle, current.dpads[control.id] ?: Dpad())
            }
        }
        if (flagged.isNotEmpty()) {
            val stroke = Stroke(2.5.dp.toPx())
            layout.controls.filter { it.id in flagged }.forEach { control ->
                when (val area = control.area(screen)) {
                    is Area.Circle -> drawCircle(GamepadColors.Warning, area.r, Offset(area.cx, area.cy), style = stroke)
                    is Area.Rect -> drawRect(GamepadColors.Warning, Offset(area.left, area.top), area.toRect().size, style = stroke)
                }
            }
        }
    }
}

private fun Area.Rect.toRect() = Rect(left, top, right, bottom)

private fun DrawScope.drawLabel(text: String, center: Offset, measurer: TextMeasurer, style: TextStyle) {
    val result = measurer.measure(text, style)
    drawText(
        result,
        topLeft = Offset(center.x - result.size.width / 2f, center.y - result.size.height / 2f),
    )
}

private fun DrawScope.drawButton(
    area: Area,
    label: String,
    pressed: Boolean,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val fill = if (pressed) GamepadColors.SurfacePressed else GamepadColors.Surface
    val center = when (area) {
        is Area.Circle -> {
            val center = Offset(area.cx, area.cy)
            drawCircle(fill, area.r, center)
            drawCircle(GamepadColors.Outline, area.r, center, style = Stroke(1.5f))
            center
        }
        is Area.Rect -> {
            val rect = area.toRect()
            val corner = CornerRadius(minOf(rect.width, rect.height) / 3f)
            drawRoundRect(fill, rect.topLeft, rect.size, corner)
            drawRoundRect(GamepadColors.Outline, rect.topLeft, rect.size, corner, style = Stroke(1.5f))
            rect.center
        }
    }
    drawLabel(label, center, measurer, style)
}

/** The base sits at the touch-down point when floating, else at the control's center. */
private fun DrawScope.drawStick(area: Area.Circle, radius: Float, stick: StickView?) {
    val base = stick?.origin?.let { Offset(it.x, it.y) } ?: Offset(area.cx, area.cy)
    drawCircle(GamepadColors.Surface, radius, base)
    drawCircle(GamepadColors.Outline, radius, base, style = Stroke(1.5f))
    val value = stick?.value
    val knob = if (value == null) base else Offset(base.x + value.x * radius, base.y + value.y * radius)
    drawCircle(GamepadColors.SurfacePressed, radius * 0.5f, knob)
}

private fun DrawScope.drawThrottle(
    area: Area.Rect,
    value: Float,
    label: String,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val rect = area.toRect()
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
    drawLabel(label, rect.center, measurer, style)
}

/** The cross fits inside the round touch area: its arm corners reach [CROSS_IN_CIRCLE] of the radius. */
private fun DrawScope.drawDpad(area: Area.Circle, dpad: Dpad) {
    val half = area.r * CROSS_IN_CIRCLE
    val rect = Rect(area.cx - half, area.cy - half, area.cx + half, area.cy + half)
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

/** An arm's outer corner sits at (1/3, 1) of the half-size, which is sqrt(10)/3 ≈ 1.054 from the center. */
private const val CROSS_IN_CIRCLE = 0.948f
