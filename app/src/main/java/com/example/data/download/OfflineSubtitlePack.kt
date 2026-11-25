package com.example.data.download

import com.example.data.model.SubtitleItem
import com.example.util.SubtitlePreference
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Downloads the confirmed subtitle languages next to an offline video and
 * writes the same `subtitles.json` manifest the offline player already reads.
 *
 * This runs in its own coroutine and never touches the video transfer: a
 * subtitle failure leaves the video job untouched and is reported separately.
 * Files are named `<language>.<ext>` so they survive app updates and can be
 * discovered by filename even if the manifest is lost.
 */
object OfflineSubtitlePack {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Result(val saved: List<String>, val failed: List<String>)

    fun matches(first: String, second: String): Boolean {
        fun tag(value: String) = SubtitlePreference.languageTag(value) ?: value.trim().lowercase(Locale.ROOT)
        return tag(first) == tag(second)
    }

    fun download(
        folder: File,
        subtitles: List<SubtitleItem>,
        languages: Set<String>?,
        fallbackReferer: String? = null
    ): Result {
        if (subtitles.isEmpty()) return Result(emptyList(), emptyList())
        if (!folder.isDirectory && !folder.mkdirs()) return Result(emptyList(), subtitles.map { it.language })

        fun normalized(label: String) = SubtitlePreference.languageTag(label) ?: label.trim().lowercase(Locale.ROOT)
        val wanted = languages?.map(::normalized)?.toSet()
        val candidates = if (wanted == null) {
            subtitles
        } else {
            subtitles.filter { normalized(it.language) in wanted }
        }
        val saved = mutableListOf<String>()
        val failed = mutableListOf<String>()
        if (candidates.isEmpty()) return Result(saved, failed)

        val existingManifest = runCatching {
            val file = File(folder, MANIFEST)
            if (file.isFile) JSONArray(file.readText()) else JSONArray()
        }.getOrDefault(JSONArray())
        val entries = mutableListOf<JSONObject>()
        for (index in 0 until existingManifest.length()) {
            existingManifest.optJSONObject(index)?.let { entries += it }
        }

        // Visible to the download manager so a finished video with captions
        // still transferring is reported as "Downloading subtitles".
        val lock = File(folder, ".cv-subtitles.lock")
        runCatching { lock.writeText("") }

        for ((position, subtitle) in candidates.withIndex()) {
            val language = subtitle.language.trim().ifBlank { "Track ${position + 1}" }
            try {
                val trackKey = java.util.UUID.nameUUIDFromBytes((subtitle.url + "|" + language).toByteArray()).toString()
                val prior = entries.firstOrNull { it.optString("track") == trackKey }
                if (prior != null && File(folder, prior.getString("fileName")).length() > 0) { saved += language; continue }
                val request = Request.Builder().url(subtitle.url).apply {
                    if (!fallbackReferer.isNullOrBlank()) header("Referer", fallbackReferer)
                }.build()
                val document = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    com.example.util.CaptionDocument.read(response.body!!.byteStream())
                }
                val name = DownloadStorage.safeName(language) + "-" + trackKey.take(8) + "." + document.extension
                val target = File(folder, name)
                val temp = File(folder, "$name.partial")
                temp.writeBytes(document.bytes)
                check(temp.renameTo(target)) { "Could not save captions" }
                entries.removeAll { it.optString("track") == trackKey }
                entries += JSONObject().put("track", trackKey).put("language", language).put("fileName", name)
                val manifest = File(folder, "$MANIFEST.partial")
                manifest.writeText(JSONArray(entries).toString())
                check(manifest.renameTo(File(folder, MANIFEST))) { "Could not save caption index" }
                saved += language
            } catch (error: Throwable) {
                failed += language
                android.util.Log.w("OfflineSubtitlePack", "Subtitle $language failed: ${error.message}")
            }
        }

        runCatching { lock.delete() }
        return Result(saved, failed)
    }

    /** Subtitle languages already stored for an offline episode. */
    fun available(folder: File): List<String> {
        val manifest = File(folder, MANIFEST)
        val fromManifest = runCatching {
            val array = if (manifest.isFile) JSONArray(manifest.readText()) else JSONArray()
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("language") }
        }.getOrDefault(emptyList())
        if (fromManifest.isNotEmpty()) return fromManifest
        return folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("srt", "vtt", "ass", "ssa") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    const val MANIFEST = "subtitles.json"
    const val LOCK = ".cv-subtitles.lock"
}
