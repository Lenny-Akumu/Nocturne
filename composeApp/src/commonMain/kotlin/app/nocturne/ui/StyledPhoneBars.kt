package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.DesignStyle
import app.nocturne.i18n.t
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.kit.Icon

/** Phones and portrait tablets: the bottom player + tabs drawn in the Linux, Arch or Terminal look. */
@Composable
internal fun StyledPhoneBar(style: DesignStyle, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteStrip()
        when (style) {
            DesignStyle.ARCH -> ArchBar(onExpand)
            DesignStyle.TERMINAL -> TerminalBar(onExpand)
            else -> LinuxBar(onExpand)
        }
    }
}

/** Height the screens keep free above the bar. */
internal fun styledBarHeight(hasPlayer: Boolean) = if (hasPlayer) 150.dp else 70.dp

@Composable
private fun TextTabs(active: Color, idle: Color, bracket: Boolean, modifier: Modifier = Modifier) {
    val nav = AppGraph.instance.navigator
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        (tabs + settingsTab).forEach { tab ->
            val on = nav.currentTab == tab.screen
            val label = t(tab.label).lowercase()
            Text(
                if (bracket) "[$label]" else label, color = if (on) active else idle,
                style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else null,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { nav.selectTab(tab.screen) }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ArchBar(onExpand: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = AppGraph.instance.player
    val player by p.state.collectAsState()
    Column(Modifier.fillMaxWidth().background(cs.background).border(1.dp, cs.outline, RectangleShape).padding(horizontal = 10.dp, vertical = 6.dp)) {
        player.current?.let { track ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically) {
                Text(if (player.isPlaying) "⏸ " else "▶ ", color = Color(0xFFB8BB26), modifier = Modifier.clickable { p.toggle() })
                Text(track.title, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(" ⏭", color = cs.onSurface, modifier = Modifier.clickable { p.next() })
            }
            SeekLine(player.positionMs, player.durationMs, Modifier.fillMaxWidth()) { p.seekTo(it) }
        }
        TextTabs(cs.primary, cs.onSurfaceVariant, bracket = true)
    }
}

@Composable
private fun TerminalBar(onExpand: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = AppGraph.instance.player
    val player by p.state.collectAsState()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.background).border(1.dp, cs.outline, RoundedCornerShape(12.dp)).padding(10.dp)) {
        player.current?.let { track ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, Modifier.size(44.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = cs.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Cava(player.isPlaying, Modifier.fillMaxWidth().height(18.dp))
                }
            }
            BlockBar(player.positionMs, player.durationMs, player.isPlaying, onToggle = { p.toggle() }) { p.seekTo(it) }
        }
        TextTabs(cs.primary, cs.onSurfaceVariant, bracket = false)
    }
}

@Composable
internal fun LinuxBar(onExpand: () -> Unit, showTabs: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val p = AppGraph.instance.player
    val player by p.state.collectAsState()
    val glow = Brush.linearGradient(listOf(Color(0xFF33CCFF), Color(0xFFEE66FF), Color(0xFFFF8A65)))
    val shape = RoundedCornerShape(18.dp)
    player.current?.let { track ->
        val frac = if (player.durationMs > 0) (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Color(0xE612101E)).border(2.dp, glow, shape)
                .drawBehind {
                    drawLine(Brush.horizontalGradient(listOf(Color(0xFF33CCFF), Color(0xFFEE66FF))), Offset(0f, size.height - 1.5.dp.toPx()), Offset(size.width * frac, size.height - 1.5.dp.toPx()), 3.dp.toPx())
                }
                .clickable(onClick = onExpand).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUrl, Modifier.size(44.dp), RoundedCornerShape(10.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF33CCFF), Color(0xFFEE66FF)))).clickable { p.toggle() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(24.dp), tint = Color.White) }
            Icon(Icons.Rounded.SkipNext, null, Modifier.padding(start = 6.dp).size(28.dp).clickable { p.next() }, tint = Color.White)
        }
    }
    if (!showTabs) return
    val nav = AppGraph.instance.navigator
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xE612101E)).border(1.dp, Color.White.copy(alpha = 0.12f), shape).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        (tabs + settingsTab).forEach { tab ->
            val on = nav.currentTab == tab.screen
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.selectTab(tab.screen) }.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (on) tab.selectedIcon else tab.icon, t(tab.label), Modifier.size(22.dp), tint = if (on) Color(0xFFEE66FF) else cs.onSurfaceVariant)
                Box(Modifier.padding(top = 3.dp).size(width = if (on) 16.dp else 0.dp, height = 3.dp).clip(CircleShape).background(glow))
            }
        }
    }
}
