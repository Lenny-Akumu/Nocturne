package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.data.ProfilePhoto
import app.nocturne.ui.kit.Icon
import coil3.compose.AsyncImage

// Own photo > first connected account's photo > initials > person glyph.
@Composable
fun ProfileAvatar(size: Dp, modifier: Modifier = Modifier, gradient: Boolean = false) {
    val acc by AppGraph.instance.accounts.state.collectAsState()
    val own by ProfilePhoto.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val url = acc.youtubeAvatar.takeIf { acc.youtubeSignedIn }
        ?: acc.spotifyAvatar.takeIf { acc.spotifySignedIn }
        ?: acc.soundcloudAvatar.takeIf { acc.soundcloudSignedIn }
    val name = listOfNotNull(
        acc.youtubeName.takeIf { acc.youtubeSignedIn },
        acc.spotifyName.takeIf { acc.spotifySignedIn },
        acc.soundcloudName.takeIf { acc.soundcloudSignedIn },
    ).firstOrNull { it.isNotBlank() }
    val bg = if (gradient) Modifier.background(Brush.linearGradient(listOf(Color(0xFFE2D4FF), Color(0xFF9B6BFF), Color(0xFF4B2A9E))))
    else Modifier.background(cs.primaryContainer)
    val fg = if (gradient) Color(0xFF1A1033) else cs.onPrimaryContainer
    Box(modifier.size(size).clip(CircleShape).then(bg), contentAlignment = Alignment.Center) {
        when {
            own != null -> AsyncImage(own, null, Modifier.size(size), contentScale = ContentScale.Crop)
            url != null -> Artwork(url, Modifier.size(size), CircleShape, Icons.Rounded.Person)
            name != null -> Text(initials(name), style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * 0.38f).sp), color = fg)
            else -> Icon(Icons.Rounded.Person, null, Modifier.size(size * 0.58f), tint = fg)
        }
    }
}

private fun initials(name: String): String =
    name.split(' ', '_', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
