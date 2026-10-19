package app.nocturne

import app.nocturne.data.Settings
import app.nocturne.player.PlayerState
import app.nocturne.ui.components.shareUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs

object DiscordRpc {
    private var started = false
    private var pipe: RandomAccessFile? = null
    private var appId = ""
    private var lastKey: String? = null
    private var lastStart = 0L
    private var retryAt = 0L
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    fun start() {
        if (started) return
        started = true
        thread(isDaemon = true, name = "discord-rpc") {
            while (true) {
                runCatching { tick() }.onFailure {
                    close()
                    DiscordPresence.status = it.message ?: "Discord isn't running"
                }
                Thread.sleep(2000)
            }
        }
    }

    private fun tick() {
        val graph = AppGraph.instance
        val s = graph.settings.state.value
        if (!s.discordRpc || !windows) {
            close()
            DiscordPresence.status = if (!windows && s.discordRpc) "Available on Windows only" else ""
            return
        }
        if (s.discordAppId.isBlank()) {
            close()
            DiscordPresence.status = "Add an Application ID to connect"
            return
        }
        if (pipe == null || appId != s.discordAppId) {
            close()
            if (System.currentTimeMillis() < retryAt) return
            retryAt = System.currentTimeMillis() + 10_000
            connect(s.discordAppId)
        }
        val p = graph.player.state.value
        val activity = activity(s, p)
        val start = System.currentTimeMillis() - p.positionMs
        val key = activity.toString()
        if (key == lastKey && (!s.discordShowTime || !p.isPlaying || abs(start - lastStart) < 3000)) return
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("nonce", UUID.randomUUID().toString())
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                put("activity", withTime(activity, s, p, start))
            }
        }
        send(1, payload.toString())
        val reply = read()
        if (reply.contains("\"evt\":\"ERROR\"")) {
            val msg = runCatching { Json.parseToJsonElement(reply).jsonObject["data"]!!.jsonObject["message"]!!.jsonPrimitive.content }.getOrNull()
            DiscordPresence.status = "Discord: ${msg ?: "error"}"
        } else {
            DiscordPresence.status = "Connected to Discord"
        }
        lastKey = key
        lastStart = start
    }

    private fun activity(s: Settings, p: PlayerState): JsonElement {
        val track = p.current ?: return JsonNull
        if (!p.isPlaying && !s.discordShowPaused) return JsonNull
        fun line(tpl: String) = DiscordPresence.fill(tpl, track).take(128).takeIf { it.isNotEmpty() }?.padEnd(2, '​')
        val art = track.artworkUrl?.takeIf { s.discordShowArtwork && it.startsWith("https://") && it.length <= 256 }
        val icon = DiscordPresence.sourceIcon(track.source).takeIf { s.discordShowSource }
        return buildJsonObject {
            put("type", s.discordActivityType)
            put("status_display_type", s.discordStatusDisplay)
            line(s.discordDetails)?.let { put("details", it) }
            line(s.discordState)?.let { put("state", it) }
            putJsonObject("assets") {
                val large = art ?: icon
                if (large != null) {
                    put("large_image", large)
                    line(s.discordLargeText)?.let { put("large_text", it) }
                }
                if (art != null && icon != null) {
                    put("small_image", icon)
                    (if (p.isPlaying) line(s.discordSmallText) else "Paused")?.let { put("small_text", it) }
                }
            }
            val url = track.shareUrl()
            val label = DiscordPresence.fill(s.discordButton, track).take(32)
            if (s.discordShowButton && label.isNotEmpty() && url.startsWith("http")) {
                putJsonArray("buttons") { addJsonObject { put("label", label); put("url", url.take(512)) } }
            }
        }
    }

    private fun withTime(activity: JsonElement, s: Settings, p: PlayerState, start: Long): JsonElement {
        if (activity !is JsonObject || !s.discordShowTime || !p.isPlaying) return activity
        return JsonObject(activity + ("timestamps" to buildJsonObject {
            put("start", start)
            if (p.durationMs > 0) put("end", start + p.durationMs)
        }))
    }

    private fun connect(id: String) {
        val file = (0..9).firstNotNullOfOrNull { i ->
            runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull()
        } ?: throw IllegalStateException("Discord isn't running")
        pipe = file
        send(0, buildJsonObject { put("v", 1); put("client_id", id) }.toString())
        val reply = read()
        if (!reply.contains("\"READY\"")) {
            close()
            throw IllegalStateException(if (reply.contains("4000") || reply.contains("Invalid")) "Invalid Application ID" else "Discord refused the connection")
        }
        appId = id
        lastKey = null
        DiscordPresence.status = "Connected to Discord"
    }

    private fun send(op: Int, json: String) {
        val body = json.toByteArray()
        val buf = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).put(body)
        pipe!!.write(buf.array())
    }

    private fun read(): String {
        val f = pipe!!
        val head = ByteArray(8)
        f.readFully(head)
        val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val op = bb.int
        val len = bb.int
        val body = ByteArray(len)
        f.readFully(body)
        val text = body.decodeToString()
        if (op == 2) throw IllegalStateException(if (text.contains("4000")) "Invalid Application ID" else "Discord closed the connection")
        return text
    }

    private fun close() {
        runCatching { pipe?.close() }
        pipe = null
        appId = ""
        lastKey = null
    }
}
