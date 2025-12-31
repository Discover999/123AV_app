package com.android123av.app.repository

import android.content.Context
import com.android123av.app.models.Video
import com.android123av.app.models.VideoDetails
import com.android123av.app.network.fetchM3u8UrlWithWebView
import com.android123av.app.network.fetchVideoDetails
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.fetchVideoUrlParallel

class VideoRepository {
    
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
    
    suspend fun fetchVideoDetails(videoId: String): Result<VideoDetails> {
        return try {
            val details = com.android123av.app.network.fetchVideoDetails(videoId)
            if (details != null) {
                Result.success(details)
            } else {
                Result.failure(Exception("无法获取视频详情"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
