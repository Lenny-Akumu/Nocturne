package app.nocturne.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.PlatformKind
import app.nocturne.data.DesignStyle
import app.nocturne.data.defaultDesignStyle
import app.nocturne.i18n.I18n
import app.nocturne.i18n.t
import app.nocturne.platformKind
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.sound.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Bump to show the setup again after an update that adds steps. */
const val ONBOARDING = 1

private enum class Step { LANGUAGE, STYLE, FONT, FLOATING, ICON, ACCOUNTS, REVEAL, DONE }

private val desktop get() = platformKind == PlatformKind.DESKTOP

private fun DesignStyle.icon(): ImageVector = when (this) {
    DesignStyle.MATERIAL -> Icons.Rounded.Android
    DesignStyle.GLASS -> Icons.Rounded.PhoneIphone
    DesignStyle.WINDOWS -> Icons.Rounded.Window
    DesignStyle.LINUX -> Icons.Rounded.AutoAwesome
    DesignStyle.ARCH -> Icons.Rounded.ChangeHistory
    DesignStyle.TERMINAL -> Icons.Rounded.Terminal
}

private fun DesignStyle.title(): String = when (this) {
    DesignStyle.MATERIAL -> "Android"
    DesignStyle.GLASS -> "Apple"
    else -> label
}

private fun DesignStyle.hint(): String = when (this) {
    DesignStyle.MATERIAL -> "Material You 3"
    DesignStyle.GLASS -> "Liquid Glass"
    DesignStyle.WINDOWS -> "Fluent · Aurora"
    DesignStyle.LINUX -> "Hyprland glass"
    DesignStyle.ARCH -> "Gruvbox TUI"
    DesignStyle.TERMINAL -> "Ghostty · cava"
}

/** First-run setup: language, design, font, mini player, app icon, accounts, then the app reveals itself. */
@Composable
fun Onboarding() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    if (Demo.enabled) return
    var step by remember { mutableStateOf(if (settings.onboarded >= ONBOARDING) Step.DONE else Step.LANGUAGE) }
    if (step == Step.DONE) return
    var ready by remember { mutableStateOf(false) }
    // let the startup splash finish first
    LaunchedEffect(Unit) { delay(if (settings.startupIntro) 2300 else 300); ready = true }
    if (!ready) return

    val steps = remember { Step.entries.filter { it != Step.DONE && (it != Step.FLOATING || desktop) } }
    fun next() {
        val i = steps.indexOf(step)
        if (step == Step.FLOATING) { FloatingEdit.preview = false; FloatingEdit.highlight = false }
        step = steps.getOrElse(i + 1) { Step.DONE }
    }
    fun finish(then: (() -> Unit)? = null) {
        FloatingEdit.preview = false
        FloatingEdit.highlight = false
        graph.settings.update { it.copy(onboarded = ONBOARDING) }
        step = Step.REVEAL
        then?.invoke()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        when (step) {
            Step.LANGUAGE -> Scrim { LanguageCard(onDone = ::next, onSkip = ::next) }
            Step.STYLE -> StyleStep(phone, onDone = ::next, onSkip = ::next)
            Step.FONT -> SidePanel(
                phone, "Font", "Pick how text looks. You see it live.",
                options = listOf("default" to "Default", "serif" to "Serif", "mono" to "Monospace", "cursive" to "Handwritten", "minecraft" to "Minecraft").map { (k, v) ->
                    Option(t(v), Icons.Rounded.TextFields, settings.fontStyle == k) { graph.settings.update { it.copy(fontStyle = k) } }
                },
                onDone = ::next, onSkip = ::next,
            )
            Step.FLOATING -> FloatingStep(phone, onDone = ::next, onSkip = ::next)
            Step.ICON -> SidePanel(phone, "App icon", "Shape and colour of Nocturne's icon.", options = emptyList(), onDone = ::next, onSkip = ::next) {
                app.nocturne.ui.screens.AppIconSettings(settings) { f -> graph.settings.update(f) }
            }
            Step.ACCOUNTS -> Scrim {
                AccountsCard(
                    onSignIn = { finish { graph.navigator.push(Screen.SettingsPage(app.nocturne.ui.screens.SettingsCategory.ACCOUNTS)) } },
                    onSkip = { finish() },
                )
            }
            Step.REVEAL -> Reveal { step = Step.DONE }
            Step.DONE -> Unit
        }
    }
}

