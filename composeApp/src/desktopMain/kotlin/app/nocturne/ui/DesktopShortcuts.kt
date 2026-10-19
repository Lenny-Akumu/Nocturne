package app.nocturne.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.HotkeyAction
import app.nocturne.Hotkeys

@Composable
actual fun ShortcutSettings() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    var editing by remember { mutableStateOf<HotkeyAction?>(null) }

    Column {
        Text(
            "Click an action and press the new key combination. Media keys always work.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text("Work when Nocturne is minimized") },
            supportingContent = { Text("Global shortcuts and media keys system-wide") },
            trailingContent = {
                androidx.compose.material3.Switch(
                    checked = settings.globalHotkeys,
                    onCheckedChange = { v -> graph.settings.update { it.copy(globalHotkeys = v) } },
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HotkeyAction.entries.forEach { action ->
            ListItem(
                headlineContent = { Text(action.label) },
                trailingContent = {
                    Text(Hotkeys.label(Hotkeys.binding(action, settings)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                },
                leadingContent = { Icon(Icons.Rounded.Keyboard, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { editing = action }.padding(horizontal = 8.dp),
            )
        }
        ListItem(
            headlineContent = { Text("Reset to defaults") },
            leadingContent = { Icon(Icons.Rounded.Restore, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { graph.settings.update { it.copy(hotkeys = emptyMap()) } }.padding(horizontal = 8.dp),
        )
    }

    editing?.let { action ->
        val focus = remember { FocusRequester() }
        var captured by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { focus.requestFocus() }
        DisposableEffect(Unit) {
            Hotkeys.capturing = true
            onDispose { Hotkeys.capturing = false }
        }
        fun save(binding: String) {
            graph.settings.update { s ->
                val cleared = s.hotkeys + HotkeyAction.entries
                    .filter { it != action && Hotkeys.binding(it, s) == binding && binding.isNotBlank() }
                    .associate { it.name to "" }
                s.copy(hotkeys = cleared + (action.name to binding))
            }
            editing = null
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(action.label) },
            text = {
                Box(
                    Modifier.fillMaxWidth().height(90.dp)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && !Hotkeys.isModifierOnly(e)) captured = Hotkeys.of(e)
                            true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        captured?.let { Hotkeys.label(it) } ?: "Press a key combination…",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { captured?.let { save(it) } }, enabled = captured != null) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { save("") }) { Text("Clear") }
                    TextButton(onClick = { save(action.default) }) { Text("Default") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
