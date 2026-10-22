package app.nocturne

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Windows: points the installer's Start menu / desktop / pinned taskbar shortcuts at the chosen app icon
 * (an .ico written to the app data folder) or back at Nocturne.exe for the default one.
 */
internal object ShellIcon {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private interface Shell32 : Library {
        fun SHChangeNotify(eventId: Int, flags: Int, item1: WString?, item2: Pointer?)
    }

    @Synchronized
    fun sync(key: String, isDefault: Boolean, images: List<BufferedImage>) {
        if (!windows || !isDefault && images.isEmpty()) return
        runCatching {
            val exe = ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith("Nocturne.exe", ignoreCase = true) } ?: return
            val dir = AppDirs.root
            val stamp = File(dir, "shell-icon.txt")
            // re-applied after updates too: the installer recreates the shortcuts with the default icon
            val state = "$key|${File(exe).lastModified()}"
            val last = stamp.takeIf { it.exists() }?.readText()
            if (last == state || last == null && isDefault) return stamp.writeText(state)
            val location = if (isDefault) "$exe,0" else {
                // a new file name per design, since Explorer caches icons by path
                val ico = File(dir, "icon-${key.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.ico")
                if (!ico.exists()) ico.writeBytes(ico(images))
                dir.listFiles { f -> f.name.startsWith("icon-") && f.name.endsWith(".ico") && f != ico }?.forEach { it.delete() }
                ico.absolutePath + ",0"
            }
            if (isDefault) dir.listFiles { f -> f.name.startsWith("icon-") && f.name.endsWith(".ico") }?.forEach { it.delete() }
            val changed = relink(location)
            stamp.writeText(state)
            refresh(changed)
        }
    }

    private fun relink(location: String): List<String> {
        val q = location.replace("'", "''")
        val script = """
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            ${'$'}sh = New-Object -ComObject WScript.Shell
            ${'$'}icon = '$q'
            ${'$'}paths = @(
              (Join-Path ([Environment]::GetFolderPath('Programs')) 'Nocturne\Nocturne.lnk'),
              (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Nocturne.lnk'),
              (Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'Nocturne\Nocturne.lnk'),
              (Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) 'Nocturne.lnk'),
              (Join-Path ${'$'}env:APPDATA 'Microsoft\Internet Explorer\Quick Launch\User Pinned\TaskBar\Nocturne.lnk')
            )
            foreach (${'$'}p in ${'$'}paths) {
              if (Test-Path -LiteralPath ${'$'}p) {
                ${'$'}s = ${'$'}sh.CreateShortcut(${'$'}p)
                if (${'$'}s.TargetPath -like '*Nocturne.exe' -and ${'$'}s.IconLocation -ne ${'$'}icon) {
                  ${'$'}s.IconLocation = ${'$'}icon
                  try { ${'$'}s.Save(); Write-Output ${'$'}p } catch {}
                }
              }
            }
        """.trimIndent()
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readLines()
        if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly()
        return out.map { it.trim() }.filter { it.endsWith(".lnk", ignoreCase = true) }
    }

    private fun refresh(paths: List<String>) {
        runCatching {
            val shell = Native.load("shell32", Shell32::class.java)
            paths.forEach { shell.SHChangeNotify(SHCNE_UPDATEITEM, SHCNF_PATHW or SHCNF_FLUSH, WString(it), null) }
            shell.SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_FLUSH, null, null)
        }
        runCatching { ProcessBuilder("ie4uinit.exe", "-show").start().waitFor(10, TimeUnit.SECONDS) }
    }

    /** Multi-size .ico with PNG-compressed entries (Vista+). */
    fun ico(images: List<BufferedImage>): ByteArray {
        val list = images.filter { it.width in 1..256 }.distinctBy { it.width }.sortedBy { it.width }
        val pngs = list.map { img -> ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "png", it) }.toByteArray() }
        val head = ByteBuffer.allocate(6 + 16 * list.size).order(ByteOrder.LITTLE_ENDIAN)
        head.putShort(0).putShort(1).putShort(list.size.toShort())
        var offset = 6 + 16 * list.size
        list.forEachIndexed { i, img ->
            head.put((img.width and 0xFF).toByte()).put((img.height and 0xFF).toByte()) // 256 is stored as 0
            head.put(0).put(0).putShort(1).putShort(32)
            head.putInt(pngs[i].size).putInt(offset)
            offset += pngs[i].size
        }
        return ByteArrayOutputStream().apply { write(head.array()); pngs.forEach { write(it) } }.toByteArray()
    }

    private const val SHCNE_UPDATEITEM = 0x00002000
    private const val SHCNE_ASSOCCHANGED = 0x08000000
    private const val SHCNF_PATHW = 0x0005
    private const val SHCNF_FLUSH = 0x1000
}
