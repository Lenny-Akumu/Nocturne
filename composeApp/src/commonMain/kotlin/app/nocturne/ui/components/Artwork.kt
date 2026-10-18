package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.nocturne.model.CollectionType
import coil3.compose.SubcomposeAsyncImage

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    fallback: ImageVector = Icons.Rounded.MusicNote,
) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { Placeholder(fallback) },
                loading = { Placeholder(fallback) },
            )
        } else Placeholder(fallback)
    }
}

@Composable
private fun Placeholder(icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.fillMaxSize(0.4f).padding(2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

fun CollectionType.fallbackIcon(): ImageVector = when (this) {
    CollectionType.ARTIST -> Icons.Rounded.Person
    CollectionType.ALBUM -> Icons.Rounded.Album
    else -> Icons.Rounded.MusicNote
}

@Composable
fun CollectionType.shape(): Shape = if (this == CollectionType.ARTIST) CircleShape else MaterialTheme.shapes.medium
