package app.nocturne.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import app.nocturne.ui.kit.Card
import androidx.compose.material3.CardDefaults
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.FilledTonalButton
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.Sync
import app.nocturne.i18n.t
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun scanQr(context: Context): String? = suspendCancellableCoroutine { cont ->
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { if (cont.isActive) cont.resume(it.rawValue) }
        .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}

@Composable
actual fun PairingCard() {
    val graph = AppGraph.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Sign in on your PC"), style = MaterialTheme.typography.titleMedium)
                Text(
                    status?.second ?: t("On the PC open Settings → Accounts → Show QR code, then scan it here."),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status?.first) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            else FilledTonalButton(onClick = {
                scope.launch {
                    status = null
                    val text = runCatching { scanQr(context) }.getOrElse { status = false to (it.message ?: t("Scanner unavailable")); return@launch } ?: return@launch
                    val joined = graph.sync.joinLink(text) // newer PCs also pair for device sync
                    val target = Sync.parse(text) ?: run {
                        status = if (joined) true to t("Paired. Your devices will appear in a moment.") else false to t("This isn't a Nocturne QR code")
                        return@launch
                    }
                    busy = true
                    status = runCatching { Sync.send(graph, target) }
                        .fold({ true to t("Sent! Your PC is signed in now.") }, { false to (it.message ?: t("Failed")) })
                    busy = false
                }
            }) { Text(t("Scan QR code")) }
        }
    }
}

@Composable
actual fun rememberQrScanner(): (suspend () -> String?)? {
    val context = LocalContext.current
    return remember(context) {
        val scan: suspend () -> String? = { scanQr(context) }
        scan
    }
}
