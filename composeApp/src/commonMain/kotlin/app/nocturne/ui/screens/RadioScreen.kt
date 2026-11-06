package app.nocturne.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.Radio
import app.nocturne.data.RadioCountry
import app.nocturne.data.RadioStation
import app.nocturne.data.flagOf
import app.nocturne.i18n.t
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.kit.TextButton
import kotlinx.coroutines.delay

@Composable
fun RadioView(padding: PaddingValues) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val player by graph.player.state.collectAsState()
    val country = settings.radioCountry.ifBlank { settings.contentCountry }.uppercase()
    var countries by remember { mutableStateOf<List<RadioCountry>>(emptyList()) }
    var stations by remember(country) { mutableStateOf<List<RadioStation>?>(null) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<RadioStation>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var genre by remember(country) { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { countries = Radio.countries() }
    LaunchedEffect(country) { stations = Radio.stations(country) }
    LaunchedEffect(query, country) {
        if (query.isBlank()) { found = null; return@LaunchedEffect }
        delay(400)
        found = Radio.search(query.trim(), country)
    }

    val playingId = player.current?.albumId?.removePrefix("radio:")
    val countryName = countries.firstOrNull { it.code == country }?.name ?: country
    val genres = remember(stations) {
        stations.orEmpty().flatMap { it.tags.split(" · ") }.filter { it.isNotBlank() && it.length < 18 }
            .groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }.take(12).map { it.key }
    }

    val jumpState = androidx.compose.foundation.lazy.rememberLazyListState()
    app.nocturne.ui.components.JumpBox(jumpState) {
    LazyColumn(state = jumpState, contentPadding = padding) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = true,
                    onClick = { picking = true },
                    label = { Text((flagOf(country) + " " + countryName).trim(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text(t("Find a station")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, t("Clear")) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        found?.let { list ->
            item { RadioHeader("Results") }
            if (list.isEmpty()) item { MessageBox(Icons.Rounded.Radio, "Nothing found", null) }
            items(list, key = { "f" + it.id }) { st -> StationRow(st, st.id == playingId, settings.radioFavorites.any { it.id == st.id }) { Radio.play(list, list.indexOf(st)) } }
            return@LazyColumn
        }

        if (settings.radioFavorites.isNotEmpty()) {
            item { RadioHeader("Favourite stations") }
            items(settings.radioFavorites, key = { "fav" + it.id }) { st ->
                StationRow(st, st.id == playingId, true) { Radio.play(settings.radioFavorites, settings.radioFavorites.indexOf(st)) }
            }
        }
        val recent = settings.radioRecent.filterNot { r -> settings.radioFavorites.any { it.id == r.id } }.take(6)
        if (recent.isNotEmpty()) {
            item { RadioHeader("Recently played") }
            items(recent, key = { "rec" + it.id }) { st -> StationRow(st, st.id == playingId, false) { Radio.play(recent, recent.indexOf(st)) } }
        }

        item { RadioHeader(t("Stations · %s", countryName), raw = true) }
        if (genres.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                item { FilterChip(genre == null, { genre = null }, label = { Text(t("All")) }, modifier = Modifier.padding(end = 6.dp)) }
                items(genres) { g -> FilterChip(genre == g, { genre = if (genre == g) null else g }, label = { Text(g) }, modifier = Modifier.padding(end = 6.dp)) }
            }
        }
        val list = stations
        when {
            list == null -> item { LoadingBox() }
            list.isEmpty() -> item { MessageBox(Icons.Rounded.Radio, "No stations", "Try another country.") }
            else -> {
                val shown = genre?.let { g -> list.filter { st -> st.tags.split(" · ").any { it.equals(g, ignoreCase = true) } } } ?: list
                items(shown, key = { "st" + it.id }) { st ->
                    StationRow(st, st.id == playingId, settings.radioFavorites.any { it.id == st.id }) { Radio.play(shown, shown.indexOf(st)) }
                }
            }
        }
    }
    }

    var confirmRu by remember { mutableStateOf(false) }
    if (picking) CountryPicker(countries, country, onDismiss = { picking = false }) { code ->
        picking = false
        if (code == "RU") confirmRu = true else graph.settings.update { it.copy(radioCountry = code) }
    }
    if (confirmRu) app.nocturne.ui.kit.AlertDialog(
        onDismissRequest = { confirmRu = false },
        title = { Text(t("Are you sure?")) },
        text = { Text(t("Are you sure about your choice?")) },
        confirmButton = { app.nocturne.ui.kit.TextButton(onClick = { confirmRu = false; graph.settings.update { it.copy(radioCountry = "RU") } }) { Text(t("Yes")) } },
        dismissButton = { app.nocturne.ui.kit.TextButton(onClick = { confirmRu = false }) { Text(t("No")) } },
    )
}

@Composable
private fun RadioHeader(text: String, raw: Boolean = false) {
    Text(
        if (raw) text else t(text), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun StationRow(st: RadioStation, playing: Boolean, favorite: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(st.favicon, Modifier.size(52.dp), RoundedCornerShape(12.dp), fallback = Icons.Rounded.Radio)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                st.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) cs.primary else cs.onSurface,
            )
            val info = listOfNotNull(
                st.tags.takeIf { it.isNotBlank() },
                st.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                st.codec.takeIf { it.isNotBlank() && it != "UNKNOWN" },
            ).joinToString(" · ")
            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Icon(Icons.Rounded.GraphicEq, null, tint = cs.primary, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = { Radio.toggleFavorite(st) }) {
            Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, t("Favourite"), tint = if (favorite) cs.primary else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountryPicker(countries: List<RadioCountry>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Country")) },
        text = {
            Column {
                OutlinedTextField(filter, { filter = it }, singleLine = true, placeholder = { Text(t("Search")) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth())
                if (countries.isEmpty()) LoadingBox()
                else {
                    val shown = countries.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) || it.code.equals(filter.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                        items(shown, key = { it.code }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(c.code) }.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(flagOf(c.code), modifier = Modifier.width(32.dp))
                                Text(
                                    c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (c.code == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (c.code == current) FontWeight.SemiBold else null,
                                )
                                Text(c.stations.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}
