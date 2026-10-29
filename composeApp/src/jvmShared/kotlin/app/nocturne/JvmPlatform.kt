package app.nocturne

import java.io.File
import java.time.LocalTime

object AppDirs {
    lateinit var root: File
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun currentHour(): Int = LocalTime.now().hour

actual fun currentMonthDay(): Pair<Int, Int> = java.time.LocalDate.now().let { it.monthValue to it.dayOfMonth }

actual fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

actual object FileStore {
    private val lock = Any()

    private fun file(name: String) = File(AppDirs.root, name)

    actual fun read(name: String): String? = synchronized(lock) {
        file(name).takeIf { it.exists() }?.readText()
    }

    actual fun write(name: String, content: String) {
        synchronized(lock) {
            AppDirs.root.mkdirs()
            val tmp = File(AppDirs.root, "$name.tmp")
            tmp.writeText(content)
            val target = file(name)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        }
    }

    actual fun delete(name: String) {
        synchronized(lock) { file(name).delete() }
    }
}

actual fun sha1Hex(text: String): String =
    java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

actual fun sha256(bytes: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { java.security.SecureRandom().nextBytes(it) }

fun downloadFile(url: String, target: File, onProgress: (Float) -> Unit) {
    target.parentFile?.mkdirs()
    val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
    conn.instanceFollowRedirects = true
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    conn.setRequestProperty("User-Agent", "Nocturne/${BuildInfo.VERSION}")
    try {
        if (conn.responseCode !in 200..299) error("Download failed: HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        val tmp = File(target.path + ".part")
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        target.delete()
        if (!tmp.renameTo(target)) error("Can't save the update")
    } finally {
        conn.disconnect()
    }
}

actual fun systemLocale(): Pair<String, String> = java.util.Locale.getDefault().let { it.language.ifBlank { "en" } to it.country.ifBlank { "US" } }

actual fun aesEncrypt(key: ByteArray, plain: ByteArray): ByteArray {
    val iv = secureRandomBytes(12)
    val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
    return iv + c.doFinal(plain)
}

actual fun aesDecrypt(key: ByteArray, data: ByteArray): ByteArray {
    val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, data, 0, 12))
    return c.doFinal(data, 12, data.size - 12)
}
