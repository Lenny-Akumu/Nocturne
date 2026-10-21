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
        ProcessBuilder(file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS").start()
        exitProcess(0)
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
            val image = runCatching {
                javax.imageio.ImageIO.read(Thread.currentThread().contextClassLoader.getResourceAsStream("nocturne.png"))
            }.getOrNull() ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            java.awt.TrayIcon(image, "Nocturne").apply {
                isImageAutoSize = true
                tray.add(this)
                icon = this
            }
        }
        tIcon.displayMessage(title, text, java.awt.TrayIcon.MessageType.INFO)
    }
}

actual fun notifyUpdateAvailable(version: String, notes: String) {
    runCatching { Tray.show("Nocturne $version is available", "Open Nocturne and click Update to install it.") }
}

actual fun notifyUpdateProgress(version: String, progress: Float?) = Unit

actual fun notifyRelease(title: String, text: String) {
    runCatching { Tray.show(title, text) }
}