private class Option(val label: String, val icon: ImageVector, val selected: Boolean, val hint: String? = null, val onClick: () -> Unit)

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        // swallow taps so nothing behind the card reacts
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.75f)),
        ) { content() }
    }
}

@Composable
private fun LanguageCard(onDone: () -> Unit, onSkip: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.padding(20.dp).widthIn(max = 460.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NocturneLogo(Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text("Welcome to Nocturne", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(t("Choose your language"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
            items(I18n.languages) { (code, name) ->
                val on = settings.uiLanguage == code
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) cs.primary.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { graph.settings.update { it.copy(uiLanguage = code) } }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (code == "system") t(name) else name, modifier = Modifier.weight(1f), color = if (on) cs.primary else cs.onSurface, fontWeight = if (on) FontWeight.SemiBold else null)
                    if (on) Icon(Icons.Rounded.Check, null, tint = cs.primary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ConfirmBar(onDone, onSkip)
    }
}

/** A quick flip through every design, then the vertical picker with live preview. */
@Composable
private fun StyleStep(phone: Boolean, onDone: () -> Unit, onSkip: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val styles = DesignStyle.entries
    var flipping by remember { mutableStateOf(true) }
    var shown by remember { mutableStateOf(styles.first()) }
    LaunchedEffect(Unit) {
        for (st in styles) {
            shown = st
            graph.settings.update { it.copy(designStyle = st) }
            delay(650)
        }
        graph.settings.update { it.copy(designStyle = if (desktop) DesignStyle.WINDOWS else defaultDesignStyle()) }
        flipping = false
    }
    if (flipping) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            AnimatedContent(shown, transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(180)))
            }, label = "flip") { st ->
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)).padding(horizontal = 26.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(st.icon(), null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(st.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(st.hint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }
    SidePanel(
        phone, "Design", "Tap a style to try it on the whole app.",
        options = styles.map { st ->
            Option(st.title(), st.icon(), settings.designStyle == st, st.hint()) { graph.settings.update { it.copy(designStyle = st) } }
        },
        onDone = onDone, onSkip = onSkip,
    )
}

@Composable
private fun FloatingStep(phone: Boolean, onDone: () -> Unit, onSkip: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    LaunchedEffect(Unit) {
        FloatingEdit.preview = true
        graph.settings.update { it.copy(floatingPlayer = true) }
        delay(400)
        FloatingEdit.highlight = true
        delay(2000)
        FloatingEdit.highlight = false
    }
    val off = !settings.floatingPlayer
    val panel = settings.floatingPlayer && settings.floatingMode == 1
    val options = buildList {
        add(Option(t("Horizontal"), Icons.Rounded.ViewAgenda, !off && !panel && !settings.floatingVertical) {
            FloatingEdit.preview = true
            graph.settings.update { it.copy(floatingPlayer = true, floatingVertical = false, floatingMode = 0) }
        })
        add(Option(t("Vertical"), Icons.Rounded.ViewWeek, !off && !panel && settings.floatingVertical) {
            FloatingEdit.preview = true
            graph.settings.update { it.copy(floatingPlayer = true, floatingVertical = true, floatingMode = 0) }
        })
        if (PanelSupport.available) add(Option(t("In the top panel"), Icons.Rounded.ViewDay, panel) {
            graph.settings.update { it.copy(floatingPlayer = true, floatingMode = 1) }
        })
        add(Option(t("Don't show"), Icons.Rounded.HideSource, off) {
            FloatingEdit.preview = false
            graph.settings.update { it.copy(floatingPlayer = false) }
        })
    }
    SidePanel(phone, "Mini player", "A small always-on-top player. It's glowing at the top of your screen.", options, onDone, onSkip)
}

/** Vertical menu on the right (bottom on phones) with the confirm bar set apart below it. */
@Composable
private fun SidePanel(
    phone: Boolean,
    title: String,
    subtitle: String,
    options: List<Option>,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    var visible by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title) { visible = true }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), contentAlignment = if (phone) Alignment.BottomCenter else Alignment.CenterEnd) {
        AnimatedVisibility(
            visible,
            enter = if (phone) slideInVertically { it } + fadeIn() else slideInHorizontally { it } + fadeIn(),
            exit = if (phone) slideOutVertically { it } + fadeOut() else slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(Modifier.then(if (phone) Modifier.fillMaxWidth() else Modifier.width(310.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer.copy(alpha = 0.96f))
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(24.dp)).padding(16.dp),
                ) {
                    Text(t(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(t(subtitle), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.heightIn(max = if (phone) 330.dp else 520.dp).verticalScroll(rememberScrollState())) {
                        options.forEach { o -> OptionRow(o) }
                        extra?.invoke()
                    }
                }
                Spacer(Modifier.height(10.dp))
                ConfirmBar(onDone, onSkip, Modifier.clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer.copy(alpha = 0.96f)).border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp)).padding(8.dp))
            }
        }
    }
}

