package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val poster: String,
    val isTvShow: Boolean,
    val rating: String? = null,
    val year: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey val id: String, // format: subjectId or subjectId_S{season}_E{episode}
    val subjectId: String,
    val name: String,
    val poster: String,
    val isTvShow: Boolean,
    val season: Int,
    val episode: Int,
    val seeTime: Long,      // Playback progress position in ms
    val totalTime: Long,    // Total media duration in ms
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_search", primaryKeys = ["source", "normalized"])
data class RecentSearch(val source: String, val normalized: String, val term: String, val searchedAt: Long)

@Entity(tableName = "favorite_channel")
data class FavoriteChannel(@PrimaryKey val channelKey: String, val country: String, val name: String)

fun historyKey(aniFam: Boolean, subjectId: String, season: Int, episode: Int): String {
    val provider = if (aniFam) "anifam" else "moviebox"
    val raw = subjectId.removePrefix("${provider}_")
    val suffix = if (episode > 0) "_S${season}_E${episode}" else ""
    return "${provider}_$raw$suffix"
}

val HistoryEntity.resumePosition: Long
    get() = seeTime.takeIf { it > 0 && (totalTime <= 0 || totalTime - it > minOf(10_000L, totalTime / 20)) } ?: 0L
