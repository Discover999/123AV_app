package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import com.android123av.app.constants.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WatchHistoryItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val watchedAt: Long,
    val videoCode: String = "",
    val releaseDate: String = "",
    val duration: String = "",
    val performer: String = ""
)

object WatchHistoryManager {
    private var sharedPreferences: SharedPreferences? = null
    
    private val _watchHistory = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val watchHistory: StateFlow<List<WatchHistoryItem>> = _watchHistory.asStateFlow()
    
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(AppConstants.WATCH_HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        loadWatchHistory()
    }
    
    private fun loadWatchHistory() {
        val historyJson = sharedPreferences?.getString(AppConstants.KEY_WATCH_HISTORY, "") ?: ""
        _watchHistory.value = if (historyJson.isNotEmpty()) {
            try {
                historyJson.split("|||").filter { it.isNotEmpty() }.mapNotNull { item ->
                    parseHistoryItem(item)
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    private fun parseHistoryItem(item: String): WatchHistoryItem? {
        return try {
            val parts = item.split("###")
            if (parts.size >= 4) {
                WatchHistoryItem(
                    videoId = parts[0],
                    title = parts[1],
                    thumbnailUrl = parts[2],
                    watchedAt = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    videoCode = parts.getOrNull(4) ?: "",
                    releaseDate = parts.getOrNull(5) ?: "",
                    duration = parts.getOrNull(6) ?: "",
                    performer = parts.getOrNull(7) ?: ""
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun addWatchHistory(
        videoId: String,
        title: String,
        thumbnailUrl: String,
        videoCode: String = "",
        releaseDate: String = "",
        duration: String = "",
        performer: String = ""
    ) {
        if (videoId.isEmpty()) return
        
        val currentHistory = _watchHistory.value
        val newItem = WatchHistoryItem(
            videoId = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            watchedAt = System.currentTimeMillis(),
            videoCode = videoCode,
            releaseDate = releaseDate,
            duration = duration,
            performer = performer
        )
        
        val newHistory = if (currentHistory.any { it.videoId == videoId }) {
            val filtered = currentHistory.filter { it.videoId != videoId }
            listOf(newItem) + filtered
        } else {
            listOf(newItem) + currentHistory.take(AppConstants.MAX_WATCH_HISTORY - 1)
        }
        
        _watchHistory.value = newHistory
        saveWatchHistory()
    }
    
    fun removeWatchHistory(videoId: String) {
        _watchHistory.value = _watchHistory.value.filter { it.videoId != videoId }
        saveWatchHistory()
    }
    
    fun clearWatchHistory() {
        _watchHistory.value = emptyList()
        saveWatchHistory()
    }
    
    fun getWatchHistoryItem(videoId: String): WatchHistoryItem? {
        return _watchHistory.value.find { it.videoId == videoId }
    }
    
    private fun saveWatchHistory() {
        val historyJson = _watchHistory.value.joinToString("|||") { item ->
            "${item.videoId}###${item.title}###${item.thumbnailUrl}###${item.watchedAt}###${item.videoCode}###${item.releaseDate}###${item.duration}###${item.performer}"
        }
        sharedPreferences?.edit()?.apply {
            putString(AppConstants.KEY_WATCH_HISTORY, historyJson)
            apply()
        }
    }
}
