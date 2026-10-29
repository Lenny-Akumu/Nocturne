package app.nocturne.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.party.ChatMsg
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.kit.ModalBottomSheet
import app.nocturne.ui.kit.OutlinedTextField
import app.nocturne.ui.kit.TextButton
import kotlin.math.sin
import kotlin.random.Random

private val Emojis = listOf("🔥", "😍", "👏", "😂", "😭", "🕺", "🤯", "💯")

/** Listen together: a reaction rail, flying emoji from everyone in the session, and the chat. */
@Composable
fun BoxScope.PartyReactions() {
    val party = AppGraph.instance.party
    val code by party.code.collectAsState()
    val joined by party.joined.collectAsState()
    if (code == null && joined == null) return
    val chat by party.chat.collectAsState()
    var chatOpen by remember { mutableStateOf(false) }
    val flying = remember { mutableStateListOf<ChatMsg>() }
    var seen by remember { mutableStateOf(chat.lastOrNull()?.id) }
    LaunchedEffect(chat) {
        val start = chat.indexOfFirst { it.id == seen }
        chat.drop(start + 1).filter { it.reaction }.forEach { flying += it }
        seen = chat.lastOrNull()?.id
    }
    flying.forEach { m -> key(m.id) { FlyingEmoji(m) { flying.remove(m) } } }
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.25f)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emojis.take(5).forEach { e ->
            Text(e, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { party.say(e, reaction = true) }.padding(7.dp))
        }
        val unread = chat.count { !it.reaction }
        Box(Modifier.clip(CircleShape).clickable { chatOpen = true }.padding(8.dp)) {
            Icon(Icons.Rounded.ChatBubbleOutline, t("Chat"), Modifier.size(22.dp), tint = Color.White)
            if (unread > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
    if (chatOpen) PartyChatSheet { chatOpen = false }
}

@Composable
private fun BoxScope.FlyingEmoji(m: ChatMsg, done: () -> Unit) {
    val p = remember { Animatable(0f) }
    val lane = remember { Random.nextFloat() }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(2600, easing = FastOutSlowInEasing))
        done()
    }
    Column(
        Modifier.align(Alignment.BottomEnd).padding(end = (24 + lane * 90).dp, bottom = 90.dp)
            .graphicsLayer {
                val v = p.value
                translationY = -v * size.height * 9f
                translationX = sin(v * 9f + lane * 6f) * 18.dp.toPx()
                alpha = if (v < 0.75f) 1f else (1f - v) / 0.25f
                val s = if (v < 0.12f) v / 0.12f * 1.25f else 1.25f - 0.25f * ((v - 0.12f) / 0.88f)
                scaleX = s; scaleY = s
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(m.text, fontSize = 34.sp)
        Text(m.from, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun PartyChatSheet(onDismiss: () -> Unit) {
    val party = AppGraph.instance.party
    val chat by party.chat.collectAsState()
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(chat.size) { if (chat.isNotEmpty()) list.animateScrollToItem(chat.lastIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.7f).padding(horizontal = 16.dp)) {
            Text(t("Chat"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(chat, key = { it.id }) { m ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(m.from, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(m.text, style = if (m.reaction) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Emojis.forEach { e -> Text(e, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { party.say(e, reaction = true) }.padding(6.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text(t("Message")) }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { party.say(text); text = "" }, enabled = text.isNotBlank()) { Text(t("Send")) }
            }
        }
    }
}
