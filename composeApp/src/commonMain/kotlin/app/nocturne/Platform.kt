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
