package app.nocturne.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import app.nocturne.AppGraph
import app.nocturne.FileStore
import app.nocturne.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scripted walkthrough for the promo screen recording: taps real buttons in order and logs where each tap
 * landed (window points + wall clock), so the video can draw a finger over the recording afterwards.
 */
object Demo {
    var enabled = false
        private set
    /** White frame at the start so the recording can be synced to the tap log. */
    var flash by androidx.compose.runtime.mutableStateOf(false)
        private set
    private val targets = HashMap<String, Pair<Rect, () -> Unit>>()
    private val taps = StringBuilder()

    fun register(id: String, bounds: Rect, onClick: () -> Unit) { targets[id] = bounds to onClick }

    private fun log(kind: String, x: Float, y: Float, id: String = "") {
        taps.append("""{"t":${nowMillis()},"k":"$kind","id":"$id","x":$x,"y":$y},""")
        FileStore.write("demo_taps.json", "[" + taps.toString().trimEnd(',') + "]")
    }

    /** Waits for [id] to be on screen, logs the tap at its center and runs its click. */
    suspend fun tap(id: String, timeoutMs: Long = 6000) {
        var waited = 0L
        while (targets[id] == null && waited < timeoutMs) { delay(100); waited += 100 }
        val (r, click) = targets[id] ?: run { log("miss", 0f, 0f, id); return }
        log(if (id == "sheet_close") "swipe" else "tap", r.center.x, if (id == "sheet_close") r.top + 40f else r.center.y, id)
        delay(260) // finger lands, then the press
        runCatching { click() }.onFailure { log("error", 0f, 0f, (it.message ?: it::class.simpleName ?: "").replace("\"", "'").take(120)) }
    }

    /** A swipe-free pause the finger spends "reading". */
    suspend fun idle(ms: Long) = delay(ms)

    fun start(g: AppGraph, script: String) {
        enabled = true
        FileStore.delete("demo_taps.json")
        g.scope.launch {
            delay(2500)
            flash = true
            log("start", 0f, 0f)
            delay(400)
            flash = false
            runCatching {
                when (script) {
                    "full" -> full(g)
                    else -> short(g)
                }
            }.onFailure { log("error", 0f, 0f, (it.message ?: it::class.simpleName ?: "").replace("\"", "'").take(120)) }
            log("end", 0f, 0f)
        }
    }

    private suspend fun playSomething(g: AppGraph) {
        if (targets.keys.none { it.startsWith("rec") }) delay(4000)
        val rec = targets.keys.filter { it.startsWith("rec") }.minOrNull()
        if (rec != null) tap(rec) else app.nocturne.ui.runPromoShot(g, "home", null)
        delay(2600)
        tap("mini")
        delay(2600)
    }

    private suspend fun full(g: AppGraph) {
        delay(1500)
        playSomething(g)
        tap("Equalizer and speed"); delay(1800)
        tap("ver_SPEED_UP"); delay(2400)
        tap("ver_SLOWED"); delay(2400)
        tap("ver_ORIGINAL"); delay(1200)
        tap("preset_Bass Boost"); delay(2200)
        tap("sheet_close"); delay(1500)
        tap("Lyrics"); delay(4000)
        tap("Lyrics"); delay(1200)
        tap("Like"); delay(1500)
        tap("Devices"); delay(3500)
        tap("party_start"); delay(4000)
        tap("sheet_close"); delay(1500)
        tap("Collapse"); delay(2000)
        tap("open_settings"); delay(1500)
        tap("settings_APPEARANCE"); delay(1800)
        tap("Design style"); delay(1200)
        tap("opt_Material You 3"); delay(3200)
        tap("Design style"); delay(1200)
        tap("opt_Liquid Glass"); delay(2500)
    }

    private suspend fun short(g: AppGraph) {
        delay(1200)
        playSomething(g)
        tap("Equalizer and speed"); delay(1800)
        tap("ver_SPEED_UP"); delay(2200)
        tap("preset_Bass Boost"); delay(2000)
        tap("sheet_close"); delay(1500)
        tap("Lyrics"); delay(3500)
    }
}

/** Registers this element as a demo tap target (no-op unless the demo runs). */
fun Modifier.demo(id: String, onClick: () -> Unit = {}): Modifier =
    if (!Demo.enabled) this else onGloballyPositioned { Demo.register(id, it.boundsInWindow(), onClick) }
