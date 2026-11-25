package com.example.data.download

/** One selectable episode in the universal download dialog. */
data class DownloadEpisode(
    val season: Int,
    val number: Int,
    val title: String = ""
) {
    val id: String get() = "$season:$number"
    val label: String get() = "S${season.toString().padStart(2, '0')}E${number.toString().padStart(2, '0')}" +
        if (title.isNotBlank()) " · $title" else ""
}

/** Everything the dialog may offer, resolved once from the provider. */
data class DownloadOptions(
    val subjectTitle: String,
    val poster: String,
    val isSeries: Boolean,
    val seasons: List<Int> = emptyList(),
    val episodes: List<DownloadEpisode> = emptyList(),
    val qualities: List<String> = emptyList(),
    val audios: List<com.example.data.model.ResourceDetector> = emptyList(),
    val subject: com.example.data.model.MovieSubject,
    val sourceId: String,
    val provider: String,
    val selectedResourceId: String?,
    val durationMinutes: Int? = null,
    val subtitleLanguages: List<String> = emptyList(),
    val defaultQuality: String = "Highest",
    val preselectedSeason: Int = 1,
    val preselectedEpisode: Int? = null
)

/** The user's confirmed selection. */
data class DownloadChoice(
    val episodes: List<DownloadEpisode>,
    val quality: String,
    val resourceId: String?,
    val sourceId: String,
    val provider: String,
    val fallback: QualityFallback,
    val subtitleLanguages: Set<String>
)

enum class QualityFallback(val label: String) {
    EXACT("Exact only"), LOWER("Nearest lower"), HIGHEST("Highest available")
}

sealed interface DownloadTarget {
    data object Movie : DownloadTarget
    data class SeriesEpisode(val episode: DownloadEpisode) : DownloadTarget
}

fun downloadTargets(series: Boolean, episodes: List<DownloadEpisode>): List<DownloadTarget> =
    if (series) episodes.distinctBy { it.id }.map { DownloadTarget.SeriesEpisode(it) }
    else listOf(DownloadTarget.Movie)

fun selectDownloadStream(streams: List<com.example.data.model.StreamItem>, quality: String, fallback: QualityFallback): com.example.data.model.StreamItem? {
    val available = streams.filter { it.url.isNotBlank() }
    available.firstOrNull { it.quality.equals(quality, true) }?.let { return it }
    fun height(label: String): Int? = when (label.lowercase()) {
        "4k" -> 2160
        "2k" -> 1440
        else -> label.trim().removeSuffix("p").toIntOrNull()
    }
    val ranked = available.mapNotNull { stream -> height(stream.quality)?.let { it to stream } }
    return when (fallback) {
        QualityFallback.EXACT -> null
        QualityFallback.HIGHEST -> ranked.maxByOrNull { it.first }?.second
        QualityFallback.LOWER -> height(quality)?.let { wanted -> ranked.filter { it.first <= wanted }.maxByOrNull { it.first }?.second }
    }
}
