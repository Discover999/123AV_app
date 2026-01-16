package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android123av.app.constants.AppConstants

object SearchHistoryManager {
    private var sharedPreferences: SharedPreferences? = null
    
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(AppConstants.SEARCH_HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        loadSearchHistory()
    }
    
    private fun loadSearchHistory() {
        val historyJson = sharedPreferences?.getString(AppConstants.KEY_SEARCH_HISTORY, "") ?: ""
        searchHistory = if (historyJson.isNotEmpty()) {
            try {
                historyJson.split("|").filter { it.isNotEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    fun addSearchHistory(query: String) {
        if (query.isEmpty()) return
        
        val newHistory = if (query in searchHistory) {
            val filtered = searchHistory.filter { it != query }
            listOf(query) + filtered
        } else {
            listOf(query) + searchHistory.take(AppConstants.MAX_SEARCH_HISTORY)
        }
        
        searchHistory = newHistory
        saveSearchHistory()
    }
    
    fun removeSearchHistory(query: String) {
        searchHistory = searchHistory.filter { it != query }
        saveSearchHistory()
    }
    
    fun clearSearchHistory() {
        searchHistory = emptyList()
        saveSearchHistory()
    }
    
    private fun saveSearchHistory() {
        sharedPreferences?.edit()?.apply {
            putString(AppConstants.KEY_SEARCH_HISTORY, searchHistory.joinToString("|"))
            apply()
        }
    }
}
