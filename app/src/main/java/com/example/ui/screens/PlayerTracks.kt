package com.example.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer

internal suspend fun PointerInputScope.playbackGestures(player: ExoPlayer, onTap: (Offset) -> Unit, onDoubleTap: (Offset) -> Unit) {
    var previous: Float? = null
    detectTapGestures(onTap = onTap, onDoubleTap = onDoubleTap,
        onLongPress = {
            previous = player.playbackParameters.speed
            player.setPlaybackSpeed(2f)
        },
        onPress = {
            try { tryAwaitRelease() } finally {
                previous?.let(player::setPlaybackSpeed)
                previous = null
            }
        })
}

/** Groups from the prepared media item, including adaptive stream audio and embedded captions. */
@Composable
internal fun StreamTracks(player: ExoPlayer, type: Int, onSelect: (String?) -> Unit = {}) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(current: Tracks) { tracks = current }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val groups = tracks.groups.filter { it.type == type }
    if (groups.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            items(group.length) { index ->
                val format = group.getTrackFormat(index)
                if (!group.isTrackSupported(index) || format.id?.startsWith("cv-sub-") == true) return@items
                val language = format.language
                val label = format.label ?: language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
                    ?: (if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Captions") + " ${index + 1}"
                FilterChip(selected = group.isTrackSelected(index), onClick = {
                    onSelect(language)
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
                }, label = { Text(label) })
            }
        }
    }
}
