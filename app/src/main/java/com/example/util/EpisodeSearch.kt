package com.example.util

import com.example.data.model.Episode
import com.example.data.model.SeasonInfo
import java.util.Locale

/**
 * Pure, allocation-light episode search used by the details screen and every
 * in-player episode picker. Works on metadata only: it never triggers a network
 * request, so typing is instantaneous even for 1,000+ episode series.
 *
 * Supported queries:
 *  - `1000`            exact episode number
 *  - `Episode 250` / `EP 250` / `E250`   episode number
 *  - `S2E5` / `S02E05` / `1x05`          season + episode
 *  - `Luffy`           case-insensitive title fragment
 */
object EpisodeSearch {

    private val sxxexx = Regex("""(?i)(?<![a-z0-9])s(\d{1,3})[\s._-]*e(\d{1,4})(?!\d)""")
    private val nxnn = Regex("""(?i)(?<![a-z0-9])(\d{1,3})x(\d{1,4})(?!\d)""")
    private val epWord = Regex("""(?i)^(?:episode|ep|e)[\s._-]*(\d{1,4})$""")
    private val onlyDigits = Regex("""^\d{1,4}$""")
    private val spaces = Regex("""\s+""")

    private fun normalized(text: String): String = text.trim().lowercase(Locale.ROOT).replace(spaces, " ")

    /** `(season, episode)` when the query names both, otherwise null. */
    fun seasonEpisode(query: String): Pair<Int, Int>? {
        val q = query.trim()
        sxxexx.find(q)?.let { m ->
            val season = m.groupValues[1].toIntOrNull() ?: return@let
            val episode = m.groupValues[2].toIntOrNull() ?: return@let
            return season to episode
        }
        nxnn.find(q)?.let { m ->
            val season = m.groupValues[1].toIntOrNull() ?: return@let
            val episode = m.groupValues[2].toIntOrNull() ?: return@let
            if (season in 0..99) return season to episode
        }
        return null
    }

    /** Episode number when the query is only an episode reference (`1000`, `E250`, `Episode 250`). */
    fun episodeNumber(query: String): Int? {
        val q = query.trim()
        if (onlyDigits.matches(q)) return q.toIntOrNull()
        epWord.find(q)?.let { m -> return m.groupValues[1].toIntOrNull() }
        return null
    }

    fun matches(episode: Episode, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true

        seasonEpisode(q)?.let { (season, number) ->
            return episode.seasonNumber == season && episode.episodeNumber == number
        }
        episodeNumber(q)?.let { number ->
            return episode.episodeNumber == number
        }

        val needle = normalized(q)
        if (needle.isEmpty()) return true
        if (normalized(episode.title).contains(needle)) return true
        // Partial numbers still help ("10" finds nothing exact, but "100" must not match 1000).
        return onlyDigits.matches(q) && episode.episodeNumber.toString() == q
    }

    fun filter(episodes: List<Episode>, query: String): List<Episode> {
        if (query.isBlank()) return episodes
        return episodes.filter { matches(it, query) }
    }

    /** Flattens provider season metadata into searchable episodes (no network, no stream URLs). */
    fun episodesFromSeasons(seasons: List<SeasonInfo>, subjectId: String): List<Episode> =
        seasons.flatMap { season ->
            season.episodeList.map { info ->
                Episode(
                    episodeId = "${subjectId}_S${season.season}_E${info.episode}",
                    title = info.title.ifEmpty { "Episode ${info.episode}" },
                    episodeNumber = info.episode,
                    seasonNumber = season.season,
                    duration = info.duration.ifEmpty { "45 min" },
                    stillPath = info.stillPath
                )
            }
        }

    /** Compact label: `EP 1000` for single-season shows, `S02 E05` otherwise. */
    fun label(episode: Episode): String =
        if (episode.seasonNumber <= 1) {
            "EP ${episode.episodeNumber}"
        } else {
            "S${episode.seasonNumber.toString().padStart(2, '0')} E${episode.episodeNumber.toString().padStart(2, '0')}"
        }
}
