package app.nocturne.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

actual fun createCastBackend(): CastBackend = DlnaBackend()

private class DlnaBackend : CastBackend {
    private val ns = "urn:schemas-upnp-org:service:AVTransport:1"

    override suspend fun discover(): List<CastDevice> = withContext(Dispatchers.IO) {
        val release = acquireMulticastLock()
        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { sock ->
                sock.soTimeout = 400
                val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $ns\r\n\r\n".toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
                val until = System.currentTimeMillis() + 3500
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < until) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    String(p.data, 0, p.length).lines()
                        .firstOrNull { it.startsWith("location:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim()?.let(locations::add)
                }
            }
        } finally {
            release()
        }
        locations.mapNotNull { loc ->
            runCatching {
                val xml = get(loc)
                val name = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim() ?: "Device"
                val service = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }
                    .firstOrNull { "AVTransport" in it } ?: return@runCatching null
                val control = Regex("<controlURL>(.*?)</controlURL>").find(service)?.groupValues?.get(1)?.trim() ?: return@runCatching null
                CastDevice(URL(URL(loc), control).toString(), unescape(name))
            }.getOrNull()
        }.distinctBy { it.id }
    }

    override suspend fun load(device: CastDevice, url: String, mime: String?, title: String, artist: String, art: String?) {
        val type = mime?.substringBefore(';') ?: "audio/mpeg"
        val didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${esc(title)}</dc:title><upnp:artist>${esc(artist)}</upnp:artist>" +
            (art?.let { "<upnp:albumArtURI>${esc(it)}</upnp:albumArtURI>" } ?: "") +
            "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
            "<res protocolInfo=\"http-get:*:$type:*\">${esc(url)}</res></item></DIDL-Lite>"
        runCatching { soap(device, "Stop", "") }
        soap(device, "SetAVTransportURI", "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>${esc(didl)}</CurrentURIMetaData>")
    }

    override suspend fun play(device: CastDevice) { soap(device, "Play", "<Speed>1</Speed>") }

    override suspend fun pause(device: CastDevice) { soap(device, "Pause", "") }

    override suspend fun stop(device: CastDevice) { soap(device, "Stop", "") }

    override suspend fun seek(device: CastDevice, ms: Long) {
        val s = ms / 1000
        soap(device, "Seek", "<Unit>REL_TIME</Unit><Target>%d:%02d:%02d</Target>".format(s / 3600, (s / 60) % 60, s % 60))
    }

    override suspend fun status(device: CastDevice): CastStatus? = runCatching {
        val pos = soap(device, "GetPositionInfo", "")
        val info = soap(device, "GetTransportInfo", "")
        val state = Regex("<CurrentTransportState>(.*?)</CurrentTransportState>").find(info)?.groupValues?.get(1).orEmpty()
        CastStatus(
            time(Regex("<RelTime>(.*?)</RelTime>").find(pos)?.groupValues?.get(1)),
            time(Regex("<TrackDuration>(.*?)</TrackDuration>").find(pos)?.groupValues?.get(1)),
            state == "PLAYING", state == "STOPPED" || state == "NO_MEDIA_PRESENT",
        )
    }.getOrNull()

    private fun time(v: String?): Long {
        val p = v?.substringBefore('.')?.split(':')?.mapNotNull { it.toLongOrNull() } ?: return 0
        return if (p.size == 3) (p[0] * 3600 + p[1] * 60 + p[2]) * 1000 else 0
    }

    private suspend fun soap(device: CastDevice, action: String, args: String): String = withContext(Dispatchers.IO) {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"$ns\">" +
            "<InstanceID>0</InstanceID>$args</u:$action></s:Body></s:Envelope>"
        val conn = (URL(device.id).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4000
            readTimeout = 6000
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", "\"$ns#$action\"")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("$action failed ($code)")
        text
    }

    private fun get(url: String): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 3000
            readTimeout = 4000
            inputStream.bufferedReader().use { it.readText() }
        }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
}
