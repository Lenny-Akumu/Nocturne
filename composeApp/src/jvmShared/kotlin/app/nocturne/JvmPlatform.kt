package app.nocturne

import java.io.File
import java.time.LocalTime

object AppDirs {
    lateinit var root: File
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun currentHour(): Int = LocalTime.now().hour

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
