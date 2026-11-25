package com.example.util

import com.example.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSearchTest {

    private fun episode(number: Int, title: String, season: Int = 1) = Episode(
        episodeId = "sub_S${season}_E$number",
        title = title,
        episodeNumber = number,
        seasonNumber = season
    )

    private val onePiece = listOf(
        episode(1, "I'm Luffy! The Man Who Will Be King of the Pirates!", 1),
        episode(2, "The Great Swordsman Appears! Pirate Hunter Roronoa Zoro", 1),
        episode(10, "The Weirdest Guy Ever! Jango the Hypnotist", 1),
        episode(100, "A Rebel's Dream", 1),
        episode(250, "Luffy vs. Usopp", 1),
        episode(1000, "Overwhelming Strength! The Straw Hats Come Together!", 1)
    )

    @Test
    fun exactNumberFindsTheEpisode() {
        assertEquals(listOf(1000), EpisodeSearch.filter(onePiece, "1000").map { it.episodeNumber })
        assertEquals(listOf(250), EpisodeSearch.filter(onePiece, "250").map { it.episodeNumber })
    }

    @Test
    fun shorterNumbersDoNotMatchLongerOnes() {
        assertTrue(EpisodeSearch.filter(onePiece, "10").none { it.episodeNumber == 100 || it.episodeNumber == 1000 })
        assertTrue(EpisodeSearch.filter(onePiece, "2").none { it.episodeNumber == 250 })
        assertEquals(listOf(10), EpisodeSearch.filter(onePiece, "10").map { it.episodeNumber })
    }

    @Test
    fun episodeLabelsAreUnderstood() {
        assertEquals(listOf(250), EpisodeSearch.filter(onePiece, "Episode 250").map { it.episodeNumber })
        assertEquals(listOf(250), EpisodeSearch.filter(onePiece, "EP 250").map { it.episodeNumber })
        assertEquals(listOf(250), EpisodeSearch.filter(onePiece, "E250").map { it.episodeNumber })
        assertEquals(listOf(2), EpisodeSearch.filter(onePiece, "ep2").map { it.episodeNumber })
    }

    @Test
    fun seasonAndEpisodeQueryFindsThePair() {
        val multi = listOf(
            episode(5, "Freedom", 2),
            episode(5, "Pilot", 1)
        )
        assertEquals(listOf(2), EpisodeSearch.filter(multi, "S2E5").map { it.seasonNumber })
        assertEquals(listOf(2), EpisodeSearch.filter(multi, "S02E05").map { it.seasonNumber })
        assertEquals(listOf(1), EpisodeSearch.filter(multi, "1x05").map { it.seasonNumber })
        assertEquals(2 to 5, EpisodeSearch.seasonEpisode("S02E05"))
        assertNull(EpisodeSearch.seasonEpisode("1000"))
    }

    @Test
    fun titleSearchIsCaseInsensitiveAndIgnoresExtraSpaces() {
        assertEquals(listOf(250), EpisodeSearch.filter(onePiece, "luffy").map { it.episodeNumber }.filter { it == 250 })
        assertEquals(listOf(1, 250), EpisodeSearch.filter(onePiece, "  LUFFY  ").map { it.episodeNumber })
        assertEquals(listOf(2), EpisodeSearch.filter(onePiece, "roronoa").map { it.episodeNumber })
    }

    @Test
    fun unmatchedQueryReturnsNothing() {
        assertTrue(EpisodeSearch.filter(onePiece, "Zoro").none { it.episodeNumber == 1000 })
        assertTrue(EpisodeSearch.filter(onePiece, "does not exist").isEmpty())
    }

    @Test
    fun blankQueryKeepsEveryEpisode() {
        assertEquals(onePiece, EpisodeSearch.filter(onePiece, "   "))
        assertTrue(EpisodeSearch.matches(onePiece.first(), ""))
    }

    @Test
    fun labelsShowAbsoluteAndSeasonedFormats() {
        assertEquals("EP 1000", EpisodeSearch.label(episode(1000, "x")))
        assertEquals("S02 E05", EpisodeSearch.label(episode(5, "x", season = 2)))
    }

    @Test
    fun seasonMetadataFlattensWithoutNetwork() {
        val seasons = listOf(
            com.example.data.model.SeasonInfo(
                season = 1,
                episodeList = listOf(com.example.data.model.EpisodeInfo(episode = 1, title = "Pilot"))
            ),
            com.example.data.model.SeasonInfo(
                season = 2,
                episodeList = listOf(com.example.data.model.EpisodeInfo(episode = 3, title = "Return"))
            )
        )
        val flat = EpisodeSearch.episodesFromSeasons(seasons, "mb_42")
        assertEquals(2, flat.size)
        assertEquals(2, flat[1].seasonNumber)
        assertFalse(flat[1].episodeId.contains("https"))
        assertEquals("Return", flat[1].title)
    }
}
