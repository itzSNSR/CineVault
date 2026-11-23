package com.example.util

import com.example.data.model.Episode
import com.example.data.model.EpisodeInfo
import com.example.data.model.MovieSubject
import com.example.data.model.SeasonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSearchIndexTest {
    private val show = MovieSubject(
        id = "show-1", name = "A Very Long Show", poster = "",
        isTvShow = true,
        seasonList = listOf(
            SeasonInfo(1, (1..750).map { EpisodeInfo(it, if (it == 501) "The Great Battle" else "Episode $it") }),
            SeasonInfo(2, (1..600).map { EpisodeInfo(it, if (it == 250) "Return of the Captain" else "Episode $it") })
        )
    )

    private val indexed = EpisodeSearchIndex.build(show, emptyList())

    @Test fun searchesAbsoluteEpisodeNumberAcrossSeasons() {
        val result = EpisodeSearchIndex.filter(indexed, "1000")
        assertEquals(1, result.size)
        assertEquals(2, result.single().episode.seasonNumber)
        assertEquals(250, result.single().episode.episodeNumber)
    }

    @Test fun searchesSeasonAndLocalEpisodeExactly() {
        val result = EpisodeSearchIndex.filter(indexed, "S02E005")
        assertEquals(1, result.size)
        assertEquals(2, result.single().episode.seasonNumber)
        assertEquals(5, result.single().episode.episodeNumber)
    }

    @Test fun searchesTitlesWithoutStartingPlayback() {
        val result = EpisodeSearchIndex.filter(indexed, "great battle")
        assertEquals(1, result.size)
        assertEquals(501, result.single().episode.episodeNumber)
    }

    @Test fun numericQueryDoesNotMatch100WhenSearching10() {
        val result = EpisodeSearchIndex.filter(indexed, "10")
        assertTrue(result.all { it.absoluteNumber == 10 || it.episode.episodeNumber == 10 })
    }

    @Test fun supportsFallbackMetadata() {
        val fallback = listOf(Episode("id2", "Luffy Returns", 99, 1))
        val result = EpisodeSearchIndex.filter(EpisodeSearchIndex.build(
            show.copy(seasonList = emptyList()), fallback
        ), "luffy")
        assertEquals("id2", result.single().episode.episodeId)
    }
}
