package app.nocturne.data

actual fun pickFolder(title: String): String? = null

actual fun appInstallDir(): String? = null

actual suspend fun relocateApp(target: String, onStatus: (String) -> Unit) = Unit
