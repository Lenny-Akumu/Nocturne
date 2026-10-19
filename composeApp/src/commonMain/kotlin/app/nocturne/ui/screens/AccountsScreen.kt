package app.nocturne.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.Source
import app.nocturne.spotifyRedirectUri
import app.nocturne.ui.WebLoginDialog
import app.nocturne.ui.clearWebLogins
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.supportsWebLogin
import kotlinx.coroutines.launch

private const val GOOGLE_LOGIN =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private enum class WebLogin { YOUTUBE, SOUNDCLOUD }

@Suppress("DEPRECATION")
@Composable
fun AccountsSettings() {
    val graph = AppGraph.instance
    val acc by graph.accounts.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf<Source?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebLogin?>(null) }
    var paste by remember { mutableStateOf<Source?>(null) }

    fun run(source: Source, block: suspend () -> Unit) {
        busy = source
        error = null
        scope.launch {
            runCatching { block() }.onFailure { error = it.message ?: "Sign-in failed" }
            invalidateHomeCache()
            busy = null
        }
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Sign in to pull your liked songs, playlists, albums and artists into Nocturne.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }

        AccountCard(Source.YOUTUBE_MUSIC, acc.youtubeName.takeIf { acc.youtubeSignedIn }, busy == Source.YOUTUBE_MUSIC, onSignOut = {
            graph.signOutYouTube(); clearWebLogins(); invalidateHomeCache()
        }) {
            if (supportsWebLogin) Button(onClick = { web = WebLogin.YOUTUBE }) { Text("Sign in with Google") }
            OutlinedButton(onClick = { paste = Source.YOUTUBE_MUSIC }) { Text("Paste cookies") }
        }

        AccountCard(Source.SPOTIFY, acc.spotifyName.takeIf { acc.spotifySignedIn }, busy == Source.SPOTIFY, onSignOut = {
            graph.spotify.signOut(); invalidateHomeCache()
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var clientId by remember(settings.spotifyClientId) { mutableStateOf(settings.spotifyClientId) }
                OutlinedTextField(
                    clientId,
                    { clientId = it; graph.settings.update { s -> s.copy(spotifyClientId = it.trim()) } },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Add this Redirect URI to your app on developer.spotify.com:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spotifyRedirectUri, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(spotifyRedirectUri)) }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
                }
                Button(onClick = { run(Source.SPOTIFY) { graph.spotify.signIn() } }, enabled = clientId.isNotBlank()) { Text("Sign in with Spotify") }
            }
        }

        AccountCard(Source.SOUNDCLOUD, acc.soundcloudName.takeIf { acc.soundcloudSignedIn }, busy == Source.SOUNDCLOUD, onSignOut = {
            graph.soundcloud.signOut(); clearWebLogins(); invalidateHomeCache()
        }) {
            if (supportsWebLogin) Button(onClick = { web = WebLogin.SOUNDCLOUD }) { Text("Sign in") }
            OutlinedButton(onClick = { paste = Source.SOUNDCLOUD }) { Text("Paste token") }
        }
        Spacer(Modifier.height(16.dp))
    }

    when (web) {
        WebLogin.YOUTUBE -> WebLoginDialog("YouTube Music", GOOGLE_LOGIN, "https://music.youtube.com", "SAPISID", onCookies = { c ->
            web = null
            run(Source.YOUTUBE_MUSIC) { graph.signInYouTube(c) }
        }, onDismiss = { web = null })
        WebLogin.SOUNDCLOUD -> WebLoginDialog("SoundCloud", "https://soundcloud.com/signin", "https://soundcloud.com", "oauth_token", onCookies = { c ->
            web = null
            run(Source.SOUNDCLOUD) { graph.soundcloud.signIn(c) }
        }, onDismiss = { web = null })
        null -> Unit
    }

    paste?.let { source ->
        var text by remember { mutableStateOf("") }
        val yt = source == Source.YOUTUBE_MUSIC
        AlertDialog(
            onDismissRequest = { paste = null },
            title = { Text(if (yt) "Paste YouTube Music cookies" else "Paste SoundCloud token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (yt) "Open music.youtube.com in your browser while signed in, press F12 → Network, reload, click any request to music.youtube.com and copy the whole “Cookie” request header."
                        else "Open soundcloud.com while signed in, press F12 → Application → Cookies → soundcloud.com and copy the value of “oauth_token”.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(text, { text = it }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    paste = null
                    if (yt) run(source) { graph.signInYouTube(text) } else run(source) { graph.soundcloud.signIn(text) }
                }, enabled = text.isNotBlank()) { Text("Sign in") }
            },
            dismissButton = { TextButton(onClick = { paste = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountCard(
    source: Source,
    signedInAs: String?,
    busy: Boolean,
    onSignOut: () -> Unit,
    signInActions: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(source, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        signedInAs?.let { "Signed in as $it" } ?: "Not signed in",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (signedInAs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                else if (signedInAs != null) {
                    IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Rounded.Logout, "Sign out") }
                }
            }
            if (signedInAs == null && !busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { signInActions() }
            }
        }
    }
}
