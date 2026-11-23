package com.example.util

import com.example.data.model.Episode
import com.example.data.model.MovieSubject

/**
 * Search-only episode metadata. Never resolves media URLs or starts playback.
 * [absoluteNumber] supports long-running shows with per-season episode numbering.
 */
data class IndexedEpisode(val episode: Episode, val absoluteNumber: Int)

object EpisodeSearchIndex {
    private val seasonEpisode = Regex("""(?i)^s\s*0*(\d+)\s*[-._ ]?\s*e\s*0*(\d+)$""")
    private val episodeNumber = Regex("""(?i)^(?:episode|ep|e)?\s*#?\s*0*(\d+)$""")

    fun build(subject: MovieSubject, fallback: List<Episode>): List<IndexedEpisode> {
        val seasons = subject.seasonList
            .filter { it.episodeList.isNotEmpty() }
            .sortedBy { it.season }

        if (seasons.isEmpty()) {
            return fallback.sortedWith(compareBy(Episode::seasonNumber, Episode::episodeNumber))
                .mapIndexed { index, episode -> IndexedEpisode(episode, index + 1) }
        }

        var absolute = 0
        return seasons.flatMap { season ->
            season.episodeList.sortedBy { it.episode }.map { info ->
                absolute++
                IndexedEpisode(
                    episode = Episode(
                        episodeId = "${subject.id}_S${season.season}_E${info.episode}",
                        title = info.title.ifBlank { "Episode ${info.episode}" },
                        episodeNumber = info.episode,
                        seasonNumber = season.season,
                        duration = info.duration.ifBlank { "45 min" },
                        stillPath = info.stillPath
                    ),
                    absoluteNumber = absolute
                )
            }
        }
    }

    fun filter(entries: List<IndexedEpisode>, query: String): List<IndexedEpisode> {
        val term = query.trim()
        if (term.isEmpty()) return entries

        val exactSeasonEpisode = seasonEpisode.matchEntire(term)
        if (exactSeasonEpisode != null) {
            val season = exactSeasonEpisode.groupValues[1].toIntOrNull()
            val episode = exactSeasonEpisode.groupValues[2].toIntOrNull()
            return entries.filter {
                it.episode.seasonNumber == season && it.episode.episodeNumber == episode
            }
        }

        val numeric = episodeNumber.matchEntire(term)?.groupValues?.get(1)?.toIntOrNull()
        if (numeric != null) {
            return entries.filter {
                it.episode.episodeNumber == numeric || it.absoluteNumber == numeric
            }
        }

        return entries.filter {
            it.episode.title.contains(term, ignoreCase = true)
        }
    }
}
