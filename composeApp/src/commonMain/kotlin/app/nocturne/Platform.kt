package app.nocturne

expect fun nowMillis(): Long

expect object FileStore {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)
}

enum class PlatformKind { ANDROID, DESKTOP }

expect val platformKind: PlatformKind
expect fun currentHour(): Int

expect fun sha1Hex(text: String): String

expect fun sha256(bytes: ByteArray): ByteArray

expect fun secureRandomBytes(size: Int): ByteArray

expect val spotifyRedirectUri: String

expect suspend fun authorizeInBrowser(url: String): String

expect fun systemLocale(): Pair<String, String>
