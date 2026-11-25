package io.github.robbieadi.gametap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/**
 * Says why the link just dropped: the phone's 2.4 GHz Wi-Fi shares its radio with Bluetooth.
 * Stays while connected again, until dismissed, so it is seen after the gamepad recovers.
 */
@Composable
fun WifiClashBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .consumeTouchDowns()
            .background(GamepadColors.Surface, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(GamepadColors.Warning, CircleShape))
        Text(
            text = "The connection dropped. This phone's 2.4 GHz Wi-Fi can interrupt Bluetooth: " +
                "use a 5 GHz network, or turn Wi-Fi off while playing.",
            style = MaterialTheme.typography.labelMedium,
            color = GamepadColors.Label,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
        )
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelLarge,
            color = GamepadColors.Label,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "Dismiss", onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
