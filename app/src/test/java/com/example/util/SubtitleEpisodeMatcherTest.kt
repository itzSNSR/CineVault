package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleEpisodeMatcherTest {
    @Test fun acceptsCorrectSeasonAndEpisode() {
        assertTrue(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("fileName" to "One.Piece.S01E1000.en.srt"), season = 1, episode = 1000
        ))
        assertTrue(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("title" to "One Piece Episode 2 English"), season = 1, episode = 2
        ))
    }

    @Test fun rejectsOtherEpisodeWithoutConfusing2And12() {
        assertFalse(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("fileName" to "Show.S01E12.English.srt"), season = 1, episode = 2
        ))
        assertFalse(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("url" to "https://cdn.test/captions/E01.vtt?episode=2&token=123"),
            season = 1, episode = 2
        ))
    }

    @Test fun rejectsWrongSeasonOrExplicitMetadata() {
        assertFalse(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("name" to "Show.S02E02.vtt"), season = 1, episode = 2
        ))
        assertFalse(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("episodeNumber" to 3.0), season = 1, episode = 2
        ))
        assertFalse(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("se" to "2", "ep" to "2"), season = 1, episode = 2
        ))
    }

    @Test fun permitsUnknownNamesFromExactEpisodeEndpoint() {
        assertTrue(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("lanName" to "English", "url" to "https://cdn.test/english.srt?hash=2983829"),
            season = 1, episode = 2
        ))
        assertTrue(SubtitleEpisodeMatcher.belongsToEpisode(
            mapOf("url" to "https://cdn.test/S02E04.en.srt"), season = null, episode = null
        ))
    }
}
