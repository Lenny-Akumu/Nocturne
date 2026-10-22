package app.nocturne.party

import app.nocturne.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Result of one relay round trip. */
class RelayCheck(val relay: String, val url: String, val ok: Boolean, val detail: String, val ms: Long) {
    override fun toString() = "$relay: ${if (ok) "OK" else "FAILED"} (${ms} ms) $detail — $url"
}

/** "Class: message" plus the root cause, for status lines people can copy into a bug report. */
internal fun describe(e: Throwable): String {
    fun one(t: Throwable) = (t::class.simpleName ?: "Error") + (t.message?.takeIf { it.isNotBlank() }?.let { ": " + it.lineSequence().first() } ?: "")
    var root = e
    while (root.cause != null && root.cause !== root) root = root.cause!!
    return if (root === e) one(e) else one(e) + " ← " + one(root)
}

/**
 * Checks every online relay in parallel the way a party uses it: client A publishes a retained and then a live message,
 * a separate client B must receive both. [ws] needs the WebSockets plugin.
 */
suspend fun checkRelays(http: HttpClient, ws: HttpClient, timeoutMs: Long = 20_000): List<RelayCheck> = coroutineScope {
    Relay.entries.map { r -> async { checkRelay(http, ws, r.name.lowercase(), r.url, timeoutMs) } }.awaitAll()
}

internal suspend fun checkRelay(http: HttpClient, ws: HttpClient, name: String, url: String, timeoutMs: Long = 20_000): RelayCheck {
    val start = nowMillis()
    val stage = arrayOf("start")
    return try {
        val detail = withTimeout(timeoutMs) { if (url.startsWith("https://")) checkNtfy(http, url, stage) else checkMqtt(ws, url, stage) }
        RelayCheck(name, url, true, detail, nowMillis() - start)
    } catch (e: TimeoutCancellationException) {
        RelayCheck(name, url, false, "timed out at: ${stage[0]}", nowMillis() - start)
    } catch (e: Throwable) {
        if (e is CancellationException) currentCoroutineContext().ensureActive() // only a dead socket gets reported
        RelayCheck(name, url, false, "at ${stage[0]}: ${describe(e)}", nowMillis() - start)
    }
}

private suspend fun checkMqtt(ws: HttpClient, url: String, stage: Array<String>): String = coroutineScope {
    val topic = "nocturne/check/" + Mqtt.clientId()
    val retained = "retained-" + Mqtt.clientId()
    val live = "live-" + Mqtt.clientId()
    val published = CompletableDeferred<Unit>()
    val gotRetained = CompletableDeferred<Unit>()
    val done = CompletableDeferred<Unit>()
    var flag = false
    launch {
        stage[0] = "A connect"
        ws.mqtt(url) {
            stage[0] = "A publish"
            publish(topic, retained.encodeToByteArray(), retain = true)
            published.complete(Unit)
            gotRetained.await()
            publish(topic, live.encodeToByteArray(), retain = false)
            done.await()
            publish(topic, ByteArray(0), retain = true)
        }
    }
    published.await()
    val msgs = Channel<Pair<String, Boolean>>(Channel.UNLIMITED)
    stage[0] = "B connect"
    ws.mqtt(url, onMessage = { t, b, r -> if (t == topic && b.isNotEmpty()) msgs.trySend(b.decodeToString() to r) }) {
        subscribe(topic)
        stage[0] = "B waits for the retained message"
        val (m1, r1) = msgs.receive()
        check(m1 == retained) { "expected the retained message, got '$m1'" }
        flag = r1
        gotRetained.complete(Unit)
        stage[0] = "B waits for the live message"
        val (m2, _) = msgs.receive()
        check(m2 == live) { "expected the live message, got '$m2'" }
        done.complete(Unit)
    }
    stage[0] = "A clears the retained message"
    "retained + live delivered" + if (flag) "" else " (retain flag missing)"
}

private suspend fun checkNtfy(http: HttpClient, base: String, stage: Array<String>): String {
    val json = Json { ignoreUnknownKeys = true }
    val topic = base.trimEnd('/') + "/nocturne-check-" + Mqtt.clientId()
    val first = "first-" + Mqtt.clientId()
    val second = "second-" + Mqtt.clientId()
    suspend fun post(body: String) {
        val resp = http.post(topic) { setBody(body); timeout { requestTimeoutMillis = 8000 } }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value} on publish")
    }
    stage[0] = "publish"
    post(first)
    stage[0] = "open stream"
    var result = ""
    http.prepareGet("$topic/json?since=latest") { timeout { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 } }.execute { resp ->
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value} on stream")
        val ch = resp.bodyAsChannel()
        suspend fun next(): String {
            while (true) {
                val line = ch.readUTF8Line() ?: error("stream ended")
                val obj = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                if ((obj["event"] as? JsonPrimitive)?.content != "message") continue
                return (obj["message"] as? JsonPrimitive)?.content.orEmpty()
            }
        }
        stage[0] = "cached message"
        val m1 = next()
        check(m1 == first) { "expected the cached message, got '$m1'" }
        stage[0] = "live message"
        post(second)
        val m2 = next()
        check(m2 == second) { "expected the live message, got '$m2'" }
        result = "cached + live delivered"
    }
    return result
}
