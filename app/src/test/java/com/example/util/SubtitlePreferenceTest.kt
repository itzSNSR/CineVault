package com.example.util

import com.example.data.model.SubtitleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePreferenceTest {

    private val s1e1English = SubtitleItem("English", "https://cdn.example/s1e1-en.vtt")
    private val s1e2English = SubtitleItem("English (CC)", "https://cdn.example/s1e2-en.vtt")
    private val s1e2Tamil = SubtitleItem("Tamil", "https://cdn.example/s1e2-ta.vtt")
    private val s1e2French = SubtitleItem("French", "https://cdn.example/s1e2-fr.vtt")

    @Test
    fun nextEpisodeIsReboundAndPreviousUrlIsNeverReused() {
        val selected = SubtitlePreference.forEpisode(
            available = listOf(s1e2Tamil, s1e2English),
            preferredLanguage = s1e1English.language,
            subtitlesOff = false
        )
        assertEquals(s1e2English.url, selected?.url)
        assertNotEquals(s1e1English.url, selected?.url)
    }

    @Test
    fun englishIsDefaultWhenItIsNotTheFirstTrack() {
        val selected = SubtitlePreference.forEpisode(
            available = listOf(
                SubtitleItem("Bengali", "https://cdn.example/bn.srt"),
                SubtitleItem("EN", "https://cdn.example/en.srt")
            ),
            preferredLanguage = null,
            subtitlesOff = false
        )
        assertEquals("https://cdn.example/en.srt", selected?.url)
    }

    @Test
    fun englishMatchesAllProviderSpellings() {
        val labels = listOf("English", "EN", "en", "eng", "English (CC)", "English (OpenSubtitles)")
        for (label in labels) {
            val selected = SubtitlePreference.forEpisode(
                available = listOf(SubtitleItem(label, "https://cdn.example/$label.vtt")),
                preferredLanguage = null,
                subtitlesOff = false
            )
            assertEquals(label, selected?.language)
        }
    }

    @Test
    fun manualLanguageIsKeptWhenTheNextEpisodeHasIt() {
        val selected = SubtitlePreference.forEpisode(
            available = listOf(s1e2English, s1e2Tamil),
            preferredLanguage = "Tamil",
            subtitlesOff = false
        )
        assertEquals(s1e2Tamil.url, selected?.url)
    }

    @Test
    fun missingPreferredLanguageFallsBackToEnglish() {
        val selected = SubtitlePreference.forEpisode(
            available = listOf(s1e2French, SubtitleItem("eng", "https://cdn.example/s1e2-en.srt")),
            preferredLanguage = "ta",
            subtitlesOff = false
        )
        assertEquals("https://cdn.example/s1e2-en.srt", selected?.url)
    }

    @Test
    fun anotherValidTrackIsUsedOnlyWhenEnglishIsMissing() {
        val selected = SubtitlePreference.forEpisode(
            available = listOf(s1e2French),
            preferredLanguage = "ta",
            subtitlesOff = false
        )
        assertEquals(s1e2French.url, selected?.url)
    }

    @Test
    fun explicitOffWinsOverEveryPreference() {
        assertNull(
            SubtitlePreference.forEpisode(listOf(s1e2English), "English", subtitlesOff = true)
        )
    }

    @Test
    fun noUsableSubtitleMeansNoSelection() {
        assertNull(SubtitlePreference.forEpisode(emptyList(), null, subtitlesOff = false))
        assertNull(
            SubtitlePreference.forEpisode(
                listOf(SubtitleItem("English", ""), SubtitleItem("Tamil", "   ")),
                null,
                subtitlesOff = false
            )
        )
    }

    @Test
    fun dubbedAudioKeepsAutomaticSelectionOff() {
        assertNull(
            SubtitlePreference.forEpisode(
                listOf(s1e2English),
                preferredLanguage = null,
                subtitlesOff = false,
                allowAutomaticEnglish = false
            )
        )
        assertEquals(
            s1e2Tamil.url,
            SubtitlePreference.forEpisode(
                listOf(s1e2English, s1e2Tamil),
                preferredLanguage = "ta",
                subtitlesOff = false,
                allowAutomaticEnglish = false
            )?.url
        )
    }

    @Test
    fun labelsAreNormalizedToMedia3Tags() {
        assertEquals("en", SubtitlePreference.languageTag("English"))
        assertEquals("en", SubtitlePreference.languageTag("EN"))
        assertEquals("en", SubtitlePreference.languageTag("en"))
        assertEquals("en", SubtitlePreference.languageTag("eng"))
        assertEquals("en", SubtitlePreference.languageTag("English (CC)"))
        assertEquals("en", SubtitlePreference.languageTag("English (OpenSubtitles)"))
        assertEquals("en", SubtitlePreference.languageTag("en-US"))
        assertEquals("ta", SubtitlePreference.languageTag("Tamil"))
        assertEquals("ta", SubtitlePreference.languageTag("தமிழ்"))
        assertEquals("hi", SubtitlePreference.languageTag("हिन्दी"))
        assertEquals("und", SubtitlePreference.languageTag("und"))
        assertNull(SubtitlePreference.languageTag(""))
        assertNull(SubtitlePreference.languageTag(null))
        assertTrue(SubtitlePreference.isEnglish("ENG"))
        assertFalse(SubtitlePreference.isEnglish("Tamil"))
    }
}
