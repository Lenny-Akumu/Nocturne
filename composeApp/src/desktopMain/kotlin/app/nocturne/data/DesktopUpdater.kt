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
        ProcessBuilder(file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART").start()
        exitProcess(0)
    } else {
        Desktop.getDesktop().open(file)
    }
}
