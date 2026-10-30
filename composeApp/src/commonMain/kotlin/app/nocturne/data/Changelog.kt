package app.nocturne.data

// Shown once after an update (newest first). Ukrainian for "uk", English for every other language.
data class ChangelogEntry(val version: String, val en: List<String>, val uk: List<String>)

val Changelog = listOf(
    ChangelogEntry(
        "1.10.7",
        en = listOf(
            "What's playing? Tap the sound icon in Search and Nocturne recognises the song around you",
            "Lyrics translation: the translate button above the lyrics shows each line in your language",
            "Last.fm scrobbling (Accounts)",
            "PC: previous / play / next buttons under the window thumbnail on the taskbar",
            "Smart playlists with your own rules, plus ready-made ones (Library)",
            "Profile: tap your picture for your achievements and listening stats",
            "Achievements: 18 of them, some secret",
            "Podcasts: search, subscribe, resume where you stopped, separate speed (Library)",
            "Time capsule on Home: what you played a year (or a month) ago this week",
            "Blocked videos (age-restricted, region-locked) now play the same song from another upload or SoundCloud",
            "Home: the Recommended header is no longer drawn under its tracks",
        ),
        uk = listOf(
            "Що це грає? Натисни іконку звуку в Пошуку, і Nocturne розпізнає пісню навколо",
            "Переклад текстів: кнопка перекладу над текстом показує кожен рядок твоєю мовою",
            "Заблоковані відео (вікове обмеження, регіон) тепер грають ту саму пісню з іншого завантаження або SoundCloud",
            "Головна: заголовок «Рекомендовано» більше не залазить під треки",
            "Скроблінг у Last.fm (Акаунти)",
            "ПК: кнопки попередній / пауза / наступний під мініатюрою вікна в панелі задач",
            "Розумні плейлисти з власними правилами та готові варіанти (Бібліотека)",
            "Профіль: натисни на своє фото, щоб побачити досягнення й статистику",
            "Досягнення: 18 штук, деякі секретні",
            "Подкасти: пошук, підписки, продовження з місця зупинки, окрема швидкість (Бібліотека)",
            "Капсула часу на головній: що ти слухав рік (або місяць) тому цього тижня",
        ),
    ),
    ChangelogEntry(
        "1.10.6.1",
        en = listOf(
            "PC: hotkeys on single keys (numpad, F-keys) take the key over: it no longer types its character",
            "PC: single-key hotkeys work system-wide too when \"Work while minimized\" is on",
            "PC: floating player opening both ways: like, dislike and open on the left, playback and volume on the right",
            "PC: vertical floating player: hover the speaker for a volume slider, drag the ring around the cover to seek",
            "PC, iPhone: animated GIF backgrounds actually animate",
            "New Windows design \"Aurora\": floating dock, cover-tinted glow, player island; on phones the mini player and tabs share one island",
            "PC: vertical floating player shows the track title in a bubble next to the cover",
            "PC: window buttons no longer cover the expanded player's icons",
            "PC: headphone, keyboard and Bluetooth media buttons work everywhere while something is playing",
            "Aurora: dock highlight sits right under its icon, moods got proper icons, see-through panels on Home",
        ),
        uk = listOf(
            "ПК: гарячі клавіші на одній кнопці (нампад, F-клавіші) перехоплюють її: символ більше не вводиться",
            "ПК: гарячі клавіші на одній кнопці працюють і в усій системі, коли увімкнено «Працювати, коли Nocturne згорнутий»",
            "ПК: плаваючий плеєр, що розкривається в обидва боки: лайк, дизлайк і «відкрити» зліва, керування й гучність справа",
            "ПК: вертикальний плаваючий плеєр: наведи на динамік для повзунка гучності, тягни кільце навколо обкладинки для перемотки",
            "ПК, iPhone: анімовані GIF-фони тепер справді рухаються",
            "Новий дизайн Windows «Аврора»: плаваючий док, сяйво в кольорах обкладинки, плеєр-острів; на телефонах мініплеєр і вкладки в одному острові",
            "ПК: вертикальний плаваючий плеєр показує назву треку в бульбашці біля обкладинки",
            "ПК: кнопки вікна більше не перекривають іконки розгорнутого плеєра",
            "ПК: медіакнопки навушників, клавіатури й Bluetooth-пристроїв працюють будь-де, поки щось грає",
            "Аврора: підсвітка в доку рівно під іконкою, настрої з нормальними іконками, напівпрозорі блоки на головній",
        ),
    ),
    ChangelogEntry(
        "1.10.5",
        en = listOf(
            "PC: minimize / restore animates the actual window, seamlessly",
            "PC: the floating player takes over smoothly from the minimize animation",
            "PC: the frameless window can be dragged by the top edge and resized from any side again",
            "PC: no light strip along the window edge",
            "PC: the floating player now shows up when you switch to another window",
            "Phones: falling leaves and snow stop above the track list on Home",
        ),
        uk = listOf(
            "ПК: згортання й розгортання анімує саме вікно, безшовно",
            "ПК: плаваючий плеєр плавно підхоплює анімацію згортання",
            "ПК: вікно без рамки знову тягається за верх і змінює розмір з будь-якого боку",
            "ПК: прибрано світлу смугу по краю вікна",
            "ПК: плаваючий плеєр тепер з'являється, коли перемикаєшся на інше вікно",
            "Телефони: листя й сніг на головній закінчуються над списком треків",
        ),
    ),
    ChangelogEntry(
        "1.10.4",
        en = listOf(
            "PC: Mica / Acrylic transparency now switches on at launch",
            "PC: window buttons float over the content, like Windows 11 apps",
            "PC: the floating player hides while the app is in front and pops in when you switch away",
            "PC: smoother minimize / restore morph with the song's artwork",
            "PC: desktop lyrics widget in a screen corner",
            "Fluent icons (can be switched back to Material)",
            "Moods on Home: Chill, Focus, Workout, Night drive and more, built from your taste",
            "Listen together: chat, flying emoji reactions and everyone's nicknames",
            "Custom app background: any image or animated GIF, with blur",
            "Nickname in Accounts, next to your profile picture",
            "Aura visualizer and a live video player background",
            "Stats: activity chart, listening clock, day streak, new artists",
            "Crossfade works properly; sleep timer fades out; wake-up alarm",
            "Linux: media keys and panel controls (MPRIS)",
        ),
        uk = listOf(
            "ПК: прозорість Mica / Acrylic тепер вмикається при запуску",
            "ПК: кнопки вікна лежать поверх вмісту, як у застосунках Windows 11",
            "ПК: плаваючий плеєр ховається, коли застосунок попереду, і з'являється, коли перемикаєшся",
            "ПК: плавніша анімація згортання й розгортання з обкладинкою треку",
            "ПК: віджет тексту пісні в куті екрана",
            "Іконки Fluent (можна повернути Material)",
            "Настрої на головній: Чіл, Фокус, Тренування, Нічна поїздка та інші, зібрані під твій смак",
            "Слухати разом: чат, летючі емодзі-реакції та ніки всіх учасників",
            "Свій фон застосунку: будь-яке зображення або анімований GIF з розмиттям",
            "Нік в Акаунтах, поруч з фото профілю",
            "Візуалізатор «Аура» та живе відео на фоні плеєра",
            "Статистика: графік активності, годинник слухання, серія днів, нові виконавці",
            "Кросфейд працює як слід; таймер сну плавно затухає; будильник",
            "Linux: медіа-клавіші та керування з панелі (MPRIS)",
        ),
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
