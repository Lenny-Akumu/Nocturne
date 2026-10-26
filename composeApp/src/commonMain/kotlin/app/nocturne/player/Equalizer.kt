package app.nocturne.player

import kotlin.math.ln

object Eq {
    val bands = listOf(60f, 150f, 400f, 1000f, 2400f, 15000f)
    const val MAX_DB = 12f

    val presets: Map<String, List<Float>> = linkedMapOf(
        "Default" to listOf(0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(7f, 5f, 1f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 1f, 4f, 7f),
        "Vocal" to listOf(-2f, -1f, 2f, 4f, 3f, 0f),
        "Rock" to listOf(5f, 3f, -1f, 1f, 3f, 5f),
        "Pop" to listOf(-1f, 1f, 3f, 3f, 1f, -1f),
        "Electronic" to listOf(5f, 3f, 0f, -1f, 2f, 5f),
        "Hip-Hop" to listOf(6f, 4f, 0f, 1f, 1f, 2f),
        "Jazz" to listOf(3f, 2f, -1f, 1f, 2f, 3f),
        "Classical" to listOf(3f, 2f, 0f, 0f, 2f, 4f),
        "Loudness" to listOf(5f, 2f, 0f, 0f, 2f, 5f),
    )

    const val EXTRA_BASS = "Extra Bass Boost"
    val extraBass = listOf(12f, 10f, 3f, 0f, 0f, 0f)

    fun gainAt(freq: Float, gains: List<Float>): Float {
        if (gains.size != bands.size) return 0f
        if (freq <= bands.first()) return gains.first()
        if (freq >= bands.last()) return gains.last()
        val i = bands.indexOfLast { it <= freq }
        val f0 = ln(bands[i]); val f1 = ln(bands[i + 1])
        val t = ((ln(freq) - f0) / (f1 - f0)).coerceIn(0f, 1f)
        return gains[i] + (gains[i + 1] - gains[i]) * t
    }
}

enum class TrackVersion(val label: String, val speed: Float) {
    ORIGINAL("Original", 1f),
    SPEED_UP("Speed Up", 1.25f),
    SLOWED("Slowed", 0.8f),
}
