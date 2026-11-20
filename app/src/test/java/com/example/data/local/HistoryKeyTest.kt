package com.example.data.local

import org.junit.Assert.*
import org.junit.Test

class HistoryKeyTest {
    @Test fun keysRespectProviderAndEpisode() {
        assertEquals("moviebox_42", historyKey(false, "42", 0, 0))
        assertEquals("anifam_42_S2_E3", historyKey(true, "anifam_42", 2, 3))
        assertNotEquals(historyKey(true, "42", 2, 3), historyKey(false, "42", 2, 3))
    }

    @Test fun resumeAtEighteenFortyAndRestartCompletedTitle() {
        val item = HistoryEntity("moviebox_42", "42", "Movie", "", false, 0, 0, 1_120_000L, 6_000_000L)
        assertEquals(1_120_000L, item.resumePosition)
        assertEquals(0L, item.copy(seeTime = item.totalTime - 3_000).resumePosition)
    }
}
