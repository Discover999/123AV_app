package com.android123av.app.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android123av.app.constants.AppConstants

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(
    tableName = "download_tasks",
    indices = [
        androidx.room.Index(value = ["videoId"], unique = true),
        androidx.room.Index(value = ["status"]),
        androidx.room.Index(value = ["createdAt"])
    ]
)
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
    val progress: Float = 0f,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val downloadSpeed: Long = 0,
    val duration: Long = 0,
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
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) * 100f else progress

    val progressDisplay: String
        get() = String.format("%.2f%%", progressPercent)

    val speedDisplay: String
        get() = formatSpeed(downloadSpeed)

    companion object {
        fun formatSpeed(bytesPerSecond: Long): String {
            return when {
                bytesPerSecond >= AppConstants.BYTES_IN_GB -> String.format("%.2f GB/s", bytesPerSecond / (AppConstants.BYTES_IN_GB.toDouble()))
                bytesPerSecond >= AppConstants.BYTES_IN_MB -> String.format("%.2f MB/s", bytesPerSecond / (AppConstants.BYTES_IN_MB.toDouble()))
                bytesPerSecond >= AppConstants.BYTES_IN_KB -> String.format("%.2f KB/s", bytesPerSecond / AppConstants.BYTES_IN_KB.toDouble())
                else -> "$bytesPerSecond B/s"
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= AppConstants.BYTES_IN_GB -> String.format("%.2f GB", bytes / (AppConstants.BYTES_IN_GB.toDouble()))
                bytes >= AppConstants.BYTES_IN_MB -> String.format("%.2f MB", bytes / (AppConstants.BYTES_IN_MB.toDouble()))
                bytes >= AppConstants.BYTES_IN_KB -> String.format("%.2f KB", bytes / AppConstants.BYTES_IN_KB.toDouble())
                else -> "$bytes B"
            }
        }

        fun formatDuration(seconds: Long): String {
            val hours = seconds / AppConstants.SECONDS_IN_HOUR
            val minutes = (seconds % AppConstants.SECONDS_IN_HOUR) / AppConstants.SECONDS_IN_MINUTE
            val secs = seconds % AppConstants.SECONDS_IN_MINUTE
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format("%d:%02d", minutes, secs)
            }
        }
    }
}

data class DownloadSegment(
    val url: String,
    val duration: Float,
    val bandwidth: Int?,
    val resolution: String?,
    val estimatedBytes: Long = 0
)

data class M3U8Playlist(
    val segments: List<DownloadSegment>,
    val bandwidth: Int?,
    val resolution: String?,
    val isMasterPlaylist: Boolean
)
