package io.github.robbieadi.gametap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object GamepadColors {
    val Background = Color(0xFF12141A)
    val Surface = Color(0xFF232733)
    val SurfacePressed = Color(0xFF4A5268)
    val Outline = Color(0xFF3A4050)
    val Label = Color(0xFFD8DCE6)
    val Accent = Color(0xFF5CC8FF)
    val Connected = Color(0xFF4CD787)
    val Warning = Color(0xFFFFB454)
    val Error = Color(0xFFFF6B6B)
}

@Composable
fun GametapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GamepadColors.Accent,
            background = GamepadColors.Background,
            surface = GamepadColors.Background,
        ),
        content = content,
    )
}
