package com.android123av.app.download

import android.content.Context
import android.util.Log
import com.android123av.app.models.VideoDetails
import com.android123av.app.network.fetchVideoDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "VideoDetailsCacheManager"

object VideoDetailsCacheManager {
    
    suspend fun cacheVideoDetails(context: Context, videoId: String, title: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始缓存视频详情: videoId=$videoId")
                val details = fetchVideoDetails(videoId)
                if (details != null) {
                    Log.d(TAG, "获取到视频详情: code=${details.code}")
                    saveVideoDetailsToDatabase(context, videoId, details, title)
                    Log.d(TAG, "视频详情已保存到缓存")
                } else {
                    Log.w(TAG, "fetchVideoDetails 返回 null: videoId=$videoId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "缓存视频详情失败: videoId=$videoId", e)
            }
        }
    }
    
    suspend fun getVideoDetails(context: Context, videoId: String): VideoDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val database = DownloadDatabase.getInstance(context)
                val cached = database.cachedVideoDetailsDao().getVideoDetails(videoId)
                if (cached != null) {
                    Log.d(TAG, "从缓存加载视频详情成功: videoId=$videoId, code=${cached.code}")
                    cached.toVideoDetails()
                } else {
                    Log.w(TAG, "缓存中未找到视频详情: videoId=$videoId")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载缓存视频详情失败: videoId=$videoId", e)
                null
            }
        }
    }
    
    suspend fun getCachedTitle(context: Context, videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val database = DownloadDatabase.getInstance(context)
                val cached = database.cachedVideoDetailsDao().getVideoDetails(videoId)
                if (cached != null) {
                    cached.title.ifBlank { cached.code }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun removeVideoDetails(context: Context, videoId: String) {
        withContext(Dispatchers.IO) {
            try {
                val database = DownloadDatabase.getInstance(context)
                database.cachedVideoDetailsDao().deleteVideoDetails(videoId)
            } catch (e: Exception) {
            }
        }
    }
    
    private suspend fun saveVideoDetailsToDatabase(context: Context, videoId: String, details: VideoDetails, title: String?) {
        try {
            val database = DownloadDatabase.getInstance(context)
            val cached = CachedVideoDetails(
                videoId = videoId,
                code = details.code,
                title = title ?: "",
                releaseDate = details.releaseDate,
                duration = details.duration,
                performer = details.performer,
                genres = details.genres.joinToString(separator = "|||"),
                maker = details.maker,
                tags = details.tags.joinToString(separator = "|||"),
                favouriteCount = details.favouriteCount,
                cachedAt = System.currentTimeMillis()
            )
            database.cachedVideoDetailsDao().insertVideoDetails(cached)
            Log.d(TAG, "视频详情已保存到数据库: videoId=$videoId")
        } catch (e: Exception) {
            Log.e(TAG, "保存视频详情到数据库失败: videoId=$videoId", e)
        }
    }
}
