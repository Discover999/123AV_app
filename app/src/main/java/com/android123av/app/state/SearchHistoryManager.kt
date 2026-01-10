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

object SearchHistoryManager {
    private const val PREFS_NAME = "search_history_prefs"
    private const val KEY_SEARCH_HISTORY = "search_history"
    
    private var sharedPreferences: SharedPreferences? = null
    
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSearchHistory()
    }
    
    private fun loadSearchHistory() {
        val historyJson = sharedPreferences?.getString(KEY_SEARCH_HISTORY, "") ?: ""
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
            listOf(query) + searchHistory.take(19)
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
            putString(KEY_SEARCH_HISTORY, searchHistory.joinToString("|"))
            apply()
        }
    }
}
