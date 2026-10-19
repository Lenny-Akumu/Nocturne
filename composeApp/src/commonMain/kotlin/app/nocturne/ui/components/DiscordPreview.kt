package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.DiscordPresence
import app.nocturne.data.Settings
import app.nocturne.model.ArtistRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.model.formatDuration

private val sample = Track("preview", Source.SPOTIFY, "Midnight City", listOf(ArtistRef("M83")), "Hurry Up, We're Dreaming", durationMs = 243_000)

@Composable
fun DiscordPreview(s: Settings, track: Track?) {
    val tr = track ?: sample
    val bg = Color(0xFF232428)
    val fg = Color(0xFFF2F3F5)
    val dim = Color(0xFFB5BAC1)
    Column(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(14.dp),
    ) {
        Text(
            (if (s.discordActivityType == 2) "LISTENING TO " else "PLAYING ") + "NOCTURNE",
            color = dim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.discordShowArtwork || s.discordShowSource) {
                Box(Modifier.size(72.dp)) {
                    if (s.discordShowArtwork) Artwork(tr.artworkUrl, Modifier.size(72.dp), RoundedCornerShape(8.dp))
                    else SourceBadge(tr.source, size = 72.dp)
                    if (s.discordShowArtwork && s.discordShowSource) {
                        Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).clip(CircleShape).background(bg).padding(2.dp)) {
                            SourceBadge(tr.source, size = 20.dp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                listOf(s.discordDetails to true, s.discordState to false).forEach { (tpl, bold) ->
                    val line = DiscordPresence.fill(tpl, tr)
                    if (line.isNotEmpty()) Text(line, color = if (bold) fg else dim, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.discordShowTime && tr.durationMs > 0) {
                    Text("0:42 / ${tr.durationMs.formatDuration()}", color = dim, fontSize = 12.sp)
                }
            }
        }
        if (s.discordShowButton) {
            val label = DiscordPresence.fill(s.discordButton, tr).take(32)
            if (label.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    label,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF4E5058)).padding(vertical = 7.dp),
                    color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
