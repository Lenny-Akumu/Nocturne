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

actual val patchListName: String? = if (isWindows) "Nocturne-files-windows.json" else "Nocturne-files-linux.json"

@kotlinx.serialization.Serializable
private class PatchFile(val p: String, val h: String, val s: Long, val u: String? = null, val x: Boolean = false)

@kotlinx.serialization.Serializable
private class PatchList(val version: String, val full: Long, val files: List<PatchFile>)

private fun sha256(f: File): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Compares the release's file list with the installed files, downloads only what differs (checked against
 * its hash), then swaps those files in once the app has exited and starts it again.
 * Returns false whenever the full installer is the safer way: unknown layout, files without a link, or most of the app changed.
 */
actual suspend fun applyPatch(listUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
    val root = appInstallDir()?.let(::File)?.takeIf { it.isDirectory } ?: return@withContext false
    val listFile = File(File(System.getProperty("java.io.tmpdir"), "nocturne-update"), "files.json")
    downloadFile(listUrl, listFile) {}
    val list = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(PatchList.serializer(), listFile.readText())
    val need = list.files.filter { e ->
        val f = File(root, e.p)
        !(f.isFile && f.length() == e.s && sha256(f) == e.h)
    }
    if (need.isEmpty() || need.any { it.u == null }) return@withContext false
    val bytes = need.sumOf { it.s }
    if (bytes > list.full * 6 / 10) return@withContext false

    val stage = if (isWindows) File(System.getProperty("java.io.tmpdir"), "nocturne-patch") else File(root.parentFile, ".nocturne-patch")
    stage.deleteRecursively()
    var done = 0L
    for (e in need) {
        val target = File(stage, e.p)
        downloadFile(e.u!!, target) { p -> onProgress(((done + e.s * p) / bytes).toFloat().coerceIn(0f, 1f)) }
        if (target.length() != e.s || sha256(target) != e.h) { stage.deleteRecursively(); error("Damaged download: ${e.p}") }
        if (e.x) target.setExecutable(true, false)
        done += e.s
    }
    onProgress(1f)
    swapAndRestart(root, stage)
    true
}

// The running app holds its files open, so a small script waits for it to exit, copies the new files over and starts it again.
private fun swapAndRestart(root: File, stage: File): Nothing {
    val pid = ProcessHandle.current().pid()
    if (isWindows) {
        val script = File(System.getProperty("java.io.tmpdir"), "nocturne-patch.cmd")
        script.writeText(
            """
            @echo off
            :wait
            tasklist /FI "PID eq $pid" 2>nul | find "$pid" >nul && (timeout /t 1 /nobreak >nul & goto wait)
            robocopy "${stage.absolutePath}" "${root.absolutePath}" /E /R:10 /W:1 /NFL /NDL /NJH /NJS /NP >nul
            rmdir /S /Q "${stage.absolutePath}"
            start "" "${File(root, "Nocturne.exe").absolutePath}"
            del "%~f0"
            """.trimIndent().replace("\n", "\r\n"),
        )
        ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.absolutePath).start()
    } else {
        val bin = File(root, "bin/Nocturne")
        val script = """
            while kill -0 $pid 2>/dev/null; do sleep 0.3; done
            cp -a '${stage.absolutePath}/.' '${root.absolutePath}/' && rm -rf '${stage.absolutePath}'
            chmod +x '${bin.absolutePath}'
            nohup '${bin.absolutePath}' >/dev/null 2>&1 &
        """.trimIndent()
        ProcessBuilder("sh", "-c", script).start()
    }
    exitProcess(0)
}
