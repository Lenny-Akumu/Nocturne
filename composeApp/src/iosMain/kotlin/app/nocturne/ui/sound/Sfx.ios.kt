package app.nocturne.ui.sound

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

private val playing = mutableListOf<AVAudioPlayer>()

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual fun playClip(name: String, wav: ByteArray, volume: Float) {
    val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
    val p = AVAudioPlayer(data = data, error = null)
    p.volume = volume
    playing.removeAll { !it.playing }
    playing += p
    p.play()
}
