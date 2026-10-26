package app.nocturne.data

// Shown once after an update (newest first). Ukrainian for "uk", English for every other language.
data class ChangelogEntry(val version: String, val en: List<String>, val uk: List<String>)

val Changelog = listOf(
    ChangelogEntry(
        "1.9.7",
        en = listOf(
            "“What's new” after every update",
            "Language picker right in Settings",
            "Clearer Spotify errors: tells you exactly how to fix access (403)",
            "Custom equalizer presets: save your curve and rename it (hold a preset)",
            "Secret Extra Bass Boost: hold the Bass Boost chip in the equalizer",
        ),
        uk = listOf(
            "Вікно «Що нового» після кожного оновлення",
            "Вибір мови прямо в Налаштуваннях",
            "Зрозуміліші помилки Spotify: підказка, як виправити доступ (403)",
            "Власні пресети еквалайзера: збережіть свою криву й перейменуйте її (затисніть пресет)",
            "Прихований Extra Bass Boost: затисніть Bass Boost в еквалайзері",
        ),
    ),
    ChangelogEntry(
        "1.9.6",
        en = listOf(
            "Windows: Mica, Mica Alt and Acrylic window effects work",
            "Keyboard shortcuts everywhere: Space, arrows, M, L, S, R, F",
            "Floating player hides behind the screen edge and comes back with a tab",
        ),
        uk = listOf(
            "Windows: працюють ефекти вікна Mica, Mica Alt і Acrylic",
            "Клавіші всюди: пробіл, стрілки, M, L, S, R, F",
            "Плаваючий плеєр ховається за край екрана і повертається язичком",
        ),
    ),
    ChangelogEntry(
        "1.9.5",
        en = listOf(
            "Widgets in every size and three styles, iPhone lock screen widgets",
            "Vertical floating player on PC",
        ),
        uk = listOf(
            "Віджети будь-якого розміру в трьох стилях, віджети на екрані блокування iPhone",
            "Вертикальний плаваючий плеєр на ПК",
        ),
    ),
)

private fun parts(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }

fun versionNewer(a: String, b: String): Boolean {
    val x = parts(a)
    val y = parts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}
