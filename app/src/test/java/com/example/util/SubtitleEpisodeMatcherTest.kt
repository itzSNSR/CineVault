package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleEpisodeMatcherTest {

    private fun caption(
        url: String,
        lanName: String = "English",
        vararg extra: Pair<String, Any?>
    ): Map<String, Any?> = buildMap {
        put("lan", "en")
        put("lanName", lanName)
        put("url", url)
        extra.forEach { (key, value) -> put(key, value) }
    }

    @Test
    fun episodeOneCaptionIsRejectedForEpisodeTwo() {
        val e1 = caption("https://cdn.example/Show.S01E01.1080p.en.srt")
        assertFalse(SubtitleEpisodeMatcher.accepts(e1, season = 1, episode = 2, trustedSource = true))
        assertTrue(SubtitleEpisodeMatcher.accepts(e1, season = 1, episode = 1, trustedSource = false))
    }

    @Test
    fun paddedAndUnpaddedNamesBothMatch() {
        listOf(
            "Show.S1E2.en.srt",
            "Show S01E02 English.vtt",
            "Show.1x02.en.srt",
            "Show.Episode 2.en.srt",
            "Show.EP002.en.srt"
        ).forEach { name ->
            assertTrue(name, SubtitleEpisodeMatcher.accepts(caption("https://cdn.example/$name"), 1, 2, false))
        }
    }

    @Test
    fun episodeTwoDoesNotMatchEpisodeTwelve() {
        val e12 = caption("https://cdn.example/Show.S01E12.en.srt")
        assertFalse(SubtitleEpisodeMatcher.accepts(e12, 1, 2, true))
        val e2 = caption("https://cdn.example/Show.S01E02.en.srt")
        assertFalse(SubtitleEpisodeMatcher.accepts(e2, 1, 12, true))
    }

    @Test
    fun fourDigitAnimeEpisodesAreSupported() {
        val e1000 = caption("https://cdn.example/OnePiece.E1000.en.srt")
        assertTrue(SubtitleEpisodeMatcher.accepts(e1000, 1, 1000, false))
        assertFalse(SubtitleEpisodeMatcher.accepts(e1000, 1, 100, false))
        assertTrue(SubtitleEpisodeMatcher.accepts(e1000, 1, 1000, true))
    }

    @Test
    fun declaredProviderMetadataWinsOverFileName() {
        // Provider labels the file generically but declares the real episode.
        val declared = caption(
            "https://cdn.example/download?id=99",
            extra = arrayOf("se" to 1, "ep" to 2)
        )
        assertEquals(SubtitleEpisodeMatcher.EpisodeRef(1, 2), SubtitleEpisodeMatcher.declaredRef(declared))
        assertTrue(SubtitleEpisodeMatcher.accepts(declared, 1, 2, false))
        assertFalse(SubtitleEpisodeMatcher.accepts(declared, 1, 3, false))
    }

    @Test
    fun seasonNumbersMustAlsoMatch() {
        val s2e2 = caption("https://cdn.example/Show.S02E02.en.srt")
        assertFalse(SubtitleEpisodeMatcher.accepts(s2e2, 1, 2, true))
        assertTrue(SubtitleEpisodeMatcher.accepts(s2e2, 2, 2, true))
    }

    @Test
    fun unverifiedFileIsOnlyAcceptedFromThePlayingSource() {
        val generic = caption("https://cdn.example/English.srt")
        assertNull(SubtitleEpisodeMatcher.captionRef(generic))
        assertTrue(SubtitleEpisodeMatcher.accepts(generic, 1, 2, trustedSource = true))
        assertFalse(SubtitleEpisodeMatcher.accepts(generic, 1, 2, trustedSource = false))
    }

    @Test
    fun theSameCaptionUrlCannotServeTwoEpisodes() {
        val tracker = CaptionEpisodeTracker()
        val url = "https://cdn.example/Show.en.srt"
        assertTrue(tracker.claim(url, 1, 1))
        assertTrue(tracker.claim(url, 1, 1)) // same episode may re-request
        assertFalse(tracker.claim(url, 1, 2)) // episode 2 must not inherit it
        assertTrue(tracker.claim("https://cdn.example/Show.S01E02.en.srt", 1, 2))
    }

    @Test
    fun moviesHaveNoEpisodeConstraint() {
        val movieSub = caption("https://cdn.example/movie.en.srt")
        assertTrue(SubtitleEpisodeMatcher.accepts(movieSub, null, null, false))
    }

    @Test
    fun resolutionAndCodecNumbersAreNotMistakenForEpisodes() {
        assertNull(SubtitleEpisodeMatcher.textRef("Show.1080p.WEB-DL.x264.en.srt"))
        assertNull(SubtitleEpisodeMatcher.textRef("English"))
        assertNull(SubtitleEpisodeMatcher.textRef(""))
        assertNull(SubtitleEpisodeMatcher.textRef(null))
    }
}
