package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.Card
import androidx.compose.material3.CardDefaults
import app.nocturne.ui.kit.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.Sync
import app.nocturne.data.SyncBundle
import app.nocturne.i18n.t
import app.nocturne.secureRandomBytes
import app.nocturne.ui.screens.invalidateHomeCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

private class PairServer {
    val key = secureRandomBytes(32)
    private val result = CompletableDeferred<SyncBundle>()
    private val server = HttpServer.create(InetSocketAddress(0), 0).apply {
        createContext("/pair") { ex ->
            val body = ex.requestBody.use { it.readBytes().decodeToString() }
            val bundle = runCatching { Sync.open(key, body) }.getOrNull()
            val reply = (if (bundle != null) "ok" else "Wrong QR code").toByteArray()
            ex.sendResponseHeaders(if (bundle != null) 200 else 400, reply.size.toLong())
            ex.responseBody.use { it.write(reply) }
            bundle?.let { result.complete(it) }
        }
        start()
    }
    val code = Sync.qr(lanAddresses(), server.address.port, key)

    suspend fun await(): SyncBundle = result.await()
    fun stop() = server.stop(0)
}

private fun lanAddresses(): List<String> =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .sortedBy { n -> val d = n.displayName.lowercase(); if (listOf("virtual", "vethernet", "vmware", "hyper-v", "wsl", "vpn", "tap").any { it in d }) 1 else 0 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .map { it.hostAddress }
        .distinct()
        .take(6)

@Composable
actual fun PairingCard() {
    var open by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.QrCode2, null, Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Sign in from your phone"), style = MaterialTheme.typography.titleMedium)
                Text(
                    status ?: t("Scan a QR code with Nocturne on your phone to copy its accounts, liked songs and playlists here."),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = { open = true }) { Text(t("Show QR code")) }
        }
    }
    if (open) PairDialog(onDone = { status = it; open = false }, onDismiss = { open = false })
}

@Composable
private fun PairDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val server = remember { runCatching { PairServer() } }
    DisposableEffect(server) { onDispose { server.getOrNull()?.stop() } }
    LaunchedEffect(server) {
        val s = server.getOrNull() ?: return@LaunchedEffect
        val bundle = s.await()
        Sync.apply(graph, bundle)
        invalidateHomeCache()
        onDone(t("Synced with your phone"))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Scan with your phone")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                server.fold(
                    { s -> QrImage(s.code, Modifier.size(260.dp)) },
                    { Text(it.message ?: "Couldn't start pairing", color = MaterialTheme.colorScheme.error) },
                )
                Text(
                    t("On your phone open Nocturne → Settings → Accounts → Scan QR code. Both devices must be on the same Wi-Fi. If Windows asks, allow Nocturne on private networks."),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

@Composable
private fun QrImage(text: String, modifier: Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
        Canvas(Modifier.size(240.dp)) {
            val cell = size.minDimension / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
