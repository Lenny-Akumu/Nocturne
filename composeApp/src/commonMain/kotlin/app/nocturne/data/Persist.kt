package app.nocturne.data

import app.nocturne.FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coalesced background saves: bursts of changes (sliders, plays) become one write off the UI thread. */
class Persist(private val file: String, private val delayMs: Long = 350, private val encode: () -> String) {
    private val tick = Channel<Unit>(Channel.CONFLATED)
    private var dirty = false

    init {
        all += this
        scope.launch {
            for (u in tick) {
                delay(delayMs)
                flush()
            }
        }
    }

    fun schedule() {
        dirty = true
        tick.trySend(Unit)
    }

    fun flush() {
        if (!dirty) return
        dirty = false
        runCatching { FileStore.write(file, encode()) }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val all = mutableListOf<Persist>()

        /** On exit / backgrounding. */
        fun flushAll() = all.toList().forEach { it.flush() }
    }
}
