package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.ui.components.liquidGlass
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val subtitleColors = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Warm" to 0xFFFFE082.toInt(),
    "Cyan" to 0xFFB2EBF2.toInt(),
    "Green" to 0xFFC5E1A5.toInt()
)

/**
 * The single advanced control surface shared by every provider and by offline
 * playback. It only exposes what Media3 can actually do for the current media:
 * track lists come from the prepared player, subtitle languages from the
 * episode's own provider response.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UniversalPlayerSheet(
    player: ExoPlayer,
    speed: Float,
    onSpeed: (Float) -> Unit,
    resizeMode: Int,
    onResizeMode: (Int) -> Unit,
    subtitleLanguages: List<String>,
    selectedSubtitleLanguage: String?,
    subtitlesOff: Boolean,
    onSelectSubtitle: (String?) -> Unit,
    onRetrySubtitles: () -> Unit,
    subtitleScale: Float,
    onSubtitleScale: (Float) -> Unit,
    subtitleColor: Int,
    onSubtitleColor: (Int) -> Unit,
    subtitleBackground: Float,
    onSubtitleBackground: (Float) -> Unit,
    subtitlePosition: Float,
    onSubtitlePosition: (Float) -> Unit,
    subtitleDelayMs: Int,
    onSubtitleDelay: (Int) -> Unit,
    onDismiss: () -> Unit,
    onEmbedded: (String?) -> Unit = {},
    onAutoSubtitle: (() -> Unit)? = null,
    captionFailed: Boolean = false,
    online: Boolean = true,
    showSpeed: Boolean = true,
    extra: @Composable () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ObsidianDark) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Playback settings", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { extra() }
                if (showSpeed) item {
                    Section("Speed")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        speedOptions.forEach { value ->
                            FilterChip(
                                selected = kotlin.math.abs(speed - value) < 0.01f,
                                onClick = { onSpeed(value) },
                                label = { Text(speedLabel(value), fontSize = 12.sp) }
                            )
                        }
                    }
                    Text(
                        "Long-press anywhere on the video for temporary 2× playback; release restores this speed.",
                        color = TextSecondary, fontSize = 11.sp
                    )
                }

                item {
                    Section("Aspect ratio")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
                            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        ).forEach { (label, mode) ->
                            FilterChip(
                                selected = resizeMode == mode,
                                onClick = { onResizeMode(mode) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                item {
                    Section("Audio")
                    StreamTracks(player, C.TRACK_TYPE_AUDIO)
                }

                item {
                    Section("Subtitles")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = subtitlesOff,
                            onClick = { onSelectSubtitle(null) },
                            label = { Text("Off", fontSize = 12.sp) }
                        )
                        onAutoSubtitle?.let { auto ->
                            TextButton(onClick = auto) { Text("Auto") }
                        }
                        subtitleLanguages.distinct().forEach { language ->
                            FilterChip(
                                selected = !subtitlesOff && selectedSubtitleLanguage.equals(language, true),
                                onClick = { onSelectSubtitle(language) },
                                label = { Text(language, fontSize = 12.sp) }
                            )
                        }
                    }
                    StreamTracks(player, C.TRACK_TYPE_TEXT, onEmbedded)
                    if (online && (subtitleLanguages.isEmpty() || captionFailed)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (captionFailed) "Could not read this caption file." else "No external captions reported.",
                                color = Color(0xFFFBBF24), fontSize = 12.sp, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRetrySubtitles) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = CinematicRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry", color = CinematicRed, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Section("Subtitle appearance")
                    Stepper("Text size", subtitleScale, 0.75f, 1.75f, onSubtitleScale)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subtitleColors.forEach { (name, value) ->
                            FilterChip(
                                selected = subtitleColor == value,
                                onClick = { onSubtitleColor(value) },
                                label = { Text(name, fontSize = 12.sp) }
                            )
                        }
                    }
                    Stepper("Background", subtitleBackground, 0f, 0.8f, onSubtitleBackground)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low" to 0.04f, "Middle" to 0.10f, "High" to 0.18f).forEach { (name, value) ->
                            FilterChip(
                                selected = kotlin.math.abs(subtitlePosition - value) < 0.001f,
                                onClick = { onSubtitlePosition(value) },
                                label = { Text(name, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                if (!subtitlesOff && selectedSubtitleLanguage != null) item {
                    Section("Subtitle timing")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                subtitleDelayMs == 0 -> "In sync"
                                subtitleDelayMs > 0 -> "+${"%.2f".format(subtitleDelayMs / 1000f)}s"
                                else -> "${"%.2f".format(subtitleDelayMs / 1000f)}s"
                            },
                            color = GlassHighlight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSubtitleDelay(subtitleDelayMs - 250) }) { Text("−0.25s", fontSize = 12.sp) }
                        TextButton(onClick = { onSubtitleDelay(0) }) { Text("Reset", fontSize = 12.sp) }
                        TextButton(onClick = { onSubtitleDelay(subtitleDelayMs + 250) }) { Text("+0.25s", fontSize = 12.sp) }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Stepper(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange((value - 0.125f).coerceAtLeast(min)) }) { Text("−", fontSize = 18.sp) }
            Text("${(value * 100).toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onChange((value + 0.125f).coerceAtMost(max)) }) { Text("+", fontSize = 18.sp) }
        }
    }
}

@Composable
fun FullScreenLockButton(
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    seekOnly: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (locked) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 560f),
        label = "full-lock-scale"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidGlass(
                radius = 19.dp,
                tint = if (locked) Color(0xFF0EA5E9) else Color.Black,
                alpha = if (locked) 0.6f else 0.32f,
                elevation = 4.dp
            )
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.ScreenLockPortrait else Icons.Filled.LockOpen,
            contentDescription = if (locked) "Unlock player" else if (seekOnly) "Lock seeking" else "Lock all controls",
            tint = if (locked) Color.White else GlassHighlight,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun speedLabel(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}×" else "${value}×"

internal enum class PlayerLock { UNLOCKED, SEEK, FULL }

internal class DisplayPreferences(prefs: android.content.SharedPreferences) {
    var resize by mutableIntStateOf(prefs.getInt("resize", AspectRatioFrameLayout.RESIZE_MODE_FIT))
    var scale by mutableFloatStateOf(prefs.getFloat("scale", 1f))
    var color by mutableIntStateOf(prefs.getInt("color", android.graphics.Color.WHITE))
    var background by mutableFloatStateOf(prefs.getFloat("background", .35f))
    var position by mutableFloatStateOf(prefs.getFloat("position", .08f))
    var fullLock by mutableStateOf(prefs.getBoolean("full_lock", false))
    var decoder by mutableStateOf(prefs.getString("decoder", "Automatic") ?: "Automatic")
}

@Composable
internal fun rememberDisplayPreferences(): DisplayPreferences {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("player_display", 0) }
    val options = remember(prefs) { DisplayPreferences(prefs) }
    LaunchedEffect(options.resize, options.scale, options.color, options.background, options.position, options.fullLock, options.decoder) {
        prefs.edit().putInt("resize", options.resize).putFloat("scale", options.scale).putInt("color", options.color)
            .putFloat("background", options.background).putFloat("position", options.position)
            .putBoolean("full_lock", options.fullLock).putString("decoder", options.decoder).apply()
    }
    return options
}

internal fun renderers(context: android.content.Context, decoder: String) =
    androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        setMediaCodecSelector { mime, secure, tunnel ->
            val codecs = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunnel)
            if (!mime.startsWith("video/")) codecs else when (decoder) {
                "Software" -> codecs.sortedByDescending { it.softwareOnly }
                "Hardware" -> codecs.sortedByDescending { it.hardwareAccelerated }
                else -> codecs
            }
        }
    }

@Composable
internal fun PlaybackPreferences(options: DisplayPreferences) {
    Section("Lock button")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!options.fullLock, { options.fullLock = false }, { Text("Seek only") })
        FilterChip(options.fullLock, { options.fullLock = true }, { Text("Full lock") })
    }
    Section("Preferred decoder")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Automatic", "Hardware", "Software").forEach { mode ->
            FilterChip(options.decoder == mode, { options.decoder = mode }, { Text(mode) })
        }
    }
}

@Composable
internal fun PlayerLockControl(lock: PlayerLock, preferFull: Boolean, modifier: Modifier, change: (PlayerLock) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    FullScreenLockButton(locked = lock != PlayerLock.UNLOCKED, seekOnly = !preferFull, modifier = modifier.focusRequester(focus), onToggle = {
        change(if (lock != PlayerLock.UNLOCKED) PlayerLock.UNLOCKED else if (preferFull) PlayerLock.FULL else PlayerLock.SEEK)
    })
    LaunchedEffect(lock) { if (lock == PlayerLock.FULL) focus.requestFocus() }
}

@Composable
private fun <T> OptionRow(label: String, options: List<T>, current: T?, name: (T) -> String, select: (T) -> Unit) {
    if (options.isEmpty()) return
    Section(label)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { item -> FilterChip(item == current, { select(item) }, { Text(name(item)) }) }
    }
}

@Composable
internal fun PlayerSourceSettings(vm: com.example.ui.viewmodel.MovieViewModel, info: com.example.data.model.PlayInfo,
    subject: com.example.data.model.MovieSubject, casting: Boolean, display: DisplayPreferences, close: () -> Unit) {
    PlaybackPreferences(display)
    OptionRow("Video quality", info.streamList.distinctBy { it.quality }, vm.selectedStream, { it.quality }) {
        vm.selectedStream = it
        vm.setDefaultStreamQualityPref(it.quality)
    }
    OptionRow("Audio / source", (vm.originalSubjectDetail ?: subject).resourceDetectors, vm.selectedResource, { it.name }) {
        close()
        vm.selectLanguageResource(it)
    }
    if (subject.isTvShow) {
        OptionRow("Season", vm.seasonsList, vm.selectedSeason, { it.toString() }, vm::loadEpisodesForSeason)
        var query by remember { mutableStateOf("") }
        com.example.ui.components.EpisodeSearchField(query, { query = it })
        OptionRow("Episode", com.example.util.EpisodeSearch.filter(vm.episodesList, query), vm.currentEpisode, { it.episodeNumber.toString() }) {
            close()
            vm.selectEpisode(it)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (casting) "Casting" else "Cast to TV", color = TextPrimary)
        AndroidView(factory = { ctx ->
            val themed = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)
            androidx.mediarouter.app.MediaRouteButton(themed).apply {
                com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(themed, this)
            }
        }, modifier = Modifier.size(48.dp))
    }
}

internal fun applySubtitleStyle(view: androidx.media3.ui.PlayerView, display: DisplayPreferences) {
    view.subtitleView?.apply {
        setStyle(androidx.media3.ui.CaptionStyleCompat(display.color, android.graphics.Color.argb((255 * display.background).toInt(), 0, 0, 0),
            android.graphics.Color.TRANSPARENT, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f * display.scale)
        setBottomPaddingFraction(display.position)
    }
}
