package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.model.Track
import app.nocturne.player.VlcAudioEngine
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.ByteArrayOutputStream
import java.io.File

actual val exportLabel: String = "Save as MP3"

actual suspend fun exportAudio(track: Track, file: String, ext: String): String? {
    val name = cleanName(listOfNotNull(track.artistNames.takeIf { it.isNotBlank() }, track.title).joinToString(" - ")) + ".mp3"
    val target = pickTarget(name) ?: return null
    val factory = (AppGraph.instance.engine as? VlcAudioEngine)?.mediaFactory
    val mp3 = if (ext == "mp3") File(file).copyTo(target, overwrite = true).let { true }
    else factory != null && withContext(Dispatchers.IO) { transcode(factory, file, target) }
    if (!mp3) {
        // no encoder: keep the original audio next to where the user asked
        val keep = File(target.parentFile, target.nameWithoutExtension + "." + ext)
        File(file).copyTo(keep, overwrite = true)
        return keep.absolutePath
    }
    runCatching { writeTags(target, track) }
    return target.absolutePath
}

private fun cleanName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "track" }

private suspend fun pickTarget(name: String): File? = withContext(Dispatchers.IO) {
    var picked: File? = null
    javax.swing.SwingUtilities.invokeAndWait {
        val music = File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save as MP3", java.awt.FileDialog.SAVE)
        dialog.directory = music.absolutePath
        dialog.file = name
        dialog.isVisible = true
        val dir = dialog.directory
        val f = dialog.file
        if (dir != null && f != null) picked = File(dir, if (f.endsWith(".mp3", true)) f else "$f.mp3")
    }
    picked
}

/** libVLC re-encodes the file to 320 kbit/s MP3. */
private suspend fun transcode(factory: uk.co.caprica.vlcj.factory.MediaPlayerFactory, src: String, out: File): Boolean {
    val player = runCatching { factory.mediaPlayers().newMediaPlayer() }.getOrNull() ?: return false
    val done = CompletableDeferred<Boolean>()
    player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
        override fun finished(mediaPlayer: MediaPlayer) { done.complete(true) }
        override fun error(mediaPlayer: MediaPlayer) { done.complete(false) }
    })
    out.delete()
    val dst = out.absolutePath.replace("'", "\\'")
    val started = player.media().play(
        src,
        ":sout=#transcode{vcodec=none,acodec=mp3,ab=320,channels=2,samplerate=44100}:std{access=file,mux=raw,dst='$dst'}",
        ":sout-keep", ":no-sout-video", ":no-video",
    )
    val ok = started && (withTimeoutOrNull(15 * 60_000L) { done.await() } ?: false)
    runCatching { player.controls().stop() }
    runCatching { player.release() }
    return ok && out.length() > 10_000
}

/** ID3v2.3: title, artist, album and the cover, so other players show them. */
private suspend fun writeTags(file: File, track: Track) {
    val cover = track.artworkUrl?.let { url -> runCatching { AppGraph.instance.http.get(url).readRawBytes() }.getOrNull() }
    val frames = ByteArrayOutputStream()
    fun text(id: String, v: String?) {
        if (v.isNullOrBlank()) return
        val body = byteArrayOf(1) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + v.toByteArray(Charsets.UTF_16LE)
        frames.write(header(id, body.size)); frames.write(body)
    }
    text("TIT2", track.title)
    text("TPE1", track.artistNames)
    text("TALB", track.album)
    if (cover != null && cover.size > 100) {
        val mime = if (cover[0] == 0x89.toByte()) "image/png" else "image/jpeg"
        val body = byteArrayOf(0) + mime.toByteArray() + byteArrayOf(0, 3, 0) + cover
        frames.write(header("APIC", body.size)); frames.write(body)
    }
    val tags = frames.toByteArray()
    val n = tags.size
    val head = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
        ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())
    val audio = file.readBytes()
    file.writeBytes(head + tags + audio)
}

private fun header(id: String, size: Int) = id.toByteArray() + byteArrayOf(
    (size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte(), 0, 0,
)
