package com.example.util

import java.net.URLDecoder
import java.util.Locale

/**
 * Decides whether a provider caption may be attached to the requested episode.
 *
 * A caption is only accepted when the evidence does not contradict the episode:
 *
 *  1. Explicit provider metadata (`se`/`ep`, `season`/`episode`) wins.
 *  2. Otherwise the file name (or title) is parsed for `S01E02`, `1x02`,
 *     `Episode 2`, `EP002` and compared exactly.
 *  3. A caption with no episode identity at all (`English.srt`) is only accepted
 *     when it came from the subject/resource that is actually playing, because
 *     that response was requested with this episode's own identity.
 *
 * Matching is exact: episode 2 never matches 12, and episode 10 never matches
 * 100. The matcher never guesses an episode for a file that does not declare one.
 */
object SubtitleEpisodeMatcher {

    data class EpisodeRef(val season: Int, val episode: Int)

    private val sxxexx = Regex("""(?i)(?<![a-z0-9])s(\d{1,3})[\s._-]*e(\d{1,4})(?!\d)""")
    private val nxnn = Regex("""(?i)(?<![a-z0-9])(\d{1,3})x(\d{1,4})(?!\d)""")
    private val epWord = Regex("""(?i)(?<![a-z0-9])(?:episode|ep|e)[\s._-]*(\d{1,4})(?!\d)""")

    private val seasonKeys = listOf("se", "season", "seasonNumber", "season_number")
    private val episodeKeys = listOf("ep", "episode", "episodeNumber", "episode_number")
    private val nameKeys = listOf("fileName", "filename", "file_name", "name", "title", "lanName")

    /** Season/episode declared by a provider caption map, when present and numeric. */
    fun declaredRef(caption: Map<String, Any?>): EpisodeRef? {
        val season = seasonKeys.firstNotNullOfOrNull { key -> number(caption[key]) } ?: return null
        val episode = episodeKeys.firstNotNullOfOrNull { key -> number(caption[key]) } ?: return null
        return EpisodeRef(season, episode)
    }

    /** Strongest season/episode identity found in a file name, title or URL. */
    fun textRef(text: String?): EpisodeRef? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val decoded = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }

        sxxexx.find(decoded)?.let { m ->
            val season = m.groupValues[1].toIntOrNull() ?: return@let
            val episode = m.groupValues[2].toIntOrNull() ?: return@let
            return EpisodeRef(season, episode)
        }
        nxnn.find(decoded)?.let { m ->
            val season = m.groupValues[1].toIntOrNull() ?: return@let
            val episode = m.groupValues[2].toIntOrNull() ?: return@let
            if (season in 0..99) return EpisodeRef(season, episode)
        }
        epWord.find(decoded)?.let { m ->
            val episode = m.groupValues[1].toIntOrNull() ?: return@let
            return EpisodeRef(season = -1, episode = episode)
        }
        return null
    }

    /** Episode identity carried anywhere in a caption map, if it declares one. */
    fun captionRef(caption: Map<String, Any?>): EpisodeRef? {
        declaredRef(caption)?.let { return it }
        for (key in nameKeys) {
            textRef(caption[key]?.toString())?.let { return it }
        }
        textRef(caption["url"]?.toString())?.let { return it }
        return null
    }

    /**
     * True when [caption] may be shown for [season]/[episode].
     *
     * @param trustedSource true only for the subject/resource that is playing;
     *   an untrusted source must prove its episode identity.
     */
    fun accepts(
        caption: Map<String, Any?>,
        season: Int?,
        episode: Int?,
        trustedSource: Boolean
    ): Boolean {
        if (season == null || episode == null) return true // movie: no episode identity to check
        val ref = captionRef(caption)
        if (ref == null) return trustedSource
        if (ref.season == -1) return ref.episode == episode
        return ref.season == season && ref.episode == episode
    }

    /** Human readable reason, used for diagnostics only (never includes the URL). */
    fun describe(caption: Map<String, Any?>, season: Int?, episode: Int?): String {
        val ref = captionRef(caption)
        val declared = if (ref == null) {
            "unverified"
        } else if (ref.season == -1) {
            "E${ref.episode}"
        } else {
            "S${ref.season}E${ref.episode}"
        }
        val language = (caption["lanName"] ?: caption["lan"] ?: "?").toString().lowercase(Locale.ROOT)
        return "lang=$language declared=$declared requested=S$season" + "E$episode"
    }

    private fun number(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        else -> value?.toString()?.trim()?.toIntOrNull()
    }
}

/**
 * Remembers which episode a caption URL was first delivered for.
 *
 * This is the direct guard for the reported symptom: when the provider ignores
 * the episode parameters it answers every request with the same (episode 1)
 * caption URL. A URL already claimed by another episode is refused instead of
 * being re-attached, so old dialogue can never be shown over a new episode. A
 * full-season pack that legitimately repeats one URL is affected too, which is
 * intentional: an unlabelled shared file cannot be verified per episode.
 */
class CaptionEpisodeTracker {
    private val owners = HashMap<String, String>()

    @Synchronized
    fun claim(url: String, season: Int, episode: Int): Boolean {
        if (url.isBlank()) return false
        val identity = "S${season}E$episode"
        val existing = owners[url]
        if (existing == null) {
            owners[url] = identity
            return true
        }
        return existing == identity
    }

    @Synchronized
    fun release(url: String) {
        owners.remove(url)
    }
}
