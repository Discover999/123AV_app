package com.android123av.app.download

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val downloadUrl: String?,
    val savePath: String,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
) {
    val isCompleted: Boolean
        get() = status == DownloadStatus.COMPLETED
    
    val isDownloading: Boolean
        get() = status == DownloadStatus.DOWNLOADING
    
    val canResume: Boolean
        get() = status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED
    
    val progressPercent: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

data class DownloadSegment(
    val url: String,
    val duration: Float,
    val bandwidth: Int?,
    val resolution: String?
)

data class M3U8Playlist(
    val segments: List<DownloadSegment>,
    val bandwidth: Int?,
    val resolution: String?,
    val isMasterPlaylist: Boolean
)