@Composable
private fun OptionRow(o: Option) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .background(if (o.selected) cs.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = o.onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (o.selected) cs.primary else cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(o.icon, null, Modifier.size(22.dp), tint = if (o.selected) cs.onPrimary else cs.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.label, fontWeight = FontWeight.SemiBold, color = if (o.selected) cs.primary else cs.onSurface)
            o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        }
        if (o.selected) Icon(Icons.Rounded.Check, null, tint = cs.primary)
    }
}

@Composable
private fun ConfirmBar(onDone: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.clip(CircleShape).clickable(onClick = onSkip).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Skip"), color = cs.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.FastForward, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(cs.primary).clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, t("Save"), Modifier.size(28.dp), tint = cs.onPrimary) }
    }
}

@Composable
private fun AccountsCard(onSignIn: () -> Unit, onSkip: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.padding(20.dp).widthIn(max = 440.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(app.nocturne.model.Source.YOUTUBE_MUSIC, app.nocturne.model.Source.SPOTIFY, app.nocturne.model.Source.SOUNDCLOUD).forEach {
                app.nocturne.ui.components.SourceBadge(it, size = 36.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(t("Sign in to your accounts?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(t("Bring your likes, playlists and recommendations from YouTube Music, Spotify and SoundCloud."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.primary).clickable(onClick = onSignIn).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(t("Sign in"), color = cs.onPrimary, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(6.dp))
        ConfirmBar(onDone = onSkip, onSkip = onSkip)
    }
}

/** The app opens through an expanding circle, the logo swells and fades, the Nocturne voice plays. */
@Composable
private fun Reveal(onEnd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hole = remember { Animatable(0f) }
    val logo = remember { Animatable(0.6f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        runCatching { Sfx.voice() }
        launch { logo.animateTo(1.15f, spring(dampingRatio = 0.5f, stiffness = 220f)) }
        delay(350)
        launch { fade.animateTo(0f, tween(700, delayMillis = 250)) }
        hole.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        onEnd()
    }
    val accent = cs.primary
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Black), center, size.maxDimension))
            val r = hole.value * hypot(size.width, size.height) / 2 * 1.05f
            drawCircle(Color.Black, r, center, blendMode = BlendMode.Clear)
            if (r > 0f) drawCircle(accent.copy(alpha = (1f - hole.value) * 0.9f), r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx()))
        }
        NocturneLogo(Modifier.size(120.dp).graphicsLayer { scaleX = logo.value; scaleY = logo.value; alpha = fade.value })
    }
}
