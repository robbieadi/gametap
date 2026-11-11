package io.github.robbieadi.gametap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/** Says the layout does not fit the current screen. The affected controls are outlined on the canvas. */
@Composable
fun FitBanner(onEdit: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .consumeAllTouches()
            .background(GamepadColors.Surface, RoundedCornerShape(50))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(GamepadColors.Warning, CircleShape))
        Text(
            text = "Layout doesn't fit this screen",
            style = MaterialTheme.typography.labelMedium,
            color = GamepadColors.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 5.dp),
        )
        Text(
            text = "Edit",
            style = MaterialTheme.typography.labelLarge,
            color = GamepadColors.Accent,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelLarge,
            color = GamepadColors.Label,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "Dismiss", onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
