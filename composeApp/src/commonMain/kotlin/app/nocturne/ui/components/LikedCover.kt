package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.min
import app.nocturne.model.CollectionRef
import app.nocturne.model.Source

/** The service whose own "Liked songs" collection this is (gets a branded cover), else null. */
fun CollectionRef.likedBrand(): Source? = when {
    source == Source.YOUTUBE_MUSIC && id == "LM" -> Source.YOUTUBE_MUSIC
    source == Source.SPOTIFY && id == "me:liked" -> Source.SPOTIFY
    source == Source.SOUNDCLOUD && id == "me:likes" -> Source.SOUNDCLOUD
    else -> null
}

private fun likedGradient(source: Source): List<Color> = when (source) {
    Source.YOUTUBE_MUSIC -> listOf(Color(0xFFFF0033), Color(0xFFB30024), Color(0xFF3D000C))
    Source.SPOTIFY -> listOf(Color(0xFF1DB954), Color(0xFF117A37), Color(0xFF121212))
    Source.SOUNDCLOUD -> listOf(Color(0xFFFF8800), Color(0xFFFF5500), Color(0xFF8C2300))
    Source.LOCAL -> listOf(Color(0xFF7C5CFF), Color(0xFF2A1B66))
}

/** Gradient in the service's colours, a big heart and the service badge. */
@Composable
fun LikedCover(source: Source, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    BoxWithConstraints(modifier.clip(shape).background(Brush.linearGradient(likedGradient(source)))) {
        val d = min(maxWidth, maxHeight)
        Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(d * 0.44f))
        Box(
            Modifier.align(Alignment.BottomEnd).padding(d * 0.07f).size(d * 0.2f).background(Color.White, CircleShape).padding(d * 0.012f),
        ) { SourceBadge(source, size = d * 0.176f) }
    }
}

/** Collection artwork that swaps in [LikedCover] for a service's liked songs. */
@Composable
fun CollectionArtwork(ref: CollectionRef, modifier: Modifier, shape: Shape) {
    val brand = ref.likedBrand()
    if (brand != null) LikedCover(brand, modifier, shape)
    else Artwork(ref.artworkUrl, modifier, shape, ref.type.fallbackIcon())
}
