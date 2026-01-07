package com.android123av.app.repository

import android.content.Context
import com.android123av.app.download.CachedVideoDetails
import com.android123av.app.download.DownloadDatabase
import com.android123av.app.models.Video
import com.android123av.app.models.VideoDetails
import com.android123av.app.network.fetchM3u8UrlWithWebView
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.fetchVideoUrlParallel

class VideoRepository(private val context: Context) {
    
    private val database: DownloadDatabase by lazy {
        DownloadDatabase.getInstance(context)
    }
    
    suspend fun fetchVideoUrl(
        context: Context,
        video: Video,
        timeoutMs: Long = 6000L
    ): Result<String> {
        return try {
            if (!video.videoUrl.isNullOrBlank()) {
                Result.success(video.videoUrl)
            } else {
                val url = fetchVideoUrlParallel(context, video.id, timeoutMs)
                    ?: run {
                        val httpUrl = fetchVideoUrl(video.id)
                        if (httpUrl?.contains(".m3u8") == true) {
                            httpUrl
                        } else {
                            fetchM3u8UrlWithWebView(context, video.id)
                        }
                    }
                
                if (url != null) {
                    Result.success(url)
                } else {
                    Result.failure(Exception("无法获取视频播放地址"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun fetchVideoDetails(videoId: String, useCache: Boolean = true): Result<VideoDetails> {
        if (useCache) {
            val cachedDetails = getCachedVideoDetails(videoId)
            if (cachedDetails != null) {
                return Result.success(cachedDetails)
            }
        }
        
        return try {
            val details = com.android123av.app.network.fetchVideoDetails(videoId)
            if (details != null) {
                cacheVideoDetails(videoId, details)
                Result.success(details)
            } else {
                Result.failure(Exception("无法获取视频详情"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCachedVideoDetails(videoId: String): VideoDetails? {
        return try {
            val cached = database.cachedVideoDetailsDao().getVideoDetails(videoId)
            cached?.toVideoDetails()
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun cacheVideoDetails(videoId: String, details: VideoDetails) {
        try {
            val cached = CachedVideoDetails.fromVideoDetails(videoId, details)
            database.cachedVideoDetailsDao().insertVideoDetails(cached)
        } catch (e: Exception) {
        }
    }
    
    suspend fun removeCachedVideoDetails(videoId: String) {
        try {
            database.cachedVideoDetailsDao().deleteVideoDetails(videoId)
        } catch (e: Exception) {
        }
    }
    
    suspend fun clearOldCache(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000) {
        try {
            val cutoffTime = System.currentTimeMillis() - maxAgeMillis
            database.cachedVideoDetailsDao().deleteOldCache(cutoffTime)
        } catch (e: Exception) {
        }
    }
    
    suspend fun getCacheCount(): Int {
        return try {
            database.cachedVideoDetailsDao().getCacheCount()
        } catch (e: Exception) {
            0
        }
    }
}
