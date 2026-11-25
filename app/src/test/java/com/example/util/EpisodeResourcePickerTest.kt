package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeResourcePickerTest {

    private val listing = listOf(
        mapOf("resourceId" to "8413404083687669968", "se" to 1, "ep" to 1, "resolution" to 360),
        mapOf("resourceId" to "3524510602414752008", "se" to 1, "ep" to 2, "resolution" to 360),
        mapOf("resourceId" to "5588566606633992456", "se" to 1, "ep" to 3, "resolution" to 360)
    )

    @Test
    fun `selects the requested episode instead of the first item`() {
        val item = EpisodeResourcePicker.find(listing, 1, 2)
        assertNotNull(item)
        assertEquals("3524510602414752008", EpisodeResourcePicker.resourceIdOf(item!!))
    }

    @Test
    fun `episode one is only matched when it is requested`() {
        assertEquals("8413404083687669968", EpisodeResourcePicker.resourceIdOf(EpisodeResourcePicker.find(listing, 1, 1)!!))
    }

    @Test
    fun `missing episode never falls back to another resource`() {
        assertNull(EpisodeResourcePicker.find(listing, 1, 9))
        assertNull(EpisodeResourcePicker.find(listing, 2, 1))
    }

    @Test
    fun `season and episode may be numeric strings`() {
        val items = listOf(
            mapOf("resourceId" to "a", "se" to "2", "ep" to "5"),
            mapOf("resourceId" to "b", "se" to "2", "ep" to "6")
        )
        assertEquals("b", EpisodeResourcePicker.resourceIdOf(EpisodeResourcePicker.find(items, 2, 6)!!))
    }

    @Test
    fun `episode two does not match episode twelve`() {
        val items = listOf(mapOf("resourceId" to "x", "se" to 1, "ep" to 12))
        assertNull(EpisodeResourcePicker.find(items, 1, 2))
        assertEquals("x", EpisodeResourcePicker.resourceIdOf(EpisodeResourcePicker.find(items, 1, 12)!!))
    }

    @Test
    fun `items without episode identity are ignored`() {
        val items = listOf(
            mapOf("resourceId" to "detector", "resolution" to 1080),
            mapOf("resourceId" to "ep2", "se" to 1, "ep" to 2, "resolution" to 720)
        )
        assertEquals("ep2", EpisodeResourcePicker.resourceIdOf(EpisodeResourcePicker.find(items, 1, 2)!!))
    }

    @Test
    fun `highest resolution wins when an episode has several resources`() {
        val items = listOf(
            mapOf("resourceId" to "low", "se" to 1, "ep" to 4, "resolution" to 360),
            mapOf("resourceId" to "high", "se" to 1, "ep" to 4, "resolution" to 1080),
            mapOf("resourceId" to "mid", "se" to 1, "ep" to 4, "resolution" to 720)
        )
        assertEquals("high", EpisodeResourcePicker.resourceIdOf(EpisodeResourcePicker.find(items, 1, 4)!!))
    }

    @Test
    fun `resource id keeps full precision for long numeric ids`() {
        val item = mapOf("resourceId" to 6065869889208832296L, "se" to 1, "ep" to 1)
        assertEquals("6065869889208832296", EpisodeResourcePicker.resourceIdOf(item))
    }

    @Test
    fun `resource id falls back to string id field`() {
        assertEquals("1234567890", EpisodeResourcePicker.resourceIdOf(mapOf("id" to "1234567890")))
        assertNull(EpisodeResourcePicker.resourceIdOf(mapOf("title" to "no id")))
    }

    @Test
    fun `movie listings without season and episode are not matched`() {
        val movies = listOf(mapOf("resourceId" to "movie", "resolution" to 1080))
        assertNull(EpisodeResourcePicker.find(movies, 1, 1))
        assertNull(EpisodeResourcePicker.find(movies, 0, 0))
    }
}
