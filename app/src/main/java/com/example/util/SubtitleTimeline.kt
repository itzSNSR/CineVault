package com.example.util

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shifts a text subtitle file in time without touching the video pipeline.
 *
 * Media3 renders SRT/VTT directly but has no built-in delay. The adjusted file
 * is produced from the episode's own subtitle URL, cached per offset, and then
 * attached exactly like the original one. A failure simply returns null so the
 * player keeps the unshifted track (or no track) and playback continues.
 */
object SubtitleTimeline {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cueTime = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")

    /** @return a local file with shifted timings, or null when it cannot be built. */
    fun adjust(context: Context, url: String, format: String?, offsetMs: Long): File? {
        if (url.isBlank() || offsetMs == 0L) return null
        val isVtt = format.equals("vtt", true) || url.contains(".vtt", true)
        if (!isVtt && !format.equals("srt", true) && !url.contains(".srt", true)) return null
        val folder = File(context.cacheDir, "subtitle-timing").apply { if (!exists()) mkdirs() }
        val name = sha1("$url|$offsetMs|$isVtt") + if (isVtt) ".vtt" else ".srt"
        val target = File(folder, name)
        if (target.isFile && target.length() > 0) return target
        return try {
            val text = if (url.startsWith("http", true)) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) CineVault/4.8")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.string() ?: return null
                }
            } else {
                val file = File(url.removePrefix("file://"))
                if (!file.isFile) return null
                file.readText()
            }
            val shifted = if (isVtt) shiftVtt(text, offsetMs) else shiftSrt(text, offsetMs)
            val temp = File(folder, "$name.tmp")
            temp.writeText(shifted)
            if (!temp.renameTo(target)) {
                temp.delete()
                return null
            }
            target
        } catch (error: Throwable) {
            android.util.Log.w("SubtitleTimeline", "Could not shift subtitles: ${error.message}")
            null
        }
    }

    fun shiftSrt(text: String, offsetMs: Long): String = shift(text, offsetMs)

    fun shiftVtt(text: String, offsetMs: Long): String = shift(text, offsetMs)

    /**
     * Pure text transform, used by the unit tests. Only cue timing lines are
     * rewritten; numbering, styling blocks and cue text are left untouched.
     */
    fun shift(text: String, offsetMs: Long): String {
        if (offsetMs == 0L) return text
        val newline = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.split("\n")
        val result = StringBuilder(text.length + 32)
        for ((index, raw) in lines.withIndex()) {
            val line = raw.removeSuffix("\r")
            if ("-->" in line) {
                result.append(shiftCueLine(line, offsetMs))
            } else {
                result.append(line)
            }
            if (index != lines.lastIndex) result.append(newline)
        }
        return result.toString()
    }

    private fun shiftCueLine(line: String, offsetMs: Long): String {
        val matches = cueTime.findAll(line).toList()
        if (matches.isEmpty()) return line
        val output = StringBuilder(line.length + 8)
        var cursor = 0
        var shiftedStart: Long? = null
        for ((position, match) in matches.withIndex()) {
            output.append(line, cursor, match.range.first)
            val separator = if (',' in match.value) ',' else '.'
            val shifted = (parseCue(match) + offsetMs).coerceAtLeast(0L)
            // A cue must still end after it starts; a fully negative cue keeps a
            // minimal duration instead of collapsing into an invalid range.
            val value = when {
                position == 0 -> shifted.also { shiftedStart = it }
                position == 1 -> shifted.coerceAtLeast((shiftedStart ?: 0L) + 200L)
                else -> shifted
            }
            output.append(formatCue(value, separator))
            cursor = match.range.last + 1
        }
        output.append(line, cursor, line.length)
        return output.toString()
    }

    private fun parseCue(match: MatchResult): Long {
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3].toLongOrNull() ?: 0L
        val millis = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    private fun formatCue(valueMs: Long, separator: Char): String {
        val total = valueMs.coerceAtLeast(0L)
        val hours = total / 3_600_000L
        val minutes = (total % 3_600_000L) / 60_000L
        val seconds = (total % 60_000L) / 1000L
        val millis = total % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d%c%03d", hours, minutes, seconds, separator, millis)
    }

    private fun sha1(value: String): String = MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
