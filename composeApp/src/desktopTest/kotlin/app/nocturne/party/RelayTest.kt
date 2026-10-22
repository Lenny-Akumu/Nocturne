package app.nocturne.party

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Live round trip through the public Listen-together relays with the app's own HttpClient setup (see AppGraph.http).
 * Network-dependent, so it only runs with NOCTURNE_RELAY_TEST=1 (the relay-test CI job); fails only if every relay fails.
 */
class RelayTest {
    private fun enabled() = System.getenv("NOCTURNE_RELAY_TEST") == "1"

    @Test
    fun relaysRoundTrip() = runBlocking {
        if (!enabled()) return@runBlocking println("RelayTest skipped (set NOCTURNE_RELAY_TEST=1)")
        val http = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
        val ws = http.config { install(WebSockets) }
        println("RELAY engine=${http.engine::class.qualifiedName} java=${System.getProperty("java.version")} os=${System.getProperty("os.name")}")
        val results = checkRelays(http, ws, timeoutMs = 25_000)
        val report = results.joinToString("\n") { "RELAY $it" }
        println(report)
        runCatching { File("build/relay-report.txt").writeText(report + "\n") }
        http.close()
        if (results.none { it.ok }) fail("every relay failed:\n$report")
    }

    @Test
    fun mqttCodec() {
        val r = MqttReader()
        val pub = Mqtt.publish("a/b", "hi".encodeToByteArray(), retain = true)
        // Split mid-packet and glue two packets into one frame.
        val first = r.feed(pub.copyOfRange(0, 3))
        val rest = r.feed(pub.copyOfRange(3, pub.size) + pub)
        assertTrue(first.isEmpty() && rest.size == 2)
        val (topic, body) = Mqtt.parsePublish(rest[0])
        assertTrue(topic == "a/b" && body.decodeToString() == "hi" && (rest[0].header and 1) == 1)
        assertTrue(Mqtt.remainingLength(321).contentEquals(byteArrayOf(0xC1.toByte(), 0x02)))
    }
}
