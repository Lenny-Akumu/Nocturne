package app.nocturne

actual val platformKind: PlatformKind = PlatformKind.ANDROID

actual fun deviceName(): String {
    val maker = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val model = android.os.Build.MODEL.orEmpty()
    return (if (model.startsWith(maker, true)) model else "$maker $model").trim().ifBlank { "Android" }
}
