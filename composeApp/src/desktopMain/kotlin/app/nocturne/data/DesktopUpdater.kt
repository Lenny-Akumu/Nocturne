package app.nocturne.data

import app.nocturne.downloadFile
import app.nocturne.isWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import kotlin.system.exitProcess

actual suspend fun downloadAndInstall(url: String, fileName: String, onProgress: (Float) -> Unit) {
    val file = File(File(System.getProperty("java.io.tmpdir"), "nocturne-update"), fileName)
    withContext(Dispatchers.IO) { downloadFile(url, file, onProgress) }
    if (isWindows) {
        // stays wherever the user moved it (the installer defaults to %LOCALAPPDATA%\Programs)
        val dir = appInstallDir()?.let { "/DIR=$it" }
        ProcessBuilder(listOfNotNull(file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS", dir)).start()
        exitProcess(0)
    } else if (file.name.endsWith(".tar.gz")) {
        installLinux(file)
    } else {
        Desktop.getDesktop().open(file)
    }
}

private object Tray {
    private var icon: java.awt.TrayIcon? = null

    fun show(title: String, text: String) {
        if (!java.awt.SystemTray.isSupported()) return
        val tray = java.awt.SystemTray.getSystemTray()
        val tIcon = icon ?: run {
            val image = app.nocturne.AppIcon.forSize(tray.trayIconSize.width)
                ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            java.awt.TrayIcon(image, "Nocturne").apply {
                isImageAutoSize = true
                tray.add(this)
                icon = this
            }
        }
        app.nocturne.AppIcon.forSize(tray.trayIconSize.width)?.let { tIcon.image = it }
        tIcon.displayMessage(title, text, java.awt.TrayIcon.MessageType.INFO)
    }
}

actual fun notifyUpdateAvailable(version: String, notes: String) {
    runCatching { Tray.show(app.nocturne.i18n.t("Nocturne %s is available", version), app.nocturne.i18n.t("Open Nocturne and click Update to install it.")) }
}

actual fun notifyUpdateProgress(version: String, progress: Float?) = Unit

actual fun notifyRelease(title: String, text: String) {
    runCatching { Tray.show(title, text) }
}

actual fun isUpdateAsset(name: String): Boolean =
    if (isWindows) name.contains("Setup", ignoreCase = true) && name.endsWith(".exe")
    else name.endsWith("-linux-x64.tar.gz")

// Unpacks next to the running copy, swaps it in once we exit, then starts the new one.
private fun installLinux(archive: File) {
    val bin = ProcessHandle.current().info().command().orElse(null)?.let(::File) ?: error("Can't find the running app")
    val root = bin.parentFile?.parentFile ?: error("Unexpected install layout")
    val stage = File(root.parentFile, ".nocturne-update").apply { deleteRecursively(); mkdirs() }
    val tar = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", stage.absolutePath).inheritIO().start()
    if (tar.waitFor() != 0) error("Couldn't unpack the update")
    val fresh = stage.listFiles()?.firstOrNull { it.isDirectory } ?: error("Empty update")
    val pid = ProcessHandle.current().pid()
    val script = """
        while kill -0 $pid 2>/dev/null; do sleep 0.3; done
        rm -rf '${root.absolutePath}.old'
        mv '${root.absolutePath}' '${root.absolutePath}.old' && mv '${fresh.absolutePath}' '${root.absolutePath}' && rm -rf '${root.absolutePath}.old' '${stage.absolutePath}'
        nohup '${File(root, "bin/" + bin.name).absolutePath}' >/dev/null 2>&1 &
    """.trimIndent()
    ProcessBuilder("sh", "-c", script).start()
    exitProcess(0)
}
