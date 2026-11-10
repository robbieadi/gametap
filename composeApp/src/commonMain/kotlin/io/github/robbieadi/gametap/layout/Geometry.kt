package io.github.robbieadi.gametap.layout

import kotlin.math.hypot

data class ScreenSize(val width: Float, val height: Float)

/** A control's touch area in pixels for one screen size. */
sealed interface Area {
    data class Circle(val cx: Float, val cy: Float, val r: Float) : Area

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) : Area
}

fun Position.centerX(screen: ScreenSize): Float = when (anchor) {
    Anchor.LEFT -> x * screen.height
    Anchor.CENTER -> screen.width / 2f + x * screen.height
    Anchor.RIGHT -> screen.width - x * screen.height
}

fun Position.centerY(screen: ScreenSize): Float = y * screen.height

fun GamepadControl.area(screen: ScreenSize): Area {
    val cx = position.centerX(screen)
    val cy = position.centerY(screen)
    val h = screen.height
    return when (val shape = shape) {
        is Shape.Circle -> Area.Circle(cx, cy, shape.r * h)
        is Shape.Rect -> Area.Rect(
            left = cx - shape.halfW * h,
            top = cy - shape.halfH * h,
            right = cx + shape.halfW * h,
            bottom = cy + shape.halfH * h,
        )
    }
}

fun Area.contains(x: Float, y: Float): Boolean = when (this) {
    is Area.Circle -> hypot(x - cx, y - cy) <= r
    is Area.Rect -> x in left..right && y in top..bottom
}

/** Areas whose edges only touch do not overlap. */
fun Area.overlaps(other: Area): Boolean = when (this) {
    is Area.Circle -> when (other) {
        is Area.Circle -> hypot(cx - other.cx, cy - other.cy) < r + other.r
        is Area.Rect -> other.overlaps(this)
    }
    is Area.Rect -> when (other) {
        is Area.Circle -> {
            val nearestX = other.cx.coerceIn(left, right)
            val nearestY = other.cy.coerceIn(top, bottom)
            hypot(other.cx - nearestX, other.cy - nearestY) < other.r
        }
        is Area.Rect -> left < other.right && other.left < right && top < other.bottom && other.top < bottom
    }
}

fun Area.isInside(screen: ScreenSize): Boolean = when (this) {
    is Area.Circle -> cx - r >= 0f && cy - r >= 0f && cx + r <= screen.width && cy + r <= screen.height
    is Area.Rect -> left >= 0f && top >= 0f && right <= screen.width && bottom <= screen.height
}
