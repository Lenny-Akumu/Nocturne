package app.nocturne

actual val platformKind: PlatformKind = PlatformKind.DESKTOP

val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

actual fun deviceName(): String =
    System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "PC"
