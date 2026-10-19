package app.nocturne.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nocturne.systemLocale

object I18n {
    var lang by mutableStateOf("en")

    val languages: List<Pair<String, String>> = listOf(
        "system" to "System default",
        "en" to "English",
        "uk" to "Українська",
        "pl" to "Polski",
        "cs" to "Čeština",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "it" to "Italiano",
        "pt" to "Português",
        "tr" to "Türkçe",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh" to "中文",
    )

    private val tables: Map<String, Map<String, String>> by lazy {
        mapOf(
            "uk" to Uk.strings, "pl" to Pl.strings, "cs" to Cs.strings, "de" to De.strings,
            "fr" to Fr.strings, "es" to Es.strings, "it" to It.strings, "pt" to Pt.strings,
            "tr" to Tr.strings, "ja" to Ja.strings, "ko" to Ko.strings, "zh" to Zh.strings,
        )
    }

    fun apply(setting: String) {
        val code = if (setting == "system") systemLocale().first else setting
        lang = if (code == "en" || code in tables.keys) code else "en"
    }

    fun lookup(s: String): String = tables[lang]?.get(s) ?: s
}

fun t(s: String): String = I18n.lookup(s)

fun t(s: String, vararg args: Any?): String {
    var r = I18n.lookup(s)
    args.forEach { a -> r = r.replaceFirst("%s", a.toString()) }
    return r
}
