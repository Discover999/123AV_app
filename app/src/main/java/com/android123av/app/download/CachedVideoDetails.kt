package com.android123av.app.download

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_video_details",
    indices = [
        androidx.room.Index(value = ["videoId"], unique = true),
        androidx.room.Index(value = ["cachedAt"])
    ]
)
data class CachedVideoDetails(
    @PrimaryKey
    val videoId: String,
    val code: String,
    val title: String,
    val releaseDate: String,
    val duration: String,
    val performer: String,
    val genres: String,
    val genreHrefs: String = "",
    val maker: String,
    val tags: String,
    val tagHrefs: String = "",
    val favouriteCount: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromVideoDetails(videoId: String, details: com.android123av.app.models.VideoDetails): CachedVideoDetails {
            return CachedVideoDetails(
                videoId = videoId,
                code = details.code,
                title = "",
                releaseDate = details.releaseDate,
                duration = details.duration,
                performer = details.performer,
                genres = details.genres.joinToString(separator = "|||"),
                genreHrefs = details.genreHrefs.joinToString(separator = "|||"),
                maker = details.maker,
                tags = details.tags.joinToString(separator = "|||"),
                tagHrefs = details.tagHrefs.joinToString(separator = "|||"),
                favouriteCount = details.favouriteCount,
                cachedAt = System.currentTimeMillis()
            )
        }
    }

    fun toVideoDetails(): com.android123av.app.models.VideoDetails {
        return com.android123av.app.models.VideoDetails(
            code = code,
            releaseDate = releaseDate,
            duration = duration,
            performer = performer,
            genres = if (genres.isBlank()) emptyList() else genres.split("|||"),
            genreHrefs = if (genreHrefs.isBlank()) emptyList() else genreHrefs.split("|||"),
            maker = maker,
            tags = if (tags.isBlank()) emptyList() else tags.split("|||"),
            tagHrefs = if (tagHrefs.isBlank()) emptyList() else tagHrefs.split("|||"),
            favouriteCount = favouriteCount
        )
    }
}
