package io.github.robbieadi.gametap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/**
 * A dialog drawn inside the screen rather than in its own window. A separate dialog window would
 * bring back the system bars and take touches away mid-gesture on the gamepad screens.
 */
@Composable
fun OverlayDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .consumeAllTouches()
            .background(GamepadColors.Background.copy(alpha = 0.8f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .background(GamepadColors.Surface, RoundedCornerShape(20.dp))
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GamepadColors.Label)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
                TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
        }
    }
}
