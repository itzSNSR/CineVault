package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimelineTest {
    @Test
    fun shiftsSrtCuesForward() {
        val input = "1\n00:00:10,000 --> 00:00:12,500\nHello\n"
        val output = SubtitleTimeline.shift(input, 1_500)
        assertTrue(output.contains("00:00:11,500 --> 00:00:14,000"))
        assertTrue(output.contains("Hello"))
    }

    @Test
    fun clampsNegativeCuesToZero() {
        val input = "1\n00:00:01,000 --> 00:00:02,000\nEarly\n"
        val output = SubtitleTimeline.shift(input, -5_000)
        assertTrue(output.contains("00:00:00,000 --> 00:00:00,200"))
    }

    @Test
    fun keepsVttSeparatorAndHeader() {
        val input = "WEBVTT\n\n00:01:00.250 --> 00:01:02.000\nLine\n"
        val output = SubtitleTimeline.shift(input, 250)
        assertTrue(output.startsWith("WEBVTT"))
        assertTrue(output.contains("00:01:00.500 --> 00:01:02.250"))
    }

    @Test
    fun zeroOffsetIsIdentity() {
        val input = "1\n00:00:10,000 --> 00:00:12,500\nHello\n"
        assertEquals(input, SubtitleTimeline.shift(input, 0))
    }
}
