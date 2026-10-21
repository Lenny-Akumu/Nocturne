package app.nocturne.data

import app.nocturne.FileStore
import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64

actual object SecureStore {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    actual fun read(name: String): String? = runCatching {
        if (!windows) return null
        val raw = Base64.getDecoder().decode(FileStore.read("$name.enc") ?: return null)
        String(Crypt32Util.cryptUnprotectData(raw), Charsets.UTF_8)
    }.getOrNull()

    actual fun write(name: String, content: String): Boolean = runCatching {
        check(windows)
        val sealed = Crypt32Util.cryptProtectData(content.toByteArray(Charsets.UTF_8))
        FileStore.write("$name.enc", Base64.getEncoder().encodeToString(sealed))
    }.isSuccess
}
