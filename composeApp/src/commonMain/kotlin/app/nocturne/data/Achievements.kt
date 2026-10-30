package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.nowMillis
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class Achievement(val id: String, val emoji: String, val title: String, val hint: String, val secret: Boolean = false)

private fun fdiv(a: Long, b: Long): Long = (a - fmod(a, b)) / b
private fun fmod(a: Long, b: Long): Long = ((a % b) + b) % b

private fun tzOffsetMin(): Long {
    var d = app.nocturne.minuteOfDay() - (nowMillis() / 60_000L) % 1440
    if (d > 840) d -= 1440
    if (d < -720) d += 1440
    return d
}

private class Ctx(val lib: LibraryData, val off: Long) {
    fun hour(t: Long) = (((t / 60_000L + off) % 1440 + 1440) % 1440 / 60).toInt()
    fun minute(t: Long) = (((t / 60_000L + off) % 1440 + 1440) % 1440).toInt()
    fun day(t: Long) = fdiv(t / 60_000L + off, 1440L)
}

// days since 1970-01-01 -> (year, month, day); Howard Hinnant's civil_from_days
private fun civil(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
    val era = fdiv(z, 146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return Triple((yoe + era * 400 + if (m <= 2) 1 else 0).toInt(), m, d)
}

object Achievements {
    val all = listOf(
        Achievement("sleepwalker", "🌙", "Sleepwalker", "25 songs between 3 and 5 in the morning"),
        Achievement("timeloop", "🔁", "Time loop", "The same song 10 times in one day"),
        Achievement("archaeologist", "🏺", "Archaeologist", "Dig up a song you hadn't played for 90 days"),
        Achievement("marathon", "🏃", "Marathon", "4 hours of music in a single day"),
        Achievement("earlybird", "🐦", "Early bird", "Music before 7 am on 7 different days"),
        Achievement("ring", "💍", "Put a ring on it", "100 plays of one artist"),
        Achievement("nomad", "🧭", "Nomad", "50 different artists in one week"),
        Achievement("streak", "🔥", "Unbroken", "Listen every day for 30 days"),
        Achievement("chameleon", "🦎", "Chameleon", "YouTube, Spotify and SoundCloud on the same day"),
        Achievement("hoarder", "🐉", "Dragon's hoard", "500 liked songs"),
        Achievement("epic", "🧙", "Epic saga", "Finish a track longer than 20 minutes"),
        Achievement("rewind", "📼", "Rewind", "Play your very first song again after 6 months"),
        Achievement("friday13", "🖤", "Bad luck", "Listen on a Friday the 13th"),
        Achievement("newyear", "🎆", "First notes", "Play something in the first 5 minutes of the year"),
        Achievement("noon", "☀️", "High noon", "Start a song at exactly 12:00"),
        Achievement("sixseven", "🤸", "Six seven", "???", secret = true),
        Achievement("blackcat", "🐈‍⬛", "Black cat crossed your path", "???", secret = true),
        Achievement("together", "🫂", "Never alone", "Listen together with someone"),
    )

    private fun earned(c: Ctx): Set<String> {
        val p = c.lib.plays
        val out = mutableSetOf<String>()
        if (p.count { c.hour(it.t) in 3..4 } >= 25) out += "sleepwalker"
        if (p.groupingBy { c.day(it.t) to it.k }.eachCount().values.any { it >= 10 }) out += "timeloop"
        val byKey = p.groupBy { it.k }
        if (byKey.values.any { ev -> val s = ev.map { it.t }.sorted(); s.zipWithNext().any { (a, b) -> b - a >= 90 * DAY } }) out += "archaeologist"
        if (p.groupBy { c.day(it.t) }.values.any { d -> d.sumOf { it.d } >= 4 * 3_600_000L }) out += "marathon"
        if (p.filter { c.hour(it.t) in 4..6 }.map { c.day(it.t) }.toSet().size >= 7) out += "earlybird"
        val artists = p.mapNotNull { it.a.split(",").firstOrNull()?.trim()?.takeIf { a -> a.isNotEmpty() } }
        if (artists.groupingBy { it }.eachCount().values.any { it >= 100 }) out += "ring"
        if (p.groupBy { c.day(it.t) / 7 }.values.any { w -> w.mapNotNull { it.a.split(",").firstOrNull()?.trim() }.toSet().size >= 50 }) out += "nomad"
        val days = p.map { c.day(it.t) }.distinct().sorted()
        var run = 1
        for (i in 1 until days.size) { run = if (days[i] == days[i - 1] + 1) run + 1 else 1; if (run >= 30) { out += "streak"; break } }
        if (p.groupBy { c.day(it.t) }.values.any { d -> d.map { it.k.substringBefore(':') }.toSet().containsAll(listOf("YOUTUBE_MUSIC", "SPOTIFY", "SOUNDCLOUD")) }) out += "chameleon"
        if (c.lib.liked.size >= 500) out += "hoarder"
        if (p.any { it.d >= 20 * 60_000L }) out += "epic"
        p.minByOrNull { it.t }?.let { first -> if (p.any { it.k == first.k && it.t - first.t >= 182 * DAY }) out += "rewind" }
        if (p.any { val (_, _, d) = civil(c.day(it.t)); d == 13 && fmod(c.day(it.t) + 4, 7L) == 5L }) out += "friday13"
        if (p.any { val (_, m, d) = civil(c.day(it.t)); m == 1 && d == 1 && c.minute(it.t) < 5 }) out += "newyear"
        if (p.any { c.minute(it.t) == 720 }) out += "noon"
        return out
    }

    /** Event-only achievements (easter eggs, listening together). */
    fun unlock(id: String) {
        val graph = runCatching { AppGraph.instance }.getOrNull() ?: return
        if (id in graph.settings.value.achievements) return
        graph.settings.update { it.copy(achievements = it.achievements + (id to nowMillis())) }
        announce(id)
    }

    private fun announce(id: String) {
        val a = all.firstOrNull { it.id == id } ?: return
        AppGraph.instance.toast("${a.emoji} " + t("Achievement unlocked: %s", t(a.title)))
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start(graph: AppGraph) {
        graph.scope.launch {
            graph.library.state.distinctUntilChangedBy { it.plays.size to it.liked.size }.debounce(1500).collect { lib ->
                val have = graph.settings.value.achievements
                val fresh = earned(Ctx(lib, tzOffsetMin())) - have.keys
                if (fresh.isEmpty()) return@collect
                val now = nowMillis()
                graph.settings.update { it.copy(achievements = it.achievements + fresh.associateWith { now }) }
                // a first run shouldn't fire a dozen toasts
                if (fresh.size <= 2) fresh.forEach(::announce)
                else AppGraph.instance.toast("🏆 " + t("%s achievements unlocked", fresh.size))
            }
        }
    }

    private const val DAY = 86_400_000L
}
