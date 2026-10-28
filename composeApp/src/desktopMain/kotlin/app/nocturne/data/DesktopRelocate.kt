package app.nocturne.data

import app.nocturne.BuildInfo
import app.nocturne.downloadFile
import app.nocturne.i18n.t
import app.nocturne.isWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.system.exitProcess

actual fun pickFolder(title: String): String? {
    val laf = UIManager.getLookAndFeel()
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    try {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(app.nocturne.MainWindow.window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
    } finally {
        runCatching { UIManager.setLookAndFeel(laf) }
    }
}

/** Windows: folder with Nocturne.exe; Linux: the app image root (bin/.. ). */
actual fun appInstallDir(): String? {
    val exe = ProcessHandle.current().info().command().orElse(null)?.let(::File) ?: return null
    return when {
        isWindows && exe.name.equals("Nocturne.exe", ignoreCase = true) -> exe.parentFile?.absolutePath
        !isWindows && exe.name == "Nocturne" && exe.parentFile?.name == "bin" -> exe.parentFile.parentFile?.absolutePath
        else -> null
    }
}

actual suspend fun relocateApp(target: String, onStatus: (String) -> Unit) {
    val current = File(appInstallDir() ?: error(t("Can't find where Nocturne is installed")))
    val base = File(target)
    val dest = if (base.name.equals("Nocturne", ignoreCase = true)) base else File(base, "Nocturne")
    if (dest.canonicalPath == current.canonicalPath) return
    require(!dest.canonicalPath.startsWith(current.canonicalPath + File.separator)) { t("Pick a folder outside the current one") }
    val pid = ProcessHandle.current().pid()
    if (isWindows) {
        onStatus(t("Downloading installer…"))
        val v = BuildInfo.VERSION
        val setup = File(File(System.getProperty("java.io.tmpdir"), "nocturne-update"), "Nocturne-Setup-$v.exe")
        withContext(Dispatchers.IO) {
            downloadFile("https://github.com/${BuildInfo.REPO}/releases/download/v$v/Nocturne-Setup-$v.exe", setup) { p -> onStatus(t("Downloading installer…") + " ${(p * 100).toInt()}%") }
        }
        onStatus(t("Installing…"))
        fun q(s: String) = s.replace("'", "''")
        val oldExe = File(current, "Nocturne.exe")
        val script = """
            Wait-Process -Id $pid -ErrorAction SilentlyContinue
            ${'$'}p = Start-Process -FilePath '${q(setup.absolutePath)}' -ArgumentList '/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/CLOSEAPPLICATIONS','/DIR="${q(dest.absolutePath)}"' -PassThru -Wait
            if (${'$'}p.ExitCode -eq 0 -and (Test-Path '${q(File(dest, "Nocturne.exe").absolutePath)}')) {
                if (Test-Path '${q(oldExe.absolutePath)}') { Remove-Item -LiteralPath '${q(current.absolutePath)}' -Recurse -Force -ErrorAction SilentlyContinue }
                Start-Process '${q(File(dest, "Nocturne.exe").absolutePath)}'
            } else { Start-Process '${q(oldExe.absolutePath)}' }
        """.trimIndent()
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-EncodedCommand", encoded).start()
        exitProcess(0)
    } else {
        onStatus(t("Moving…"))
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
        val home = System.getProperty("user.home")
        val data = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        val script = """
            while kill -0 $pid 2>/dev/null; do sleep 0.3; done
            mkdir -p ${q(dest.parent)}
            if mv ${q(current.absolutePath)} ${q(dest.absolutePath)}; then
              [ -L ${q("$home/.local/bin/nocturne")} ] && ln -sf ${q("${dest.absolutePath}/bin/Nocturne")} ${q("$home/.local/bin/nocturne")}
              f=${q("$data/applications/nocturne.desktop")}
              [ -f "${'$'}f" ] && sed -i "s#^Exec=.*#Exec=${dest.absolutePath}/bin/Nocturne %u#" "${'$'}f"
              nohup ${q("${dest.absolutePath}/bin/Nocturne")} >/dev/null 2>&1 &
            else
              nohup ${q("${current.absolutePath}/bin/Nocturne")} >/dev/null 2>&1 &
            fi
        """.trimIndent()
        ProcessBuilder("sh", "-c", script).start()
        exitProcess(0)
    }
}
