package app.nocturne.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.Rule
import app.nocturne.data.RuleField
import app.nocturne.data.RuleOp
import app.nocturne.data.SmartPlaylist
import app.nocturne.data.SmartPlaylists
import app.nocturne.i18n.t
import app.nocturne.nowMillis
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.TrackRow
import app.nocturne.ui.kit.Icon

// Ready-made starting points, so the screen is never empty.
private fun presets(): List<SmartPlaylist> = listOf(
    SmartPlaylist("p-forgotten", t("Forgotten favourites"), listOf(Rule(RuleField.LIKED, RuleOp.IS), Rule(RuleField.LAST_PLAYED, RuleOp.MORE, "60"))),
    SmartPlaylist("p-hidden", t("Liked but barely played"), listOf(Rule(RuleField.LIKED, RuleOp.IS), Rule(RuleField.PLAYS, RuleOp.LESS, "3")), sort = "random"),
    SmartPlaylist("p-night", t("Night owl rotation"), listOf(Rule(RuleField.NIGHT, RuleOp.IS))),
    SmartPlaylist("p-heavy", t("Heavy rotation this month"), listOf(Rule(RuleField.PLAYED_WITHIN, RuleOp.IS, "30"), Rule(RuleField.PLAYS, RuleOp.MORE, "5"))),
)

@Composable
fun SmartPlaylistsView(padding: PaddingValues) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    var open by remember { mutableStateOf<SmartPlaylist?>(null) }
    var editing by remember { mutableStateOf<SmartPlaylist?>(null) }
    val mine = settings.smartPlaylists
    val list = mine + presets().filter { p -> mine.none { it.id == p.id } }

    val o = open
    if (o != null) {
        val tracks = remember(o, library.plays.size, library.liked.size) { SmartPlaylists.evaluate(o, library, nowMillis()) }
        LazyColumn(contentPadding = padding) {
            item {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Back")) }
                    Text(o.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { editing = o }) { Icon(Icons.Rounded.Edit, t("Edit")) }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { graph.player.play(tracks, 0, o.name) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(t("Play"))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { graph.player.play(tracks, tracks.indices.random(), o.name, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text(t("Shuffle"))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${tracks.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(describe(o), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            }
            if (tracks.isEmpty()) item { MessageBox(Icons.Rounded.AutoAwesome, "Nothing matches yet", "Keep listening, or loosen the rules.") }
            items(tracks.size, key = { tracks[it].key }) { i ->
                TrackRow(tracks[i], onClick = { graph.player.play(tracks, i, o.name) }, modifier = Modifier.padding(horizontal = 4.dp), menuContext = TrackMenuContext())
            }
        }
    } else LazyColumn(contentPadding = padding) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FilledTonalButton(onClick = { editing = SmartPlaylist("s" + nowMillis(), "", listOf(Rule(RuleField.LIKED, RuleOp.IS))) }) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(t("New smart playlist"))
                }
            }
        }
        items(list, key = { it.id }) { p ->
            val count = remember(p, library.plays.size, library.liked.size) { SmartPlaylists.evaluate(p, library, nowMillis()).size }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { open = p }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6A3DE8), Color(0xFFE84D8A)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(26.dp), tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t("%s songs", count) + " · " + describe(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (mine.any { it.id == p.id }) IconButton(onClick = { graph.settings.update { s -> s.copy(smartPlaylists = s.smartPlaylists.filterNot { it.id == p.id }) } }) {
                    Icon(Icons.Rounded.Delete, t("Delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editing?.let { e ->
        RuleEditor(e, onDismiss = { editing = null }) { saved ->
            graph.settings.update { s -> s.copy(smartPlaylists = s.smartPlaylists.filterNot { it.id == saved.id } + saved) }
            editing = null
            if (open != null) open = saved
        }
    }
}

private fun describe(p: SmartPlaylist): String = p.rules.joinToString(if (p.matchAll) " · " else " / ") { r ->
    listOf(t(r.field.label), t(r.op.label), r.value).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun RuleEditor(initial: SmartPlaylist, onDismiss: () -> Unit, onSave: (SmartPlaylist) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var rules by remember { mutableStateOf(initial.rules) }
    var all by remember { mutableStateOf(initial.matchAll) }
    var sort by remember { mutableStateOf(initial.sort) }
    var limit by remember { mutableStateOf(initial.limit.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Smart playlist")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(t("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(all, { all = true }, label = { Text(t("Match all")) })
                    FilterChip(!all, { all = false }, label = { Text(t("Match any")) })
                }
                rules.forEachIndexed { i, r ->
                    RuleRow(r, onChange = { nr -> rules = rules.toMutableList().also { it[i] = nr } }, onRemove = { rules = rules.filterIndexed { j, _ -> j != i } })
                }
                TextButton(onClick = { rules = rules + Rule(RuleField.PLAYS, RuleOp.MORE, "3") }) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text(t("Add rule"))
                }
                Text(t("Sort by"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("plays" to "Most played", "recent" to "Recent", "random" to "Random", "title" to "Title").forEach { (k, l) ->
                        FilterChip(sort == k, { sort = k }, label = { Text(t(l)) })
                    }
                }
                OutlinedTextField(limit, { limit = it.filter(Char::isDigit).take(4) }, label = { Text(t("Max songs")) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), rules = rules, matchAll = all, sort = sort, limit = limit.toIntOrNull()?.coerceIn(1, 5000) ?: 100))
            }) { Text(t("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun RuleRow(rule: Rule, onChange: (Rule) -> Unit, onRemove: () -> Unit) {
    var fieldMenu by remember { mutableStateOf(false) }
    var opMenu by remember { mutableStateOf(false) }
    var srcMenu by remember { mutableStateOf(false) }
    val needsValue = rule.field !in setOf(RuleField.LIKED, RuleField.NIGHT)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { fieldMenu = true }) { Text(t(rule.field.label), maxLines = 1) }
                DropdownMenu(fieldMenu, { fieldMenu = false }) {
                    RuleField.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(t(f.label)) }, onClick = {
                            fieldMenu = false
                            onChange(Rule(f, SmartPlaylists.opsFor(f).first(), if (f == RuleField.SOURCE) "SPOTIFY" else if (f == RuleField.PLAYED_WITHIN) "30" else ""))
                        })
                    }
                }
            }
            Box {
                TextButton(onClick = { opMenu = true }) { Text(t(rule.op.label)) }
                DropdownMenu(opMenu, { opMenu = false }) {
                    SmartPlaylists.opsFor(rule.field).forEach { op -> DropdownMenuItem(text = { Text(t(op.label)) }, onClick = { opMenu = false; onChange(rule.copy(op = op)) }) }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, t("Remove")) }
        }
        if (rule.field == RuleField.SOURCE) Box {
            TextButton(onClick = { srcMenu = true }) { Text(app.nocturne.model.Source.entries.firstOrNull { it.name == rule.value }?.label ?: rule.value) }
            DropdownMenu(srcMenu, { srcMenu = false }) {
                app.nocturne.model.Source.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { srcMenu = false; onChange(rule.copy(value = s.name)) }) }
            }
        } else if (needsValue) OutlinedTextField(rule.value, { onChange(rule.copy(value = it)) }, singleLine = true, modifier = Modifier.fillMaxWidth().height(56.dp))
    }
}
