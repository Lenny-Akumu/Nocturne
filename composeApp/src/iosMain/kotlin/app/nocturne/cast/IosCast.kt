package app.nocturne.cast

actual fun createCastBackend(): CastBackend = object : CastBackend {
    override suspend fun discover(): List<CastDevice> = emptyList()
    override suspend fun load(device: CastDevice, url: String, mime: String?, title: String, artist: String, art: String?) = Unit
    override suspend fun play(device: CastDevice) = Unit
    override suspend fun pause(device: CastDevice) = Unit
    override suspend fun stop(device: CastDevice) = Unit
    override suspend fun seek(device: CastDevice, ms: Long) = Unit
    override suspend fun status(device: CastDevice): CastStatus? = null
}

actual fun acquireMulticastLock(): () -> Unit = {}
