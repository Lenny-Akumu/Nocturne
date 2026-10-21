package app.nocturne.source

actual suspend fun scanLocalAudio(folders: List<String>): List<LocalAudio> = emptyList()

actual fun hasLocalAudioAccess(): Boolean = false

actual fun requestLocalAudioAccess(onResult: (Boolean) -> Unit) = onResult(false)
