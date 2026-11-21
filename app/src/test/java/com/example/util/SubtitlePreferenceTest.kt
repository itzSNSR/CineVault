package com.example.util

import com.example.data.model.SubtitleItem
import org.junit.Assert.*
import org.junit.Test

class SubtitlePreferenceTest {
    private val firstEpisode = SubtitleItem("English", "https://example.com/s1e1.vtt")
    private val nextEpisode = SubtitleItem("English (CC)", "https://example.com/s1e2.vtt")

    @Test fun rebindingEnglishNeverReusesPreviousEpisodeSubtitle() {
        val selected = SubtitlePreference.forEpisode(
            listOf(SubtitleItem("Spanish", "https://example.com/es-s1e2.vtt"), nextEpisode),
            firstEpisode.language,
            subtitlesOff = false,
            embeddedLanguage = null
        )
        assertEquals(nextEpisode.url, selected?.url)
        assertNotEquals(firstEpisode.url, selected?.url)
    }

    @Test fun defaultsToEnglishEvenWhenItIsNotTheFirstTrack() {
        val available = listOf(
            SubtitleItem("Bengali", "https://example.com/bn.srt"),
            SubtitleItem("EN", "https://example.com/en.srt")
        )
        assertEquals("https://example.com/en.srt",
            SubtitlePreference.forEpisode(available, null, false, null)?.url)
    }

    @Test fun missingPreferredLanguageFallsBackToEnglish() {
        val available = listOf(
            SubtitleItem("French", "https://example.com/fr.vtt"),
            SubtitleItem("eng", "https://example.com/en.vtt")
        )
        assertEquals("eng",
            SubtitlePreference.forEpisode(available, "Spanish", false, null)?.language)
    }

    @Test fun keepsNonEnglishPreferenceWhenPresent() {
        val available = listOf(
            SubtitleItem("English", "https://example.com/en.vtt"),
            SubtitleItem("French", "https://example.com/fr.vtt")
        )
        assertEquals("French",
            SubtitlePreference.forEpisode(available, "fr", false, null)?.language)
    }

    @Test fun offAndEmbeddedSelectionsAreRespected() {
        assertNull(SubtitlePreference.forEpisode(listOf(nextEpisode), "English", true, null))
        assertNull(SubtitlePreference.forEpisode(listOf(nextEpisode), "English", false, "ja"))
    }

    @Test fun returnsOnlyValidEpisodeLinks() {
        val available = listOf(
            SubtitleItem("English", ""),
            SubtitleItem("Tamil", "https://example.com/ta.vtt")
        )
        assertEquals("Tamil",
            SubtitlePreference.forEpisode(available, null, false, null)?.language)
        assertNull(SubtitlePreference.forEpisode(emptyList(), "English", false, null))
    }

    @Test fun normalizesDisplayNamesToMedia3LanguageTags() {
        assertEquals("en", SubtitlePreference.languageTag("English (OpenSubtitles)"))
        assertEquals("en", SubtitlePreference.languageTag("EN-US"))
        assertEquals("hi", SubtitlePreference.languageTag("Hindi"))
        assertEquals("bn", SubtitlePreference.languageTag("Bengali"))
        assertNull(SubtitlePreference.languageTag(""))
    }
}
