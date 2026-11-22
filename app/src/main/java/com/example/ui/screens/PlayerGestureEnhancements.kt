package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.components.liquidGlass
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface PlayerGestureHud {
    data class Brightness(val percent: Int) : PlayerGestureHud
    data class Volume(val percent: Int, val level: Int, val max: Int) : PlayerGestureHud
    data class Seek(val positionMs: Long, val deltaMs: Long, val durationMs: Long) : PlayerGestureHud
}

/**
 * Adds left brightness, right volume and horizontal seek drags without
 * consuming taps. The existing detectTapGestures handler remains responsible
 * for single tap, double tap and long press.
 */
fun Modifier.playerDragGestures(
    player: ExoPlayer,
    seekEnabled: Boolean,
    onHud: (PlayerGestureHud?) -> Unit,
    onSeekCommit: (Long) -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val density = LocalDensity.current
    val touchSlop = with(density) { 12.dp.toPx() }

    pointerInput(player, seekEnabled, activity, audioManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var mode = Mode.None
            var total = androidx.compose.ui.geometry.Offset.Zero
            var startBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
            if (startBrightness < 0f) startBrightness = 0.5f
            val startVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
            val duration = player.duration
            val startPosition = player.currentPosition
            var targetPosition = startPosition
            val seekRangeMs = if (duration > 0) {
                minOf(duration.toFloat(), (duration * 0.25f).coerceIn(30_000f, 600_000f))
            } else 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                total += change.positionChange()

                if (mode == Mode.None && total.getDistance() > touchSlop) {
                    val horizontal = abs(total.x) > abs(total.y) * 1.15f
                    mode = when {
                        horizontal -> {
                            if (seekEnabled && duration > 0 && player.isCurrentMediaItemSeekable) {
                                Mode.Seek
                            } else {
                                // Consume the gesture so it cannot fall through as a tap
                                // when seek is locked or the media is unseekable.
                                Mode.Blocked
                            }
                        }
                        down.position.x < size.width / 2f -> Mode.Brightness
                        else -> Mode.Volume
                    }
                }

                when (mode) {
                    Mode.Brightness -> {
                        change.consume()
                        val value = (startBrightness - total.y / size.height * 1.5f).coerceIn(0.01f, 1f)
                        activity?.window?.let { window ->
                            val attributes = window.attributes
                            attributes.screenBrightness = value
                            window.attributes = attributes
                        }
                        onHud(PlayerGestureHud.Brightness((value * 100f).roundToInt()))
                    }

                    Mode.Volume -> {
                        change.consume()
                        if (maxVolume > 0) {
                            val value = (startVolume - total.y / size.height * maxVolume)
                                .roundToInt()
                                .coerceIn(0, maxVolume)
                            try {
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                            } catch (_: SecurityException) {
                                // Do not crash if a device policy blocks volume changes.
                            }
                            onHud(
                                PlayerGestureHud.Volume(
                                    percent = (value * 100f / maxVolume).roundToInt(),
                                    level = value,
                                    max = maxVolume
                                )
                            )
                        }
                    }

                    Mode.Seek -> {
                        change.consume()
                        if (seekRangeMs > 0f) {
                            targetPosition = (startPosition + (total.x / size.width * seekRangeMs).toLong())
                                .coerceIn(0L, duration)
                            onHud(
                                PlayerGestureHud.Seek(
                                    positionMs = targetPosition,
                                    deltaMs = targetPosition - startPosition,
                                    durationMs = duration
                                )
                            )
                        }
                    }

                    Mode.Blocked -> change.consume()

                    Mode.None -> Unit
                }
            }

            when (mode) {
                Mode.Seek -> {
                    onSeekCommit(targetPosition)
                    onHud(null)
                }
                Mode.Brightness, Mode.Volume -> onHud(null)
                Mode.Blocked, Mode.None -> Unit
            }
        }
    }
}

private enum class Mode { None, Brightness, Volume, Seek, Blocked }

@Composable
fun PlayerGestureHud(
    hud: PlayerGestureHud?,
    modifier: Modifier = Modifier
) {
    var lastHud by remember { mutableStateOf<PlayerGestureHud?>(null) }
    LaunchedEffect(hud) { if (hud != null) lastHud = hud }
    AnimatedVisibility(
        visible = hud != null,
        enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.92f),
        exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.94f),
        modifier = modifier
    ) {
        when (val value = hud ?: lastHud) {
            is PlayerGestureHud.Brightness -> HudLevelCard(
                icon = Icons.Filled.BrightnessHigh,
                title = "Brightness",
                percent = value.percent
            )
            is PlayerGestureHud.Volume -> HudLevelCard(
                icon = Icons.Filled.VolumeUp,
                title = "Volume",
                percent = value.percent
            )
            is PlayerGestureHud.Seek -> {
                Column(
                    modifier = Modifier
                        .liquidGlass(radius = 20.dp, tint = CinematicRed, alpha = 0.52f, elevation = 8.dp)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatHudTime(value.positionMs),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = (if (value.deltaMs >= 0) "+" else "-") + formatHudTime(abs(value.deltaMs)),
                        color = GlassHighlight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "of ${formatHudTime(value.durationMs)}",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun HudLevelCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    percent: Int
) {
    Row(
        modifier = Modifier
            .liquidGlass(radius = 22.dp, tint = CinematicRed, alpha = 0.50f, elevation = 8.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Column(Modifier.width(112.dp)) {
            Row(Modifier.padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("$percent%", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .height(4.dp)
                    .clip(CircleShape),
                color = CinematicRed,
                trackColor = Color.White.copy(alpha = 0.16f),
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
fun SeekLockButton(
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (locked) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 560f),
        label = "seek-lock-scale"
    )
    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(
                radius = 19.dp,
                tint = if (locked) CinematicRed else Color.Black,
                alpha = if (locked) 0.55f else 0.32f,
                elevation = 4.dp
            )
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .testTag("seek_lock_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (locked) "Unlock seeking" else "Lock seeking",
            tint = if (locked) Color.White else GlassHighlight,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun formatHudTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
