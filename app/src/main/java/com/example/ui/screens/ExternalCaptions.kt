package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.example.util.CaptionDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private val captionClient = OkHttpClient.Builder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()

@Composable
internal fun ExternalCaptions(player: ExoPlayer, url: String, local: Boolean, retry: Int,
    delayMs: Int, scale: Float, color: Int, background: Float, position: Float,
    headers: Map<String, String> = emptyMap(),
    onFailure: (Boolean) -> Unit) {
    var timeline by remember(url, retry) { mutableStateOf<Subtitle?>(null) }
    val report by rememberUpdatedState(onFailure)
    LaunchedEffect(url, retry) {
        report(false)
        try {
            timeline = withContext(Dispatchers.IO) {
                val document = if (local) File(url).inputStream().use(CaptionDocument::read)
                    else {
                        // Fetch with the same headers as the stream itself, exactly
                        // like the reference player, which lets ExoPlayer download
                        // the subtitle through its own HTTP data source.
                        val request = Request.Builder().url(url).apply {
                            headers.forEach { (name, value) -> addHeader(name, value) }
                        }.build()
                        captionClient.newCall(request).execute().use { response ->
                            check(response.isSuccessful) { "HTTP ${response.code}" }
                            response.body!!.byteStream().use(CaptionDocument::read)
                        }
                    }
                document.timeline().also { require(it.eventTimeCount > 0) }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { report(true) }
    }
    var cues by remember(url, retry) { mutableStateOf<List<Cue>>(emptyList()) }
    LaunchedEffect(player, timeline, delayMs) {
        val track = timeline ?: return@LaunchedEffect
        while (true) {
            cues = track.getCues((player.currentPosition - delayMs) * 1000L)
            delay(75)
        }
    }
    AndroidView(factory = { SubtitleView(it) }, modifier = Modifier.fillMaxSize(), update = {
        it.setStyle(CaptionStyleCompat(color, android.graphics.Color.argb((background * 255).toInt(), 0, 0, 0),
            android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
        it.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f * scale)
        it.setBottomPaddingFraction(position)
        it.setCues(cues)
    })
}
