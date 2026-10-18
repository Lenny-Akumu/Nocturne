package app.nocturne

actual val platformKind: PlatformKind = PlatformKind.DESKTOP

val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
