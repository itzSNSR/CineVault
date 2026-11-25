package com.example.util

/**
 * Selects the MovieBox resource that belongs to exactly one episode.
 *
 * `/wefeed-mobile-bff/subject-api/resource` lists every episode of a subject
 * with its own `resourceId`, and `get-ext-captions` is scoped by that id, so
 * this lookup is what makes MovieBox captions episode-specific (the same
 * approach MovieBox-Tui uses in `find_matching_resource_item`).
 *
 * Matching is exact: `se`/`ep` may arrive as numbers or numeric strings, and an
 * item whose pair does not match is never used as a substitute. Episode 2 must
 * not fall back to the resource that happens to sit first in the list, because
 * that one belongs to episode 1.
 */
object EpisodeResourcePicker {

    private val seasonKeys = listOf("se", "season", "seasonNumber")
    private val episodeKeys = listOf("ep", "episode", "episodeNumber")
    private val resolutionKeys = listOf("resolution", "resolutions", "definition", "quality")

    fun seasonOf(item: Map<String, Any?>): Int? = number(item, seasonKeys)

    fun episodeOf(item: Map<String, Any?>): Int? = number(item, episodeKeys)

    /** Exact episode match, preferring the highest declared resolution. */
    fun find(items: List<Map<String, Any?>>, season: Int, episode: Int): Map<String, Any?>? {
        if (season <= 0 || episode <= 0) return null
        val matches = items.filter { seasonOf(it) == season && episodeOf(it) == episode }
        if (matches.isEmpty()) return null
        return matches.maxByOrNull { resolutionOf(it) }
    }

    /** The provider's own id for this resource, as a string without precision loss. */
    fun resourceIdOf(item: Map<String, Any?>): String? {
        val raw = item["resourceId"] ?: item["resource_id"] ?: item["id"] ?: return null
        val id = when (raw) {
            is Number -> raw.toLong().toString()
            else -> raw.toString().trim()
        }
        return id.ifEmpty { null }
    }

    fun resolutionOf(item: Map<String, Any?>): Int =
        resolutionKeys.firstNotNullOfOrNull { key ->
            item[key]?.toString()?.filter { it.isDigit() }?.toIntOrNull()
        } ?: 0

    private fun number(item: Map<String, Any?>, keys: List<String>): Int? =
        keys.firstNotNullOfOrNull { key ->
            when (val value = item[key]) {
                is Number -> value.toInt()
                else -> value?.toString()?.trim()?.toDoubleOrNull()?.toInt()
            }
        }
}
