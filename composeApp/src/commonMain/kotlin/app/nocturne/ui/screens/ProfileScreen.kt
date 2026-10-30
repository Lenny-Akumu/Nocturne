package app.nocturne.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.data.Achievement
import app.nocturne.data.Achievements
import app.nocturne.i18n.t
import app.nocturne.ui.Screen
import app.nocturne.ui.components.ProfileAvatar
import app.nocturne.ui.kit.FilledTonalButton
import app.nocturne.ui.kit.Icon

/** Opened from the profile picture: who you are, what you've unlocked, how you listen. */
@Composable
fun ProfileScreen(padding: PaddingValues) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val nav = graph.navigator
    val name = settings.nickname.trim().ifBlank { settings.deviceName.ifBlank { app.nocturne.deviceName() } }
    val got = settings.achievements
    val all = Achievements.all
    val minutes = library.plays.sumOf { it.d } / 60_000
    StatsView(padding) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (nav.canGoBack) IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Back")) }
                Text(t("Profile"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = { nav.push(Screen.Settings) }) { Icon(Icons.Rounded.Settings, t("Settings")) }
            }
        }
        item {
            val cs = MaterialTheme.colorScheme
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(116.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(cs.primary, cs.tertiary, cs.secondary, cs.primary)))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) { ProfileAvatar(108.dp) }
                Spacer(Modifier.height(10.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        t("%s min listened", minutes),
                        t("%s liked", library.liked.size),
                        settings.lastfmUser.takeIf { it.isNotBlank() }?.let { "Last.fm: $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { nav.push(Screen.SettingsPage(SettingsCategory.ACCOUNTS)) }) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Edit profile"))
                }
            }
        }
        item {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(t("Achievements"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${got.keys.count { k -> all.any { it.id == k } }}/${all.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            val sorted = all.sortedByDescending { got[it.id] ?: -1L }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val cols = (maxWidth / 160.dp).toInt().coerceIn(2, 6)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sorted.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { a -> Badge(a, a.id in got, Modifier.weight(1f)) }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            Text(t("Stats"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 8.dp))
        }
    }
}

@Composable
private fun Badge(a: Achievement, on: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.height(138.dp).clip(shape)
            .background(if (on) Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)) else Brush.linearGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainerHigh)))
            .border(1.dp, if (on) cs.primary.copy(alpha = 0.35f) else cs.outlineVariant.copy(alpha = 0.4f), shape)
            .padding(12.dp),
    ) {
        Text(if (on || !a.secret) a.emoji else "❔", fontSize = 28.sp, modifier = Modifier.alpha(if (on) 1f else 0.35f))
        Spacer(Modifier.height(6.dp))
        Text(if (on || !a.secret) t(a.title) else t("Secret"), style = MaterialTheme.typography.titleSmall,
            color = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (a.secret && !on) t("Find it yourself") else t(a.hint), style = MaterialTheme.typography.bodySmall,
            color = (if (on) cs.onPrimaryContainer else cs.onSurfaceVariant).copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
