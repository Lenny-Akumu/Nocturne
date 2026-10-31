package app.nocturne

import app.nocturne.data.Settings
import app.nocturne.player.PlayerState
import java.io.File

/** Linux: player modules in the Waybar top panel (Omarchy, Hyprland, Sway). */
object PanelWidget {
    val supported: Boolean = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)

    private const val SIGNAL = 11
    private const val BEGIN = "// nocturne:begin"
    private const val END = "// nocturne:end"
    private const val CSS_BEGIN = "/* nocturne:begin */"
    private const val CSS_END = "/* nocturne:end */"
    private const val GROUP = "\"group/nocturne\", "

    private val home = File(System.getProperty("user.home"))
    private val waybarDir: File get() = File(System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: File(home, ".config"), "waybar")
    private val dir: File get() = File(AppDirs.root, "waybar").apply { mkdirs() }
    private var installed: List<String>? = null
    private var last = ""

    private val buttons = listOf("prev", "play", "next", "like", "dislike", "volume", "open")

    fun available(): Boolean = supported && config() != null

    private fun config(): File? = listOf("config.jsonc", "config").map { File(waybarDir, it) }.firstOrNull { it.isFile }

    /** Adds or refreshes our modules; false when there's no Waybar config. */
    fun install(s: Settings): Boolean {
        val cfg = config() ?: return false
        val mods = listOf("title") + buttons.filter { it == "play" || it in s.floatingButtons }
        if (installed == mods && cfg.readText().contains(BEGIN)) return true
        writeScript()
        var text = strip(cfg.readText())
        if (!File(cfg.path + ".nocturne-bak").exists()) cfg.copyTo(File(cfg.path + ".nocturne-bak"))
        val first = text.indexOf('{').takeIf { it >= 0 } ?: return false
        text = text.substring(0, first + 1) + "\n" + block(mods) + text.substring(first + 1)
        val center = Regex("\"modules-center\"\\s*:\\s*\\[").find(text)
        text = if (center != null) text.substring(0, center.range.last + 1) + GROUP + text.substring(center.range.last + 1)
        else text.replaceFirst(BEGIN, "$BEGIN\n  \"modules-center\": [\"group/nocturne\"],")
        cfg.writeText(text)
        File(waybarDir, "style.css").takeIf { it.isFile }?.let { css ->
            css.writeText(stripCss(css.readText()) + "\n$CSS_BEGIN\n$STYLE\n$CSS_END\n")
        }
        installed = mods
        last = ""
        reload()
        return true
    }

    fun uninstall() {
        installed = null
        val cfg = config() ?: return
        val text = cfg.readText()
        if (!text.contains(BEGIN)) return
        cfg.writeText(strip(text))
        File(waybarDir, "style.css").takeIf { it.isFile }?.let { it.writeText(stripCss(it.readText())) }
        reload()
    }

    fun update(p: PlayerState, liked: Boolean) {
        val t = p.current
        val out = buildMap {
            put("title", if (t == null) "" else json(esc(t.title + " — " + t.artists.joinToString { it.name }), esc(t.title), if (p.isPlaying) "playing" else "paused"))
            put("prev", if (t == null) "" else json("\uF048"))
            put("play", if (t == null) "" else json(if (p.isPlaying) "\uF04C" else "\uF04B"))
            put("next", if (t == null) "" else json("\uF051"))
            put("like", if (t == null) "" else json(if (liked) "\uF004" else "\uF08A", cls = if (liked) "on" else ""))
            put("dislike", if (t == null) "" else json("\uF165"))
            put("volume", if (t == null) "" else json("\uF028"))
            put("open", if (t == null) "" else json("\uF08E"))
        }
        val key = out.values.joinToString("|")
        if (key == last) return
        last = key
        out.forEach { (k, v) -> File(dir, "$k.json").writeText(v) }
        signal()
    }

    fun command(op: String) {
        val g = AppGraph.instance
        val p = g.player
        val t = p.state.value.current
        when (op) {
            "prev" -> p.previous()
            "play" -> p.toggle()
            "next" -> p.next()
            "like" -> t?.let { g.toggleLike(it) }
            "dislike" -> t?.let { g.notInterested(it) }
            "fwd" -> p.seekTo(p.state.value.positionMs + 5000)
            "back" -> p.seekTo((p.state.value.positionMs - 5000).coerceAtLeast(0))
            "volup" -> p.setVolume((g.settings.value.volume + 0.05f).coerceAtMost(1f))
            "voldown" -> p.setVolume((g.settings.value.volume - 0.05f).coerceAtLeast(0f))
            "mute" -> p.setVolume(if (g.settings.value.volume > 0f) 0f else 0.6f)
            "open" -> MainWindow.show()
        }
    }

    fun clear() {
        runCatching { dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { it.writeText("") } }
        signal()
    }

    private fun json(text: String, tip: String = "", cls: String = "") =
        "{\"text\":\"$text\",\"tooltip\":\"$tip\",\"class\":\"$cls\"}"

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").let {
        if (it.length > 48) it.take(47) + "…" else it
    }

    private fun block(mods: List<String>): String {
        val ctl = File(dir, "ctl.sh").absolutePath
        val sb = StringBuilder("  $BEGIN\n")
        sb.append("  \"group/nocturne\": { \"orientation\": \"horizontal\", \"modules\": [")
        sb.append(mods.joinToString { "\"custom/nocturne-$it\"" }).append("] },\n")
        mods.forEach { m ->
            val file = File(dir, "$m.json").absolutePath
            sb.append("  \"custom/nocturne-$m\": { \"exec\": \"cat '$file' 2>/dev/null\", \"return-type\": \"json\", \"interval\": 5, \"signal\": $SIGNAL")
            when (m) {
                "title" -> sb.append(", \"max-length\": 48, \"on-click\": \"$ctl open\", \"on-scroll-up\": \"$ctl fwd\", \"on-scroll-down\": \"$ctl back\"")
                "volume" -> sb.append(", \"on-click\": \"$ctl mute\", \"on-scroll-up\": \"$ctl volup\", \"on-scroll-down\": \"$ctl voldown\"")
                else -> sb.append(", \"on-click\": \"$ctl $m\"")
            }
            sb.append(" },\n")
        }
        return sb.append("  $END").toString()
    }

    private fun strip(text: String): String {
        var t = text
        val a = t.indexOf(BEGIN)
        val b = t.indexOf(END)
        if (a >= 0 && b > a) t = t.substring(0, t.lastIndexOf('\n', a).coerceAtLeast(0)) + t.substring(b + END.length)
        return t.replace(GROUP, "").replace(Regex("\\n\\s*\"modules-center\": \\[\"group/nocturne\"],"), "")
    }

    private fun stripCss(text: String): String {
        val a = text.indexOf(CSS_BEGIN)
        val b = text.indexOf(CSS_END)
        return if (a >= 0 && b > a) (text.substring(0, a) + text.substring(b + CSS_END.length)).trimEnd() + "\n" else text
    }

    private fun writeScript() {
        val port = File(AppDirs.root, "instance.port").absolutePath
        File(dir, "ctl.sh").apply {
            writeText(
                """
                #!/usr/bin/env bash
                p=${'$'}(cat '$port' 2>/dev/null) || exit 0
                exec 3<>/dev/tcp/127.0.0.1/${'$'}p && printf 'nocturne://control/%s\n' "${'$'}1" >&3 && head -c1 <&3 >/dev/null
                """.trimIndent() + "\n"
            )
            setExecutable(true)
        }
    }

    private fun reload() {
        runCatching { ProcessBuilder("pkill", "-SIGUSR2", "-x", "waybar").start() }
    }

    private fun signal() {
        runCatching { ProcessBuilder("pkill", "-RTMIN+$SIGNAL", "-x", "waybar").start() }
    }

    private val STYLE = """
        #nocturne { margin: 0 6px; }
        #custom-nocturne-title { padding: 0 8px; }
        #custom-nocturne-title.paused { opacity: 0.6; }
        #custom-nocturne-prev, #custom-nocturne-play, #custom-nocturne-next,
        #custom-nocturne-like, #custom-nocturne-dislike, #custom-nocturne-volume, #custom-nocturne-open { padding: 0 6px; }
        #custom-nocturne-like.on { color: #ff6b8a; }
    """.trimIndent()
}
