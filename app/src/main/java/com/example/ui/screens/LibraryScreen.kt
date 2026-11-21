package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.data.local.*
import com.example.ui.components.LiquidNavItem
import com.example.ui.components.LiquidNavigationBar
import com.example.ui.components.liquidGlass
import com.example.ui.theme.*
import com.example.ui.viewmodel.MovieViewModel

private val destinations = listOf(
    Triple("home", "Home", Icons.Default.Home),
    Triple("search", "Search", Icons.Default.Search),
    Triple("livetv", "Live TV", Icons.Default.LiveTv),
    Triple("download", "Downloads", Icons.Default.Download),
    Triple("library", "Library", Icons.Default.VideoLibrary)
)

@Composable
fun CapsuleNavigation(active: String, restricted: Boolean, navigate: (String) -> Unit) {
    val tabs = if (restricted) destinations.filter { it.first == "download" } else destinations
    val selected = tabs.indexOfFirst { it.first == active || (it.first == "library" && active in listOf("settings", "history", "watchlist", "manage_categories")) }
    val items = tabs.map { (id, label, icon) -> LiquidNavItem(id, label, icon) }
    LiquidNavigationBar(
        items = items,
        selectedIndex = selected.coerceAtLeast(0),
        onSelected = { index -> navigate(items[index].id) }
    )
}

@Composable
fun ContinueWatching(vm: MovieViewModel) {
    val history by vm.historyList.collectAsState()
    val entries = history.distinctBy { it.subjectId.removePrefix("moviebox_").removePrefix("anifam_") }.filter { it.resumePosition > 0 }
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = GlassSpacing)) {
        Text("Continue Watching", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(GlassSpacing)) {
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.width(180.dp).liquidGlass(radius = 16.dp, alpha = 0.42f).clickable { vm.resumeHistory(entry) }) {
                    AsyncImage(entry.poster, null, Modifier.fillMaxWidth().height(92.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(10.dp)) {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val ep = if (entry.isTvShow) "S${entry.season} E${entry.episode} · " else ""
                        Text("Resume · $ep${formatTime(entry.seeTime)}", color = GlassHighlight, style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(progress = { (entry.seeTime.toFloat() / entry.totalTime.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RecentSearches(vm: MovieViewModel, source: String, onSearch: (String) -> Unit) {
    val searches by vm.recentSearches.collectAsState()
    val relevant = searches.filter { it.source == source }
    if (relevant.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(relevant, key = { it.normalized }) { search ->
            InputChip(selected = false, onClick = { vm.rememberSearch(source, search.term); onSearch(search.term) },
                label = { Text(search.term, maxLines = 1) }, trailingIcon = {
                    IconButton(onClick = { vm.forgetSearch(search) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Delete ${search.term}", modifier = Modifier.size(16.dp))
                    }
                })
        }
    }
}

@Composable
fun LibraryScreen(vm: MovieViewModel, navigate: (String) -> Unit, repeatSearch: (RecentSearch) -> Unit) {
    val searches by vm.recentSearches.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(GlassSpacing)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Library", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
        } }
        item { ContinueWatching(vm) }
        items(listOf("Watchlist", "History")) { label ->
            Text(label, Modifier.fillMaxWidth().glass().clickable { navigate(label.lowercase()) }.padding(18.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (searches.isNotEmpty()) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.clearSearchHistory() }) { Text("Clear all") }
            } }
            items(searches) { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { repeatSearch(entry) }.padding(8.dp)) {
                        Text(entry.term)
                        Text(entry.source, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    IconButton(onClick = { vm.forgetSearch(entry) }) { Icon(Icons.Default.Close, "Delete ${entry.term}") }
                }
            }
        }
    }
}
