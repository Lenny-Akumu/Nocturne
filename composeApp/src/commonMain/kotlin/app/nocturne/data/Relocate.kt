package app.nocturne.data

/** Native folder picker; null when cancelled or unsupported. */
expect fun pickFolder(title: String): String?

/** Folder the app runs from, or null where it can't be moved. */
expect fun appInstallDir(): String?

/** Reinstalls / moves the app into [target] and restarts it from there. */
expect suspend fun relocateApp(target: String, onStatus: (String) -> Unit)
