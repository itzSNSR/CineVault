package com.example.ui.screens

import com.example.data.local.resumePosition
import com.example.ui.components.LiquidDots
import com.example.ui.components.LiquidFilterChip
import com.example.ui.components.LiquidGlassButton
import com.example.ui.theme.glass
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.FavoriteEntity
import com.example.data.local.HistoryEntity
import com.example.data.model.Episode
import com.example.data.model.MovieSubject
import com.example.data.model.StreamItem
import com.example.data.model.SubtitleItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.MovieViewModel
import com.example.util.EpisodeSearchIndex
import com.example.ui.viewmodel.ActiveDownload
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.net.Uri
import android.content.Intent
import com.example.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(viewModel: MovieViewModel) {
    val subject = viewModel.selectedSubject ?: return
    val allHistory by viewModel.historyList.collectAsState()
    val history = allHistory.filter { it.subjectId.removePrefix("moviebox_").removePrefix("anifam_") == subject.id.removePrefix("moviebox_").removePrefix("anifam_") }
    val last = history.firstOrNull()
    val resume = last?.takeIf { it.resumePosition > 0 }
    var expanded by remember(subject.id) { mutableStateOf(false) }
    var overflows by remember(subject.id) { mutableStateOf(false) }
    // Episode search is metadata-only: it does not fetch a stream or change playback.
    var episodeQuery by remember(subject.id) { mutableStateOf("") }
    val searchableEpisodes = remember(subject.id, subject.seasonList, viewModel.episodesList) {
        EpisodeSearchIndex.build(subject, viewModel.episodesList)
    }
    val displayedEpisodes = if (episodeQuery.isBlank()) {
        viewModel.episodesList
    } else {
        EpisodeSearchIndex.filter(searchableEpisodes, episodeQuery).map { it.episode }
    }
    val context = LocalContext.current
    val isFav by viewModel.isFavoriteFlow(subject.id).collectAsState(false)

    BackHandler {
        viewModel.closeDetail()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Backdrop and poster cover
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    val optimalBackdropUrl = androidx.compose.runtime.remember(subject.backdrop, subject.poster) {
                        val sourceUrl = subject.backdrop?.ifBlank { null } ?: subject.poster
                        com.example.util.ImageOptimizer.getOptimalUrl(sourceUrl, 1080)
                    }
                    val ambientColor = androidx.compose.runtime.remember(subject.averageHue) {
                        com.example.util.ImageOptimizer.parseAverageHue(subject.averageHue)
                    }
                    val placeholderPainter = androidx.compose.runtime.remember(subject.blurHash, ambientColor) {
                        com.example.util.ImageOptimizer.getBlurHashPainter(subject.blurHash, 30, 20)
                            ?: androidx.compose.ui.graphics.painter.ColorPainter(ambientColor)
                    }

                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(optimalBackdropUrl)
                            .memoryCacheKey(optimalBackdropUrl)
                            .diskCacheKey(optimalBackdropUrl)
                            .precision(coil.size.Precision.EXACT)
                            .crossfade(true)
                            .build(),
                        contentDescription = subject.name,
                        contentScale = ContentScale.Crop,
                        placeholder = placeholderPainter,
                        error = placeholderPainter,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Backdrop gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        ObsidianDark
                                    )
                                )
                            )
                    )

                    // Close Button
                    IconButton(
                        onClick = { viewModel.closeDetail() },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(top = 16.dp, start = 16.dp)
                            .align(Alignment.TopStart)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .testTag("detail_close_button")
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            }

            // Title block
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subject.name,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!subject.rating.isNullOrBlank() && subject.rating != "Not available" && subject.rating != "0" && subject.rating != "0.0") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = "Rating", tint = AccentAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(subject.rating, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        if (!subject.year.isNullOrBlank() && subject.year != "Not available") {
                            Text(subject.year, color = TextSecondary, fontSize = 14.sp)
                        }
                        if (!subject.duration.isNullOrBlank() && subject.duration != "Not available") {
                            Text(subject.duration, color = TextSecondary, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val episode = viewModel.episodesList.firstOrNull()
                    LiquidGlassButton(
                        text = if (resume != null) "Resume · ${formatTime(resume.seeTime)}" else "Play",
                        icon = Icons.Filled.PlayArrow,
                        onClick = {
                            if (resume != null) viewModel.resumeHistory(resume)
                            else if (episode != null && subject.isTvShow) viewModel.selectEpisode(episode)
                            else viewModel.selectMovieDirect()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("play_movie_button")
                    )
                    if (last?.isTvShow == true) Text("Last watched · S${last.season} E${last.episode}", color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.toggleFavorite(subject) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isFav) "Remove from watchlist" else "Add to watchlist",
                                tint = if (isFav) CinematicRed else TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        if (resume != null) TextButton(onClick = { viewModel.resumeHistory(resume, true) }) { Text("Start over") }
                        LiquidGlassButton(
                            text = if (subject.isTvShow) "Download E${episode?.episodeNumber ?: 1}" else "Download",
                            icon = Icons.Filled.Download,
                            enabled = !subject.isTvShow || episode != null,
                            onClick = {
                                if (subject.isTvShow && episode != null) viewModel.downloadSingleEpisode(context, subject, episode)
                                else viewModel.downloadMovie(context, subject)
                            }
                        )
                    }
                    // Genres row
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subject.genres) { gen ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = gen,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Audio Language / Dubs row
                    val audioDisplay = when {
                        subject.dubs.isNotEmpty() -> subject.dubs.joinToString(", ")
                        subject.resourceDetectors.isNotEmpty() -> subject.resourceDetectors.joinToString(", ") { it.name }
                        else -> "Not available"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Audio Language", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Audio: $audioDisplay",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description text
                    Text(
                        text = subject.description.ifEmpty { "No description available for this item." },
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
                    )
                    if (overflows || expanded) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (subject.isTvShow && !viewModel.isAniFam) {
                        Text(
                            text = "Seasons",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Season Selector Horizontal chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            items(viewModel.seasonsList) { s ->
                                LiquidFilterChip(
                                    selected = viewModel.selectedSeason == s,
                                    onClick = { viewModel.loadEpisodesForSeason(s) },
                                    label = "Season $s"
                                )
                            }
                        }
                    }
                }
            }

            // Search title or episode number across all seasons (including 1000+ episodes).
            if (subject.isTvShow) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            "Episodes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = episodeQuery,
                            onValueChange = { episodeQuery = it },
                            modifier = Modifier.fillMaxWidth().testTag("episode_search_field"),
                            singleLine = true,
                            label = { Text("Search by title or episode number") },
                            placeholder = { Text("E1000, S2E5, Luffy...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (episodeQuery.isNotEmpty()) {
                                    IconButton(onClick = { episodeQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear episode search")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )
                        if (episodeQuery.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${displayedEpisodes.size} matching episodes",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                if (viewModel.isLoadingEpisodes && episodeQuery.isBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LiquidDots(dotSize = 8.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Loading episodes...",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (displayedEpisodes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (episodeQuery.isNotBlank()) "No matching episodes" else "No episodes available for this season",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(displayedEpisodes) { ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .clickable { viewModel.selectEpisode(ep) }
                                .glass(14.dp)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ep.stillPath.ifEmpty { subject.poster },
                                contentDescription = ep.title,
                                contentScale = ContentScale.Crop, placeholder = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.DarkGray), error = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.DarkGray),
                                modifier = Modifier
                                    .size(width = 80.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Episode ${ep.episodeNumber}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmber
                                )
                                history.firstOrNull { it.episode == ep.episodeNumber && it.season == ep.seasonNumber }?.let {
                                    LinearProgressIndicator(progress = { (it.seeTime.toFloat() / it.totalTime.coerceAtLeast(1)).coerceIn(0f, 1f) })
                                }
                                Text(
                                    text = ep.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val context = LocalContext.current
                            IconButton(
                                onClick = { viewModel.downloadSingleEpisode(context, subject, ep) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = "Download Episode",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.PlayCircleFilled, contentDescription = "Play", tint = CinematicRed, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            item { Column(Modifier.padding(horizontal = 20.dp)) {
                    // Live Cast / Characters Section
                    if (subject.cast.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (viewModel.isAniFam) "Characters" else "Cast",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(subject.cast) { actor ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(80.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(ObsidianCard)
                                    ) {
                                        if (actor.avatar.isNotEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = actor.avatar,
                                                contentDescription = actor.name,
                                                contentScale = ContentScale.Crop, placeholder = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.DarkGray), error = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.DarkGray),
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = "Avatar placeholder",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = actor.name,
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = actor.role.ifEmpty { if (viewModel.isAniFam) "Character" else "Cast" },
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // More Like This Section
                    if (viewModel.detailRecommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "More Like This",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(viewModel.detailRecommendations) { movie ->
                                MovieGridItem(movie) {
                                    viewModel.selectSubject(movie)
                                }
                            }
                        }
                    }

            } }
            // Bottom space
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
