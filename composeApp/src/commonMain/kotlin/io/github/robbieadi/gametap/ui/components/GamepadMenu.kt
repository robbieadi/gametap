package io.github.robbieadi.gametap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.robbieadi.gametap.layout.DefaultLayout
import io.github.robbieadi.gametap.layout.Layouts
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.ui.theme.GamepadColors

/**
 * The in-game menu, drawn over the gamepad rather than on its own screen, so the screen stays
 * landscape with the system bars hidden. A tap outside the card closes it. Built for landscape:
 * profiles on the left, actions on the right.
 */
@Composable
fun GamepadMenu(
    layouts: Layouts,
    transport: TransportState,
    onSelectProfile: (String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onRenameProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onEditLayout: () -> Unit,
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .consumeAllTouches()
            .background(GamepadColors.Background.copy(alpha = 0.8f))
            .clickable(interactionSource = null, indication = null, onClickLabel = "Resume", onClick = onResume),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .widthIn(max = 600.dp)
                .background(GamepadColors.Surface, RoundedCornerShape(20.dp))
                // Swallows taps on the card's empty space, so only the scrim resumes.
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Layout", style = MaterialTheme.typography.titleSmall, color = GamepadColors.Label)
                Column(
                    Modifier.padding(top = 8.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                ) {
                    layouts.profiles.forEach { profile ->
                        ProfileRow(
                            name = profile.name,
                            builtIn = profile.id == DefaultLayout.PROFILE_ID,
                            selected = profile.id == layouts.active.id,
                            onClick = { onSelectProfile(profile.id) },
                            onDuplicate = { onDuplicateProfile(profile.id) },
                            onRename = { onRenameProfile(profile.id) }.takeUnless { profile.id == DefaultLayout.PROFILE_ID },
                            onDelete = { onDeleteProfile(profile.id) }.takeUnless { profile.id == DefaultLayout.PROFILE_ID },
                        )
                    }
                }
            }
            Column(
                Modifier.width(180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
                OutlinedButton(onClick = onEditLayout, modifier = Modifier.fillMaxWidth()) { Text("Edit layout") }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
                OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text(leaveLabel(transport)) }
            }
        }
    }
}

/** Leaving always ends at the start screen. While a host is involved it also stops the gamepad. */
private fun leaveLabel(transport: TransportState): String = when (transport) {
    is TransportState.Connected -> "Disconnect"
    TransportState.Advertising -> "Stop"
    else -> "Exit"
}

@Composable
private fun ProfileRow(
    name: String,
    builtIn: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) GamepadColors.SurfacePressed else GamepadColors.Surface,
                RoundedCornerShape(10.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = GamepadColors.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (builtIn) {
            Text("Built-in", style = MaterialTheme.typography.labelSmall, color = GamepadColors.Label.copy(alpha = 0.6f))
        }
        if (selected) {
            Text("✓", style = MaterialTheme.typography.bodyMedium, color = GamepadColors.Accent)
        }
        Box {
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = true }) { Text("⋯", color = GamepadColors.Label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                fun close(action: () -> Unit): () -> Unit = {
                    open = false
                    action()
                }
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = close(onDuplicate))
                onRename?.let { DropdownMenuItem(text = { Text("Rename") }, onClick = close(it)) }
                onDelete?.let { DropdownMenuItem(text = { Text("Delete", color = GamepadColors.Error) }, onClick = close(it)) }
            }
        }
    }
}
