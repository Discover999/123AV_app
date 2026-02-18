package com.android123av.app.cache

import android.util.Log
import com.android123av.app.models.VideoDetails
import com.android123av.app.models.VideoPart

private const val TAG = "VideoSessionCache"

data class CachedVideoInfo(
    val videoUrl: String?,
    val videoParts: List<VideoPart>,
    val videoDetails: VideoDetails?,
    val timestamp: Long = System.currentTimeMillis()
)

object VideoSessionCache {
    private const val MAX_CACHE_SIZE = 30
    private const val CACHE_EXPIRATION_MS = 30 * 60 * 1000L

    private val cache = LinkedHashMap<String, CachedVideoInfo>(16, 0.75f, true)
    private val lock = Any()

    fun put(
        videoId: String,
        videoUrl: String? = null,
        videoParts: List<VideoPart> = emptyList(),
        videoDetails: VideoDetails? = null
    ) {
        if (videoId.isBlank()) return

        synchronized(lock) {
            val existing = cache[videoId]
            val mergedInfo = CachedVideoInfo(
                videoUrl = videoUrl ?: existing?.videoUrl,
                videoParts = if (videoParts.isNotEmpty()) videoParts else existing?.videoParts ?: emptyList(),
                videoDetails = videoDetails ?: existing?.videoDetails,
                timestamp = System.currentTimeMillis()
            )
            cache[videoId] = mergedInfo
            Log.d(TAG, "缓存视频信息: videoId=$videoId, videoUrl=${videoUrl != null}, parts=${videoParts.size}, details=${videoDetails != null}")
        }
    }

    fun getVideoUrl(videoId: String): String? {
        synchronized(lock) {
            val cached = cache[videoId]
            return if (cached != null && !isExpired(cached)) {
                Log.d(TAG, "从缓存获取视频URL: videoId=$videoId")
                cached.videoUrl
            } else {
                null
            }
        }
    }

    fun getVideoParts(videoId: String): List<VideoPart> {
        synchronized(lock) {
            val cached = cache[videoId]
            return if (cached != null && !isExpired(cached)) {
                Log.d(TAG, "从缓存获取视频部分: videoId=$videoId, parts=${cached.videoParts.size}")
                cached.videoParts
            } else {
                emptyList()
            }
        }
    }

    fun getVideoDetails(videoId: String): VideoDetails? {
        synchronized(lock) {
            val cached = cache[videoId]
            return if (cached != null && !isExpired(cached)) {
                Log.d(TAG, "从缓存获取视频详情: videoId=$videoId")
                cached.videoDetails
            } else {
                null
            }
        }
    }

    fun get(videoId: String): CachedVideoInfo? {
        synchronized(lock) {
            val cached = cache[videoId]
            return if (cached != null && !isExpired(cached)) {
                cached
            } else {
                null
            }
        }
    }

    fun hasValidCache(videoId: String): Boolean {
        synchronized(lock) {
            val cached = cache[videoId]
            return cached != null && !isExpired(cached) && cached.videoUrl != null
        }
    }

    fun remove(videoId: String) {
        synchronized(lock) {
            cache.remove(videoId)
            Log.d(TAG, "移除缓存: videoId=$videoId")
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
            Log.d(TAG, "清空所有会话缓存")
        }
    }

    fun cleanExpired() {
        synchronized(lock) {
            val iterator = cache.entries.iterator()
            var removed = 0
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (isExpired(entry.value)) {
                    iterator.remove()
                    removed++
                }
            }
            if (removed > 0) {
                Log.d(TAG, "清理过期缓存: $removed 条")
            }
        }
    }

    private fun isExpired(cached: CachedVideoInfo): Boolean {
        return System.currentTimeMillis() - cached.timestamp > CACHE_EXPIRATION_MS
    }

    fun getCacheSize(): Int {
        synchronized(lock) {
            return cache.size
        }
    }
}
