package app.nocturne.ui

import androidx.compose.ui.graphics.ImageBitmap

expect fun ImageBitmap.encodePng(): ByteArray

/** Saves or shares the picture; returns where it landed when there's a path to show. */
expect suspend fun shareImage(png: ByteArray, name: String): String?
