package app.nocturne.player

import app.nocturne.AppGraph
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Nocturne's own slider in the system volume mixer (Windows sessions, PipeWire/PulseAudio streams). */
interface SystemMixer {
    /** 0..1, or null while the system has no stream of ours. */
    fun get(): Float?
    fun set(v: Float)

    companion object {
        fun create(): SystemMixer? {
            val os = System.getProperty("os.name").orEmpty()
            return when {
                os.startsWith("Windows", true) -> WindowsMixer
                os.startsWith("Linux", true) -> PulseMixer.takeIf { it.usable() }
                else -> null
            }
        }
    }
}

/**
 * Keeps the app's volume and the system mixer's "Nocturne" slider the same, both ways.
 * The mixer holds the user's volume; fades (crossfade, DJ transitions, sleep timer) stay inside the app,
 * so the mixer slider doesn't jump on every song change.
 */
object MixerSync {
    /** The user's volume as the system mixer applies it; null when the mixer isn't in charge. */
    @Volatile var userVolume: Float? = null
        private set

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "mixer").apply { isDaemon = true } }.asCoroutineDispatcher()

    fun start(graph: AppGraph, engine: VlcAudioEngine?) {
        val mixer = SystemMixer.create() ?: return
        graph.scope.launch(io) {
            var synced = -1f
            // the app's slider moved: hand it to the system
            launch {
                graph.settings.state.map { it.volume.coerceIn(0f, 1f) }.distinctUntilChanged().collect { v ->
                    if (userVolume != null && abs(v - synced) > 0.004f) {
                        synced = v
                        runCatching { mixer.set(v) }
                        userVolume = v
                        engine?.refreshGain()
                    }
                }
            }
            while (true) {
                val os = if (engine?.mixerReady == true) runCatching { mixer.get() }.getOrNull() else null
                if (os == null) {
                    // our stream is gone (or not there yet): the app does the volume itself again
                    if (userVolume != null) { userVolume = null; engine?.refreshGain() }
                } else if (userVolume == null) {
                    // stream found: the app's volume wins once, then they follow each other
                    val v = graph.settings.value.volume.coerceIn(0f, 1f)
                    synced = v
                    runCatching { mixer.set(v) }
                    userVolume = v
                    engine?.refreshGain()
                } else if (abs(os - synced) > 0.015f) {
                    // moved in the system mixer
                    synced = os
                    userVolume = os
                    graph.settings.update { it.copy(volume = os) }
                    engine?.refreshGain()
                }
                delay(700)
            }
        }
    }
}

/** Linux: our sink input in PipeWire / PulseAudio, through pactl. */
private object PulseMixer : SystemMixer {
    private val pid = ProcessHandle.current().pid().toString()

    fun usable() = runCatching { run("pactl", "info") != null }.getOrDefault(false)

    private fun run(vararg cmd: String): String? {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return if (p.waitFor() == 0) out else null
    }

    private data class Input(val index: String, val percent: Int)

    // "Sink Input #12 … Volume: front-left: 30934 /  47% / … application.process.id = "1234""
    private fun ours(): List<Input> {
        val text = run("pactl", "list", "sink-inputs") ?: return emptyList()
        return text.split("Sink Input #").drop(1).mapNotNull { block ->
            if (!block.contains("application.process.id = \"$pid\"")) return@mapNotNull null
            val index = block.substringBefore('\n').trim()
            val pct = Regex("Volume:[^\\n]*?(\\d+)%").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            Input(index, pct)
        }
    }

    override fun get(): Float? = ours().firstOrNull()?.let { it.percent / 100f }

    override fun set(v: Float) {
        val pct = "${(v * 100).roundToInt().coerceIn(0, 100)}%"
        ours().forEach { run("pactl", "set-sink-input-volume", it.index, pct) }
    }
}

/** Windows: our audio session on the default output (the row in the Volume mixer), through WASAPI. */
private object WindowsMixer : SystemMixer {
    private val pid = ProcessHandle.current().pid().toInt()
    private var comReady = false

    private val CLSID_MMDeviceEnumerator = Guid.GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    private val IID_IMMDeviceEnumerator = Guid.GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    private val IID_IAudioSessionManager2 = Guid.GUID("{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}")
    private val IID_IAudioSessionControl2 = Guid.GUID("{BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D}")
    private val IID_ISimpleAudioVolume = Guid.GUID("{87CE5498-68D6-44E5-9215-6DA47EF883D8}")
    private const val CLSCTX_ALL = 23

    // COM by hand: method [index] of the object's vtable
    private fun call(obj: Pointer, index: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0)
        val fn = Function.getFunction(vtbl.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf<Any?>(obj, *args))
    }

    private fun release(obj: Pointer?) { if (obj != null) runCatching { call(obj, 2) } }

    /** Every volume control of our process's sessions; the caller releases them. */
    private fun volumes(): List<Pointer> {
        if (!comReady) { Ole32.INSTANCE.CoInitializeEx(null, 0); comReady = true }
        val out = ArrayList<Pointer>()
        val en = PointerByReference()
        if (Ole32.INSTANCE.CoCreateInstance(CLSID_MMDeviceEnumerator, null, CLSCTX_ALL, IID_IMMDeviceEnumerator, en).toInt() != 0) return out
        val enumerator = en.value
        val dev = PointerByReference()
        try {
            if (call(enumerator, 4, 0, 1, dev) != 0) return out // GetDefaultAudioEndpoint(eRender, eMultimedia)
            val mgr = PointerByReference()
            if (call(dev.value, 3, IID_IAudioSessionManager2, CLSCTX_ALL, null, mgr) != 0) return out // Activate
            val sessions = PointerByReference()
            try {
                if (call(mgr.value, 5, sessions) != 0) return out // GetSessionEnumerator
                val count = IntByReference()
                call(sessions.value, 3, count)
                for (i in 0 until count.value) {
                    val ctl = PointerByReference()
                    if (call(sessions.value, 4, i, ctl) != 0) continue
                    val ctl2 = PointerByReference()
                    try {
                        if (call(ctl.value, 0, IID_IAudioSessionControl2, ctl2) != 0) continue
                        val id = IntByReference()
                        call(ctl2.value, 14, id) // GetProcessId
                        if (id.value != pid) continue
                        val vol = PointerByReference()
                        if (call(ctl.value, 0, IID_ISimpleAudioVolume, vol) == 0) out += vol.value
                    } finally {
                        release(ctl2.value); release(ctl.value)
                    }
                }
            } finally {
                release(sessions.value); release(mgr.value)
            }
        } finally {
            release(dev.value); release(enumerator)
        }
        return out
    }

    override fun get(): Float? {
        val vs = volumes()
        try {
            val f = FloatByReference()
            return vs.firstOrNull()?.let { if (call(it, 4, f) == 0) f.value else null } // GetMasterVolume
        } finally { vs.forEach(::release) }
    }

    override fun set(v: Float) {
        val vs = volumes()
        try { vs.forEach { call(it, 3, v.coerceIn(0f, 1f), null) } } // SetMasterVolume
        finally { vs.forEach(::release) }
    }
}
