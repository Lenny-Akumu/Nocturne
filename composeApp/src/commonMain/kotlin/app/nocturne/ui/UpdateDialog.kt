package app.nocturne.ui

import app.nocturne.i18n.t
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import app.nocturne.ui.kit.Button
import app.nocturne.ui.kit.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import app.nocturne.ui.kit.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.BuildInfo
import app.nocturne.data.UpdateInfo
import app.nocturne.data.UpdateState
import kotlinx.coroutines.launch

private fun mb(bytes: Long) = (bytes / 1024.0 / 1024.0).let { ((it * 10).toLong() / 10.0).toString() }

@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updater = AppGraph.instance.updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(false) }
    val info: UpdateInfo? = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Failed -> s.info
        UpdateState.Idle -> null
    }
    AnimatedVisibility(
        visible = info != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding().padding(12.dp),
    ) {
        val i = info ?: return@AnimatedVisibility
        Card(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Update available — Nocturne %s", i.version), style = MaterialTheme.typography.titleMedium)
                        Text(
                            t("You have %s", BuildInfo.VERSION) + (if (i.size > 0) " • ${mb(i.size)} MB" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(notes && i.notes.isNotBlank(), enter = expandVertically(), exit = shrinkVertically()) {
                    Text(
                        i.notes.replace(Regex("[#*`]"), "").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.size(12.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            t("Downloading… %s%", (s.progress * 100).toInt()) +
                                (if (i.size > 0) " • ${mb((i.size * s.progress).toLong())} / ${mb(i.size)} MB" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    else -> {
                        (s as? UpdateState.Failed)?.let {
                            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (i.notes.isNotBlank()) TextButton(onClick = { notes = !notes }) { Text(t(if (notes) "Hide" else "What's new"), maxLines = 1, softWrap = false) }
                            TextButton(onClick = { updater.skip(i) }) { Text(t("Skip"), maxLines = 1, softWrap = false) }
                            TextButton(onClick = { updater.later() }) { Text(t("Later"), maxLines = 1, softWrap = false) }
                            Button(onClick = { scope.launch { updater.install(i) } }) {
                                Text(t(if (s is UpdateState.Failed) "Retry" else "Update"), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
