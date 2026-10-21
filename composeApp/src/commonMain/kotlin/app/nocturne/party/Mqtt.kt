package app.nocturne.party

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Minimal MQTT 3.1.1 packet codec (QoS 0 only). */
internal object Mqtt {
    const val CONNACK = 2
    const val PUBLISH = 3
    const val SUBACK = 9
    const val PINGRESP = 13
    val PINGREQ = byteArrayOf(0xC0.toByte(), 0)
    val DISCONNECT = byteArrayOf(0xE0.toByte(), 0)

    fun clientId(): String {
        val abc = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "noc" + (1..17).map { abc[Random.nextInt(abc.length)] }.joinToString("")
    }

    fun remainingLength(n: Int): ByteArray {
        require(n in 0..268_435_455)
        val out = ArrayList<Byte>(4)
        var x = n
        do {
            var b = x % 128
            x /= 128
            if (x > 0) b = b or 0x80
            out += b.toByte()
        } while (x > 0)
        return out.toByteArray()
    }

    private fun str(s: String): ByteArray {
        val b = s.encodeToByteArray()
        return byteArrayOf((b.size shr 8).toByte(), b.size.toByte()) + b
    }

    private fun packet(header: Int, body: ByteArray) = byteArrayOf(header.toByte()) + remainingLength(body.size) + body

    /** Clean session, no will / credentials. */
    fun connect(clientId: String, keepAliveSec: Int) = packet(
        0x10,
        str("MQTT") + byteArrayOf(4, 0x02, (keepAliveSec shr 8).toByte(), keepAliveSec.toByte()) + str(clientId),
    )

    fun subscribe(packetId: Int, topic: String) = packet(
        0x82,
        byteArrayOf((packetId shr 8).toByte(), packetId.toByte()) + str(topic) + byteArrayOf(0),
    )

    fun publish(topic: String, payload: ByteArray, retain: Boolean) = packet(if (retain) 0x31 else 0x30, str(topic) + payload)

    /** Topic and payload of a PUBLISH packet (skips the packet id when QoS > 0). */
    fun parsePublish(p: MqttPacket): Pair<String, ByteArray> {
        val b = p.body
        val len = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        val topic = b.decodeToString(2, 2 + len)
        val start = 2 + len + if (((p.header shr 1) and 3) > 0) 2 else 0
        return topic to b.copyOfRange(start, b.size)
    }
}

internal class MqttPacket(val header: Int, val body: ByteArray) {
    val type: Int get() = header ushr 4
}

/** Reassembles MQTT packets from WebSocket frames (a packet may span frames, a frame may hold several packets). */
internal class MqttReader {
    private var buf = ByteArray(0)

    fun feed(chunk: ByteArray): List<MqttPacket> {
        buf += chunk
        val out = ArrayList<MqttPacket>()
        var pos = 0
        while (buf.size - pos >= 2) {
            var len = 0
            var mul = 1
            var i = pos + 1
            var done = false
            while (i < buf.size) {
                val b = buf[i++].toInt() and 0xFF
                len += (b and 0x7F) * mul
                if ((b and 0x80) == 0) { done = true; break }
                mul *= 128
                if (i - pos > 4) error("bad MQTT length")
            }
            if (!done || buf.size - i < len) break
            if (len > MAX_PACKET) error("MQTT packet too big")
            out += MqttPacket(buf[pos].toInt() and 0xFF, buf.copyOfRange(i, i + len))
            pos = i + len
        }
        if (pos > 0) buf = buf.copyOfRange(pos, buf.size)
        if (buf.size > MAX_PACKET) error("MQTT packet too big")
        return out
    }

    private companion object { const val MAX_PACKET = 1 shl 20 }
}

internal class MqttSession(private val ws: DefaultClientWebSocketSession) {
    private val reader = MqttReader()
    private val pending = ArrayDeque<MqttPacket>()
    private var nextId = 1

    suspend fun send(bytes: ByteArray) {
        try {
            ws.send(Frame.Binary(true, bytes))
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException("closed", e) // socket died, not us
        }
    }

    suspend fun publish(topic: String, payload: ByteArray, retain: Boolean) = send(Mqtt.publish(topic, payload, retain))

    suspend fun subscribe(topic: String) {
        send(Mqtt.subscribe(nextId, topic))
        nextId = nextId % 65535 + 1
    }

    /** Next packet; throws (never a CancellationException) when the socket closes. */
    suspend fun read(): MqttPacket {
        while (pending.isEmpty()) {
            val r = ws.incoming.receiveCatching()
            val f = r.getOrNull()
            if (f == null) {
                val why = runCatching { withTimeoutOrNull(300) { ws.closeReason.await() } }.getOrNull()?.message
                throw IllegalStateException("closed" + (why?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""), r.exceptionOrNull())
            }
            if (f is Frame.Binary) pending += reader.feed(f.data)
        }
        return pending.removeFirst()
    }
}

private const val KEEP_ALIVE = 30

/**
 * Opens an MQTT-over-WebSocket link to [url] (client needs the WebSockets plugin), runs [block] with keepalive pings and
 * delivers incoming publishes to [onMessage]. Returns when [block] returns; throws when the link fails or drops.
 */
internal suspend fun HttpClient.mqtt(
    url: String,
    onMessage: (topic: String, payload: ByteArray, retained: Boolean) -> Unit = { _, _, _ -> },
    block: suspend MqttSession.() -> Unit,
) {
    val ws = withTimeoutOrNull(15_000) { webSocketSession(url) { header(HttpHeaders.SecWebSocketProtocol, "mqtt") } }
        ?: error("connect timeout")
    try {
        val s = MqttSession(ws)
        s.send(Mqtt.connect(Mqtt.clientId(), KEEP_ALIVE))
        val ack = withTimeoutOrNull(10_000) { s.read() } ?: error("no CONNACK")
        if (ack.type != Mqtt.CONNACK || ack.body.size < 2) error("bad CONNACK")
        if (ack.body[1].toInt() != 0) error("refused (${ack.body[1]})")
        coroutineScope {
            launch {
                while (true) {
                    delay(KEEP_ALIVE * 1000L * 2 / 3)
                    s.send(Mqtt.PINGREQ)
                }
            }
            launch {
                while (true) {
                    // A ping goes out every 20s, so silence this long means a dead link.
                    val p = withTimeoutOrNull(KEEP_ALIVE * 1500L) { s.read() } ?: error("broker timed out")
                    when (p.type) {
                        Mqtt.PUBLISH -> runCatching { Mqtt.parsePublish(p) }.getOrNull()?.let { (t, b) -> onMessage(t, b, (p.header and 1) == 1) }
                        Mqtt.SUBACK -> if (p.body.drop(2).any { it == 0x80.toByte() }) error("subscribe refused")
                    }
                }
            }
            s.block()
            s.send(Mqtt.DISCONNECT)
            coroutineContext.cancelChildren()
        }
    } finally {
        withContext(NonCancellable) { withTimeoutOrNull(1500) { runCatching { ws.close() } } }
        ws.cancel()
    }
}
