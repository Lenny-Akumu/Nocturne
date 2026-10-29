package app.nocturne.ui.sound

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Plays a short WAV clip on top of whatever is playing. */
expect fun playClip(name: String, wav: ByteArray, volume: Float = 1f)

object Sfx {
    @OptIn(ExperimentalEncodingApi::class)
    private fun decode(parts: Array<String>) = Base64.decode(parts.joinToString(""))

    private val startup by lazy { decode(SfxData.startup) }
    private val voice by lazy { decode(SfxData.voice) }
    private val meow by lazy { decode(SfxData.meow) }

    fun startup() = runCatching { playClip("startup", startup, 0.7f) }
    fun voice() = runCatching { playClip("voice", voice, 1f) }
    fun meow() = runCatching { playClip("meow", meow, 0.55f) }
}
