package app.nocturne.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.PlatformKind
import app.nocturne.data.DesignStyle
import app.nocturne.data.IconBackground
import app.nocturne.data.IconPalette
import app.nocturne.data.Settings
import app.nocturne.i18n.t
import app.nocturne.platformKind
import app.nocturne.ui.NocturneIcon
import app.nocturne.ui.applyAppIcon
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.kit.iosGroupRow

/** Settings → Appearance → App icon: background and colour pickers with live previews. */
@Composable
fun AppIconSettings(s: Settings, set: ((Settings) -> Settings) -> Unit) {
    fun choose(bg: IconBackground, pal: IconPalette) {
        set { it.copy(iconBackground = bg, iconPalette = pal) }
        applyAppIcon(bg, pal)
    }
    // iOS has no system accent to follow.
    val palettes = IconPalette.entries.filter { it != IconPalette.SYSTEM || platformKind != PlatformKind.IOS }
    IconRow("Background") {
        IconBackground.entries.forEach { bg ->
            IconTile(bg.label, bg == s.iconBackground, { choose(bg, s.iconPalette) }) { NocturneIcon(bg, s.iconPalette, it) }
        }
    }
    IconRow("Color") {
        palettes.forEach { pal ->
            IconTile(pal.label, pal == s.iconPalette, { choose(s.iconBackground, pal) }) { NocturneIcon(s.iconBackground, pal, it) }
        }
    }
}

@Composable
private fun IconRow(title: String, content: @Composable () -> Unit) {
    val ios = LocalDesignStyle.current == DesignStyle.GLASS
    Column((if (ios) Modifier.iosGroupRow(16.dp) else Modifier).padding(horizontal = if (ios) 16.dp else 24.dp, vertical = 10.dp)) {
        Text(t(title), style = MaterialTheme.typography.bodyLarge)
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun IconTile(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Modifier) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(76.dp).clip(shape)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(Modifier.size(52.dp))
        Text(
            t(label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
