package app.nocturne

expect fun nowMillis(): Long

expect object FileStore {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)
}

enum class PlatformKind { ANDROID, DESKTOP, IOS }

expect val platformKind: PlatformKind
expect fun currentHour(): Int

/** Local (month 1..12, day of month). */
expect fun currentMonthDay(): Pair<Int, Int>

/** Local minutes since midnight. */
expect fun minuteOfDay(): Int

expect fun sha1Hex(text: String): String

expect fun sha256(bytes: ByteArray): ByteArray

expect fun secureRandomBytes(size: Int): ByteArray

expect val spotifyRedirectUri: String

expect suspend fun authorizeInBrowser(url: String): String

expect fun systemLocale(): Pair<String, String>

expect fun aesEncrypt(key: ByteArray, plain: ByteArray): ByteArray

expect fun aesDecrypt(key: ByteArray, data: ByteArray): ByteArray

/** Default name shown to the user's other devices. */
expect fun deviceName(): String
