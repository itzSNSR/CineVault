package com.example.util

import com.example.data.download.*
import com.example.data.model.StreamItem
import com.example.data.local.*
import org.junit.Assert.*
import org.junit.Test

class ReleaseRegressionTest {
    @Test fun emptyMovieSelectionStillQueuesOneMovie() {
        assertEquals(listOf(DownloadTarget.Movie), downloadTargets(false, emptyList()))
        assertTrue(downloadTargets(true, emptyList()).isEmpty())
        val one = DownloadEpisode(1, 5)
        val two = DownloadEpisode(2, 5)
        assertEquals(2, downloadTargets(true, listOf(one, one, two)).size)
    }

    @Test fun exactQualityDoesNotSubstituteAndLowerNeverUpsizes() {
        val sd = StreamItem("480p", "sd")
        val hd = StreamItem("1080p", "hd")
        val all = listOf(hd, sd)
        assertNull(selectDownloadStream(all, "720p", QualityFallback.EXACT))
        assertEquals(sd, selectDownloadStream(all, "720p", QualityFallback.LOWER))
        assertNull(selectDownloadStream(all, "360p", QualityFallback.LOWER))
        assertEquals(hd, selectDownloadStream(all, "720p", QualityFallback.HIGHEST))
    }

    @Test fun captionLabelsAndPayloads() {
        assertTrue(OfflineSubtitlePack.matches("English", "eng"))
        assertTrue(OfflineSubtitlePack.matches("en-US", "English (CC)"))
        assertFalse(OfflineSubtitlePack.matches("English", "Tamil"))
        val cue = "1\n00:00:01,000 --> 00:00:02,000\nHello"
        assertEquals("srt", CaptionDocument.read(cue.byteInputStream()).extension)
        assertEquals("vtt", CaptionDocument.read(("WEBVTT\n\n" + cue).byteInputStream()).extension)
        assertTrue(runCatching { CaptionDocument.read("<html>Error</html>".byteInputStream()) }.isFailure)
    }

    @Test fun historyWithSameNumericIdIsProviderScoped() {
        val movie = HistoryEntity("moviebox_7", "7", "A", "", false, 0, 0, 40, 100)
        assertEquals("moviebox_7", movie.copy(id = "7").titleKey)
        assertNotEquals(movie.titleKey, movie.copy(id = "anifam_7").titleKey)
    }
}
