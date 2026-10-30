package app.nocturne.data

import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlinx.serialization.Serializable

@Serializable
enum class RuleField(val label: String) {
    LIKED("Liked"), PLAYS("Times played"), LAST_PLAYED("Last played, days ago"), PLAYED_WITHIN("Played in the last days"),
    ARTIST("Artist"), TITLE("Title"), SOURCE("Source"), DURATION("Length, minutes"), NIGHT("Mostly played at night"),
}

@Serializable
enum class RuleOp(val label: String) { IS("is"), NOT("is not"), MORE("more than"), LESS("less than"), CONTAINS("contains") }

@Serializable
data class Rule(val field: RuleField, val op: RuleOp, val value: String = "")

@Serializable
data class SmartPlaylist(
    val id: String,
    val name: String,
    val rules: List<Rule>,
    val matchAll: Boolean = true,
    val limit: Int = 100,
    val sort: String = "plays",
)

object SmartPlaylists {
    private const val DAY = 86_400_000L

    fun opsFor(f: RuleField): List<RuleOp> = when (f) {
        RuleField.LIKED, RuleField.NIGHT, RuleField.PLAYED_WITHIN -> listOf(RuleOp.IS, RuleOp.NOT)
        RuleField.ARTIST, RuleField.TITLE -> listOf(RuleOp.CONTAINS, RuleOp.NOT)
        RuleField.SOURCE -> listOf(RuleOp.IS, RuleOp.NOT)
        else -> listOf(RuleOp.MORE, RuleOp.LESS)
    }

    fun evaluate(p: SmartPlaylist, lib: LibraryData, now: Long): List<Track> {
        val pool = (lib.liked + lib.history.map { it.track }).distinctBy { it.key }
        val likedKeys = lib.liked.mapTo(HashSet()) { it.key }
        val playsBy = lib.plays.groupBy { it.k }
        val lastPlayed = lib.history.associate { it.track.key to it.playedAt }
        fun match(t: Track, r: Rule): Boolean {
            val num = r.value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            fun cmp(x: Double) = if (r.op == RuleOp.MORE) x > num else x < num
            val txt = r.value.trim().lowercase()
            return when (r.field) {
                RuleField.LIKED -> (t.key in likedKeys) == (r.op == RuleOp.IS)
                RuleField.PLAYS -> cmp((lib.playCounts[t.key] ?: 0).toDouble())
                RuleField.LAST_PLAYED -> cmp(((now - (lastPlayed[t.key] ?: 0L)) / DAY).toDouble())
                RuleField.PLAYED_WITHIN -> playsBy[t.key].orEmpty().any { it.t >= now - num.toLong() * DAY } == (r.op == RuleOp.IS)
                RuleField.ARTIST -> (t.artistNames.lowercase().contains(txt)) == (r.op != RuleOp.NOT)
                RuleField.TITLE -> (t.title.lowercase().contains(txt)) == (r.op != RuleOp.NOT)
                RuleField.SOURCE -> (t.source.name.equals(r.value, true) || t.source.label.equals(r.value, true)) == (r.op == RuleOp.IS)
                RuleField.DURATION -> cmp(t.durationMs / 60_000.0)
                RuleField.NIGHT -> {
                    val ev = playsBy[t.key].orEmpty()
                    val night = ev.count { val h = ((it.t / 3_600_000L) % 24); h >= 21 || h <= 4 }
                    (ev.isNotEmpty() && night * 2 > ev.size) == (r.op == RuleOp.IS)
                }
            }
        }
        val hits = pool.filter { t ->
            if (p.rules.isEmpty()) true
            else if (p.matchAll) p.rules.all { match(t, it) } else p.rules.any { match(t, it) }
        }
        val sorted = when (p.sort) {
            "recent" -> hits.sortedByDescending { lastPlayed[it.key] ?: 0L }
            "random" -> hits.shuffled()
            "title" -> hits.sortedBy { it.title.lowercase() }
            else -> hits.sortedByDescending { lib.playCounts[it.key] ?: 0 }
        }
        return sorted.take(p.limit)
    }

    val sources: List<Source> get() = Source.entries
}
