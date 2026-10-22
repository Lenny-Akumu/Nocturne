package app.nocturne.ui.screens

import app.nocturne.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.Button
import androidx.compose.material3.ButtonDefaults
import app.nocturne.ui.kit.Card
import androidx.compose.material3.CardDefaults
import app.nocturne.ui.kit.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.OutlinedTextField
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.BuildInfo
import app.nocturne.model.Source
import app.nocturne.ui.WebLoginDialog
import app.nocturne.ui.clearWebLogins
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.brandColor
import app.nocturne.ui.supportsWebLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    var spotifyDev by remember { mutableStateOf(false) }
    var importFor by remember { mutableStateOf<Source?>(null) }
    var scHelp by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun run(source: Source, block: suspend () -> Unit) {
        busy = source
        error = null
        job?.cancel()
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: t("Sign-in failed")
            } finally {
                invalidateHomeCache()
                if (busy == source) busy = null
            }
        }
    }
    val cancel = { job?.cancel(); busy = null }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            t("Connect your music accounts to bring your liked songs, playlists, albums and artists into Nocturne."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }
        app.nocturne.ui.PairingCard()

        AccountCard(
            source = Source.YOUTUBE_MUSIC,
            signedInAs = acc.youtubeName.takeIf { acc.youtubeSignedIn } ?: t("Google account").takeIf { acc.youtubeSignedIn },
            busy = busy == Source.YOUTUBE_MUSIC,
            hint = "Use your Google account",
            onSignOut = { graph.signOutYouTube(); clearWebLogins(); invalidateHomeCache() },
            onOther = { paste = Source.YOUTUBE_MUSIC },
            onImport = { importFor = Source.YOUTUBE_MUSIC },
            onCancel = cancel,
        ) {
            ProviderButton("Continue with Google", Color.White, Color(0xFF1F1F1F), mark = "G") {
                if (supportsWebLogin) web = WebLogin.YOUTUBE else paste = Source.YOUTUBE_MUSIC
            }
        }

        val spotifyClientId = settings.spotifyClientId.ifBlank { BuildInfo.SPOTIFY_CLIENT_ID }
        AccountCard(
            source = Source.SPOTIFY,
            signedInAs = acc.spotifyName.takeIf { acc.spotifySignedIn },
            avatar = acc.spotifyAvatar,
            busy = busy == Source.SPOTIFY,
            hint = "Log in with Spotify, Google, Facebook or Apple on the next screen",
            busyHint = "Finish signing in in your browser. If Spotify shows “Invalid redirect URI”, open Spotify app settings below.",
            onSignOut = { graph.spotify.signOut(); invalidateHomeCache() },
            onOther = { spotifyDev = !spotifyDev },
            otherLabel = "Spotify app settings (advanced)",
            onImport = { importFor = Source.SPOTIFY },
            onCancel = cancel,
        ) {
            ProviderButton("Continue with Spotify", Source.SPOTIFY.brandColor, Color.Black) {
                if (spotifyClientId.isBlank()) spotifyDev = true
                else run(Source.SPOTIFY) { graph.spotify.signIn() }
            }
        }
        if (!acc.spotifySignedIn) app.nocturne.ui.kit.OutlinedButton(onClick = { importFor = Source.SPOTIFY }, modifier = Modifier.fillMaxWidth()) {
            Text(t("Import a Spotify playlist or album by link"))
        }
        AccountCard(
            source = Source.SOUNDCLOUD,
            signedInAs = acc.soundcloudName.takeIf { acc.soundcloudSignedIn },
            avatar = acc.soundcloudAvatar,
            busy = busy == Source.SOUNDCLOUD,
            hint = "Google, Facebook, Apple or email on the next screen",
            onSignOut = { graph.soundcloud.signOut(); clearWebLogins(); invalidateHomeCache() },
            onOther = { paste = Source.SOUNDCLOUD },
            onImport = { importFor = Source.SOUNDCLOUD },
            onCancel = cancel,
        ) {
            ProviderButton("Continue to SoundCloud", Source.SOUNDCLOUD.brandColor, Color.White) {
                if (app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) scHelp = true
                else if (supportsWebLogin) web = WebLogin.SOUNDCLOUD else paste = Source.SOUNDCLOUD
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    when (web) {
        WebLogin.YOUTUBE -> WebLoginDialog(
            "YouTube Music", GOOGLE_LOGIN,
            listOf("https://music.youtube.com", "https://www.youtube.com"), "SAPISID",
            desktopMode = false,
            onCookies = { c ->
            web = null
            run(Source.YOUTUBE_MUSIC) { graph.signInYouTube(c) }
        }, onDismiss = { web = null })
        WebLogin.SOUNDCLOUD -> WebLoginDialog(
            "SoundCloud", "https://soundcloud.com/signin",
            listOf("https://soundcloud.com", "https://m.soundcloud.com", "https://secure.soundcloud.com", "https://api-auth.soundcloud.com"),
            "oauth_token",
            desktopMode = true,
            onCookies = { c ->
            web = null
            run(Source.SOUNDCLOUD) { graph.soundcloud.signIn(c) }
        }, onDismiss = { web = null })
        null -> Unit
    }

    if (spotifyDev) {
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        var clientId by remember { mutableStateOf(settings.spotifyClientId) }
        AlertDialog(
            onDismissRequest = { spotifyDev = false },
            icon = { SourceBadge(Source.SPOTIFY, size = 32.dp) },
            title = { Text(t("Set up Spotify once")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(t("Spotify lets apps sign you in only through a registered app. It takes about a minute and you only do it once:"), style = MaterialTheme.typography.bodyMedium)
                    Step(1, "Open the Spotify dashboard and create an app (any name, select “Web API”).")
                    TextButton(onClick = { uri.openUri("https://developer.spotify.com/dashboard/create") }) { Text(t("Open Spotify dashboard")) }
                    Step(2, "In “Redirect URIs” add both of these:")
                    SPOTIFY_REDIRECTS.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(r)) }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
                        }
                    }
                    Step(3, "In “User Management” add the email of your Spotify account (needed while the app is in development mode).")
                    Step(4, "Copy the app's Client ID and paste it here:")
                    OutlinedTextField(
                        clientId, { clientId = it.trim() }, label = { Text(t("Client ID")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { if (BuildInfo.SPOTIFY_CLIENT_ID.isNotBlank()) Text(t("Built-in app")) },
                    )
                    Text(t("No client secret is needed — Nocturne uses Spotify's secure PKCE sign-in."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t("Then you sign in on Spotify's own page — with Google, Facebook, Apple or email."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    graph.settings.update { it.copy(spotifyClientId = clientId) }
                    spotifyDev = false
                    run(Source.SPOTIFY) { graph.spotify.signIn() }
                }, enabled = clientId.length >= 16 || (clientId.isEmpty() && BuildInfo.SPOTIFY_CLIENT_ID.isNotBlank())) { Text(t("Continue with Spotify")) }
            },
            dismissButton = { TextButton(onClick = { spotifyDev = false }) { Text(t("Cancel")) } },
        )
    }

    if (scHelp) {
        AlertDialog(
            onDismissRequest = { scHelp = false },
            icon = { SourceBadge(Source.SOUNDCLOUD, size = 32.dp) },
            title = { Text(t("SoundCloud on Windows")) },
            text = {
                Text(
                    t("SoundCloud's sign-in page doesn't load in the built-in browser on Windows. The easiest way: sign in to SoundCloud in Nocturne on your phone, then press “Show QR code” above and scan it — your account moves here in seconds."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { scHelp = false; paste = Source.SOUNDCLOUD }) { Text(t("Paste token")) } },
            dismissButton = {
                Row {
                    if (supportsWebLogin) TextButton(onClick = { scHelp = false; web = WebLogin.SOUNDCLOUD }) { Text(t("Try anyway")) }
                    TextButton(onClick = { scHelp = false }) { Text(t("Close")) }
                }
            },
        )
    }

    importFor?.let { ImportDialog(it) { importFor = null } }

    paste?.let { source ->
        var text by remember { mutableStateOf("") }
        val yt = source == Source.YOUTUBE_MUSIC
        AlertDialog(
            onDismissRequest = { paste = null },
            title = { Text(t(if (yt) "Sign in with browser cookies" else "Sign in with a token")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t(if (yt) "Open music.youtube.com in your browser while signed in, press F12 → Network, reload, click any request to music.youtube.com and copy the whole “Cookie” request header."
                        else "Open soundcloud.com while signed in, press F12 → Application → Cookies → soundcloud.com and copy the value of “oauth_token”."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(text, { text = it }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    paste = null
                    if (yt) run(source) { graph.signInYouTube(text) } else run(source) { graph.soundcloud.signIn(text) }
                }, enabled = text.isNotBlank()) { Text(t("Sign in")) }
            },
            dismissButton = { TextButton(onClick = { paste = null }) { Text(t("Cancel")) } },
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row {
        Text("$n.", fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(t(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProviderButton(label: String, container: Color, content: Color, mark: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (mark != null) {
            Text(mark, fontWeight = FontWeight.Black, color = Color(0xFF4285F4), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
        }
        Text(t(label), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AccountCard(
    source: Source,
    signedInAs: String?,
    busy: Boolean,
    hint: String,
    onSignOut: () -> Unit,
    onOther: () -> Unit,
    otherLabel: String = "Other ways to sign in",
    avatar: String? = null,
    busyHint: String? = null,
    onImport: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    signIn: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (avatar != null && signedInAs != null) Box(Modifier.size(40.dp)) {
                    Artwork(avatar, Modifier.size(40.dp), CircleShape)
                    SourceBadge(source, size = 16.dp, modifier = Modifier.align(Alignment.BottomEnd))
                } else SourceBadge(source, size = 36.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        signedInAs?.let { t("Signed in as %s", it) } ?: t(if (busy) "Connecting…" else hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (signedInAs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) Box(Modifier.size(28.dp)) { CircularProgressIndicator(strokeWidth = 2.dp) }
            }
            when {
                signedInAs != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onImport != null) app.nocturne.ui.kit.OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Import"))
                    }
                    TextButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Disconnect"))
                    }
                }
                busy -> {
                    busyHint?.let { Text(t(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (onCancel != null) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t("Cancel")) }
                    TextButton(onClick = onOther, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t(otherLabel)) }
                }
                else -> {
                    signIn()
                    TextButton(onClick = onOther, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t(otherLabel)) }
                }
            }
        }
    }
}

private val SPOTIFY_REDIRECTS = listOf("nocturne://spotify-callback", "http://127.0.0.1:43821/callback")
