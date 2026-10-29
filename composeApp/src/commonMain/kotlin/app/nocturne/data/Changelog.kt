package app.nocturne.data

// Shown once after an update (newest first). Ukrainian for "uk", English for every other language.
data class ChangelogEntry(val version: String, val en: List<String>, val uk: List<String>)

val Changelog = listOf(
    ChangelogEntry(
        "1.10.4",
        en = listOf("PC: Mica / Acrylic transparency now switches on at launch"),
        uk = listOf("ПК: прозорість Mica / Acrylic тепер вмикається при запуску"),
    ),
    ChangelogEntry(
        "1.10.3",
        en = listOf(
            "PC: fixed a crash on start with Mica / Acrylic",
            "Spotify works without the Web API: charts on Home, playlists and albums from links",
        ),
        uk = listOf(
            "ПК: виправлено виліт при запуску з Mica / Acrylic",
            "Spotify без Web API: чарти на головній, плейлисти й альбоми за посиланням",
        ),
    ),
    ChangelogEntry(
        "1.10.2",
        en = listOf(
            "PC: Mica / Acrylic transparency finally works",
            "PC: cleaner frameless title bar and rounder window corners",
            "PC: the window shrinks into the floating player when minimized and grows out of it on restore",
            "iPhone: updates install right from the app via TrollStore, SideStore or AltStore",
            "Fixed the Discord icon in About",
        ),
        uk = listOf(
            "ПК: прозорість Mica / Acrylic нарешті працює",
            "ПК: охайніший заголовок без рамки й більш заокруглені кути вікна",
            "ПК: вікно згортається в плаваючий плеєр і виростає з нього при відкритті",
            "iPhone: оновлення ставляться прямо з застосунку через TrollStore, SideStore або AltStore",
            "Виправлено іконку Discord у «Про застосунок»",
        ),
    ),
    ChangelogEntry(
        "1.10.1",
        en = listOf(
            "Halloween app icon: pick it in Appearance → App icon (until 1 November)",
            "A cuter, clearer voice when you tap the logo 10 times",
        ),
        uk = listOf(
            "Хелловінська іконка застосунку: оберіть її в Оформлення → Іконка (до 1 листопада)",
            "Миліший і зрозуміліший голос, якщо натиснути логотип 10 разів",
        ),
    ),
    ChangelogEntry(
        "1.10.0",
        en = listOf(
            "Linux version with a one-command installer",
            "Frameless window on PC: window buttons live in a frosted capsule",
            "Startup animation with a short chime (can be turned off)",
            "Seasonal effects on Home: autumn leaves, snow, spring leaves, summer sun rays",
            "Tap the logo to jump Home and refresh it (and try tapping it 10 times)",
            "Halloween icon from 28 October to 1 November",
            "Smooth list animations: removed tracks fade out and the queue slides up",
            "Pick the downloads folder and move the app to another drive",
            "Comments load from several sources at once and no longer spin forever",
            "Listen Together: guests no longer jump to their own queue",
            "iPhone: fixed the time bar drifting in Speed up / Slowed",
            "Player keys no longer fire while typing",
            "Smoother floating player when it opens to the left",
            "App icon in the Android media notification",
            "Credits in About",
        ),
        uk = listOf(
            "Версія для Linux з установкою однією командою",
            "Вікно без рамки на ПК: кнопки вікна в розмитій капсулі",
            "Анімація запуску з коротким музичним звуком (можна вимкнути)",
            "Сезонні ефекти на головній: осіннє листя, сніг, весняне листя, літні промені",
            "Натисніть логотип, щоб перейти на головну й оновити її (і спробуйте натиснути 10 разів)",
            "Хелловінська іконка з 28 жовтня по 1 листопада",
            "Плавні анімації списків: видалений трек зникає, а черга підтягується вгору",
            "Вибір папки завантажень і перенесення застосунку на інший диск",
            "Коментарі вантажаться з кількох джерел одночасно й більше не крутяться вічно",
            "Слухати разом: гості більше не перемикаються на свою чергу",
            "iPhone: виправлено розсинхрон смуги часу в Speed up / Slowed",
            "Клавіші плеєра не спрацьовують під час введення тексту",
            "Плавніший плаваючий плеєр при розгортанні вліво",
            "Іконка застосунку в медіа-сповіщенні Android",
            "Автори в «Про застосунок»",
        ),
    ),
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
