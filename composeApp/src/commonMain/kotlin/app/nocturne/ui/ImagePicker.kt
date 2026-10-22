package app.nocturne.ui

import androidx.compose.runtime.Composable

// Opens the system photo picker; hands back a small square-ish JPEG (or nothing if cancelled).
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit
