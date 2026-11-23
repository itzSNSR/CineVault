package com.example.util

import java.net.URLDecoder

/**
 * Conservative subtitle episode check. Rejects a proven mismatch; unknown
 * filenames are accepted only when requested from the current episode's API.
 */
object SubtitleEpisodeMatcher {
    // A static subtitle URL cannot belong to two different episodes of one show.
    // Track URLs within this app process to catch providers returning E1 for E2.
    private val urlOwners = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun claimUrl(subjectId: String, season: Int?, episode: Int?, url: String): Boolean {
        if (episode == null || episode <= 0) return true
        if (urlOwners.size > 5000) urlOwners.clear()
        val owner = "$season:$episode"
        val old = urlOwners.putIfAbsent("$subjectId|$url", owner)
        return old == null || old == owner
    }

    private val seasonEpisode = Regex("""(?i)(?:^|[^a-z0-9])s(?:eason)?[ ._-]*0*(\d{1,3})[ ._-]*e(?:p(?:isode)?)?[ ._-]*0*(\d{1,5})(?!\d)""")
    private val episodeLabel = Regex("""(?i)(?:^|[^a-z0-9])(?:episode|ep|e)[ ._#-]*0*(\d{1,5})(?!\d)""")

    private fun asNumber(value: Any?): Int? {
        val valueText = value?.toString()?.trim().orEmpty()
        return valueText.toIntOrNull()
            ?: valueText.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
    }

    private fun metadataNumber(values: Map<String, Any?>, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key -> asNumber(values[key]) }

    fun belongsToEpisode(
        subtitle: Map<String, Any?>,
        season: Int?,
        episode: Int?
    ): Boolean {
        if (episode == null || episode <= 0) return true // Movies have no episode number.

        val metadataEpisode = metadataNumber(
            subtitle, "episode", "ep", "episodeNumber", "episode_number"
        )
        if (metadataEpisode != null && metadataEpisode != episode) return false

        val metadataSeason = metadataNumber(
            subtitle, "season", "se", "seasonNumber", "season_number"
        )
        if (season != null && metadataSeason != null && metadataSeason != season) return false

        val names = listOf("fileName", "filename", "subName", "title", "name", "url")
        for (key in names) {
            val raw = subtitle[key]?.toString()?.takeIf { it.isNotBlank() } ?: continue
            // Remove signed CDN parameters: numbers in signatures are not episode IDs.
            val basename = raw.substringBefore('?').substringAfterLast('/')
            val name = try {
                URLDecoder.decode(basename, "UTF-8")
            } catch (_: Exception) {
                basename
            }

            val seasonEp = seasonEpisode.find(name)
            if (seasonEp != null) {
                val namedSeason = seasonEp.groupValues[1].toIntOrNull()
                val namedEp = seasonEp.groupValues[2].toIntOrNull()
                if (namedEp != episode || (season != null && namedSeason != season)) return false
                continue
            }

            val namedEp = episodeLabel.find(name)?.groupValues?.get(1)?.toIntOrNull()
            if (namedEp != null && namedEp != episode) return false
        }
        return true
    }
}
