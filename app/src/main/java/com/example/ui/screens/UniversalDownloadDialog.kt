package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.EpisodeSearchField
import com.example.data.download.DownloadChoice
import com.example.data.download.DownloadEpisode
import com.example.data.download.DownloadOptions
import com.example.data.download.DownloadPreferences
import com.example.data.download.DownloadStorage
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.SubtitlePreference
import java.util.Locale

/**
 * One download configuration dialog for every provider.
 *
 * Nothing is enqueued until the user confirms; the chosen quality, audio and
 * subtitle languages are the exact values the provider reported, so no
 * resolution is ever presented that the source cannot deliver.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UniversalDownloadDialog(
    options: DownloadOptions,
    resolveVariant: suspend (DownloadOptions, String?, DownloadEpisode?) -> com.example.data.model.PlayInfo,
    onDismiss: () -> Unit,
    onConfirm: (DownloadChoice) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = DownloadPreferences.current

    val initialQuality = remember(options) {
        options.qualities.firstOrNull { it.equals(options.defaultQuality, true) }
            ?: options.qualities.firstOrNull { it.equals("1080p", true) }
            ?: options.qualities.firstOrNull().orEmpty()
    }
    var quality by remember(options) { mutableStateOf(initialQuality) }
    var audio by remember(options) { mutableStateOf(
        options.audios.firstOrNull { settings.preferredAudio != null && (it.name.equals(settings.preferredAudio, true) || it.resourceId == settings.preferredAudio) }
            ?: options.audios.firstOrNull { it.resourceId == options.selectedResourceId } ?: options.audios.firstOrNull()
    ) }
    var fallback by remember(options) { mutableStateOf(com.example.data.download.QualityFallback.EXACT) }
    var subtitles by remember(options) {
        mutableStateOf(options.subtitleLanguages.filter { settings.autoSubtitles && requiresSubtitle(it, settings.preferredSubtitles) }.toSet())
    }
    var season by remember(options) { mutableStateOf(options.preselectedSeason) }
    var query by remember { mutableStateOf("") }
    var fromEpisode by remember { mutableStateOf("") }
    var toEpisode by remember { mutableStateOf("") }
    val selected = remember(options) { androidx.compose.runtime.mutableStateListOf<DownloadEpisode>().apply {
        options.episodes.firstOrNull { it.season == options.preselectedSeason && it.number == options.preselectedEpisode }?.let { add(it) }
    } }

    val seasonEpisodes = remember(options, season) {
        options.episodes.filter { it.season == season }.sortedBy { it.number }
    }
    val visibleEpisodes = remember(seasonEpisodes, query) {
        val text = query.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) seasonEpisodes else seasonEpisodes.filter {
            it.number.toString().contains(text) || it.episodeTitleContains(text) || it.label.lowercase(Locale.ROOT).contains(text)
        }
    }
    val probe = selected.lastOrNull() ?: seasonEpisodes.firstOrNull()
    val initialProbe = options.episodes.firstOrNull {
        it.season == options.preselectedSeason && (options.preselectedEpisode == null || it.number == options.preselectedEpisode)
    }
    val resource = audio?.resourceId ?: options.selectedResourceId
    val initial = resource == options.selectedResourceId && probe?.id == initialProbe?.id
    var qualities by remember(options, resource, probe) { mutableStateOf(if (initial) options.qualities else emptyList()) }
    var languages by remember(options, resource, probe) { mutableStateOf(if (initial) options.subtitleLanguages else emptyList()) }
    var loading by remember(options, resource, probe) { mutableStateOf(!initial) }
    androidx.compose.runtime.LaunchedEffect(options, resource, probe) {
        if (!initial) {
            val info = resolveVariant(options, resource, probe)
            qualities = info.streamList.filter { it.url.isNotBlank() }.map { it.quality }.filter(String::isNotBlank).distinct()
            languages = info.subTitleList.map { it.language }.distinct()
        }
        if (qualities.none { it == quality }) quality = qualities.firstOrNull { it == options.defaultQuality } ?: qualities.firstOrNull().orEmpty()
        subtitles = languages.filter { lang -> subtitles.any { com.example.data.download.OfflineSubtitlePack.matches(lang, it) } }.toSet()
        loading = false
    }
    val estimatedBytes = options.durationMinutes?.takeIf { !options.isSeries && quality.isNotBlank() }
        ?.let { DownloadStorage.estimateEpisodeBytes(quality, it) } ?: 0L
    val directory = remember { DownloadStorage.directory(context) }
    val available = remember { DownloadStorage.availableBytes(directory) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianDark
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = options.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                        .background(ObsidianCard)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        options.subjectTitle,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (options.isSeries) "Download episodes" else "Download movie",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (options.isSeries) {
                    item {
                        SectionTitle("Season")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.seasons.sorted().forEach { value ->
                                FilterChip(
                                    selected = season == value,
                                    onClick = {
                                        season = value
                                        fromEpisode = ""
                                        toEpisode = ""
                                    },
                                    label = { Text("S${value.toString().padStart(2, '0')}") }
                                )
                            }
                        }
                    }
                    item {
                        EpisodeSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            testTag = "download_episode_search"
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = fromEpisode,
                                onValueChange = { fromEpisode = it.filter(Char::isDigit).take(4) },
                                label = { Text("From", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = toEpisode,
                                onValueChange = { toEpisode = it.filter(Char::isDigit).take(4) },
                                label = { Text("To", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val low = fromEpisode.toIntOrNull() ?: seasonEpisodes.minOfOrNull { it.number } ?: 1
                                    val high = toEpisode.toIntOrNull() ?: seasonEpisodes.maxOfOrNull { it.number } ?: low
                                    val range = seasonEpisodes.filter { it.number in low..high }
                                    selected.removeAll { it.season == season }
                                    selected.addAll(range)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianCard),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) { Text("Range", fontSize = 12.sp) }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                val all = seasonEpisodes
                                if (all.all { ep -> selected.any { it.id == ep.id } }) {
                                    selected.removeAll { it.season == season }
                                } else {
                                    selected.removeAll { it.season == season }
                                    selected.addAll(all)
                                }
                            }) { Text("Select / deselect season", fontSize = 12.sp) }
                            Text(
                                "${selected.size} selected",
                                color = CinematicRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(visibleEpisodes, key = { it.id }) { episode ->
                        val isSelected = selected.any { it.id == episode.id }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isSelected) selected.removeAll { it.id == episode.id }
                                else selected.add(episode)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF271B1B) else ObsidianCard
                            ),
                            border = if (isSelected) BorderStroke(1.dp, CinematicRed) else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Episode ${episode.number.toString().padStart(2, '0')}",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (episode.title.isNotBlank()) {
                                        Text(
                                            episode.title,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier.size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) CinematicRed else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                                        if (!isSelected) {
                                            drawCircle(
                                                color = Color(0xFF6B7280),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                                            )
                                        }
                                    }
                                    if (isSelected) Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (visibleEpisodes.isEmpty()) {
                        item { Text("No episodes match this search.", color = TextSecondary, fontSize = 13.sp) }
                    }
                }

                item {
                    SectionTitle("Video quality")
                    if (qualities.isEmpty()) {
                        Text(if (loading) "Checking this source…" else "The provider did not report downloadable qualities.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            qualities.forEach { value ->
                                FilterChip(
                                    selected = quality.equals(value, true),
                                    onClick = { quality = value },
                                    label = { Text(value, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                if (options.isSeries && qualities.isNotEmpty()) {
                    item {
                        SectionTitle("When a quality is unavailable")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.example.data.download.QualityFallback.entries.forEach { policy ->
                                FilterChip(selected = fallback == policy, onClick = { fallback = policy }, label = { Text(policy.label) })
                            }
                        }
                    }
                }

                if (options.audios.isNotEmpty()) {
                    item {
                        SectionTitle("Audio / source")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.audios.forEach { value ->
                                FilterChip(
                                    selected = audio == value,
                                    onClick = { audio = value },
                                    label = { Text(value.name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                if (languages.isNotEmpty()) {
                    item {
                        SectionTitle("Subtitles")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = subtitles.isEmpty(),
                                onClick = { subtitles = emptySet() },
                                label = { Text("None", fontSize = 12.sp) }
                            )
                            languages.forEach { value ->
                                FilterChip(
                                    selected = subtitles.any { it.equals(value, true) },
                                    onClick = {
                                        subtitles = if (subtitles.any { it.equals(value, true) }) {
                                            subtitles.filterNot { it.equals(value, true) }.toSet()
                                        } else subtitles + value
                                    },
                                    label = { Text(value, fontSize = 12.sp) }
                                )
                            }
                        }
                        Text(
                            "Subtitle files are saved next to the video and are matched per episode.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                item {
                    SummaryRow("Items", if (options.isSeries) "${selected.size} episodes" else "1 movie")
                    SummaryRow("Estimated size", if (estimatedBytes > 0) "≈ ${DownloadStorage.formatBytes(estimatedBytes)}" else "Unknown")
                    SummaryRow("Storage free", DownloadStorage.formatBytes(available))
                    SummaryRow("Destination", directory.absolutePath, small = true)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Button(
                    onClick = {
                        if (quality.isBlank()) return@Button
                        if (options.isSeries && selected.isEmpty()) return@Button
                        onConfirm(
                            DownloadChoice(
                                episodes = selected.toList(),
                                quality = quality,
                                resourceId = audio?.resourceId ?: options.selectedResourceId,
                                sourceId = options.sourceId,
                                provider = options.provider,
                                fallback = fallback,
                                subtitleLanguages = subtitles
                            )
                        )
                    },
                    enabled = !loading && quality in qualities && quality.isNotBlank() && (!options.isSeries || selected.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinematicRed,
                        disabledContainerColor = Color(0xFF3F3F46)
                    )
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (options.isSeries) "Download ${selected.size} episode${if (selected.size == 1) "" else "s"}" else "Download",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun SummaryRow(label: String, value: String, small: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(
            value,
            color = if (small) TextSecondary else GlassHighlight,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = if (small) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun DownloadEpisode.episodeTitleContains(text: String): Boolean =
    title.lowercase(Locale.ROOT).contains(text)

private fun requiresSubtitle(language: String, preferred: Set<String>): Boolean {
    if (preferred.isEmpty()) return false
    val tag = SubtitlePreference.languageTag(language)
    return preferred.any { it.equals(tag, true) || it.equals(language, true) }
}
