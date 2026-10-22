package app.nocturne.data

import app.nocturne.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// User-picked profile picture (small JPEG), kept on disk as base64.
@OptIn(ExperimentalEncodingApi::class)
object ProfilePhoto {
    private const val FILE = "profile_photo.b64"
    private val _state = MutableStateFlow(runCatching { FileStore.read(FILE)?.let { Base64.decode(it) } }.getOrNull())
    val state: StateFlow<ByteArray?> = _state.asStateFlow()

    fun set(bytes: ByteArray) {
        FileStore.write(FILE, Base64.encode(bytes))
        _state.value = bytes
    }

    fun clear() {
        FileStore.delete(FILE)
        _state.value = null
    }
}
