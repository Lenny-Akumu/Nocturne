package app.nocturne.ui

import androidx.compose.runtime.Composable

// Opens the system photo picker; hands back a small JPEG (or nothing if cancelled).
// [large]: wallpaper-sized JPEG, and GIFs untouched so they keep animating.
@Composable
expect fun rememberImagePicker(large: Boolean = false, onPicked: (ByteArray) -> Unit): () -> Unit
