package com.android123av.app.state

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import java.io.File

object DownloadPathManager {
    private const val PREF_NAME = "download_path_prefs"
    private const val KEY_CUSTOM_PATH = "custom_download_path"
    private const val KEY_USE_CUSTOM_PATH = "use_custom_path"
    
    private const val DEFAULT_DOWNLOAD_PATH = ""
    
    private var currentPathState = mutableStateOf("")
    
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_PATH, DEFAULT_DOWNLOAD_PATH) ?: DEFAULT_DOWNLOAD_PATH
        val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_PATH, false)
        currentPathState.value = if (useCustom) customPath else DEFAULT_DOWNLOAD_PATH
    }
    
    fun getDefaultPath(context: Context): String {
        val defaultDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(defaultDir, "123AV_Downloads").absolutePath
    }
    
    fun getCurrentPath(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_PATH, false)
        val customPath = prefs.getString(KEY_CUSTOM_PATH, DEFAULT_DOWNLOAD_PATH) ?: DEFAULT_DOWNLOAD_PATH
        
        return if (useCustom && customPath.isNotEmpty()) {
            customPath
        } else {
            getDefaultPath(context)
        }
    }
    
    fun getDisplayPath(context: Context): String {
        val path = getCurrentPath(context)
        val defaultPath = getDefaultPath(context)
        
        return if (path == defaultPath) {
            "默认位置"
        } else {
            path
        }
    }
    
    fun isUsingDefaultPath(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_PATH, false)
        return !useCustom
    }
    
    fun setCustomPath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CUSTOM_PATH, path)
            putBoolean(KEY_USE_CUSTOM_PATH, path.isNotEmpty())
            apply()
        }
        currentPathState.value = path
    }
    
    fun resetToDefault(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_CUSTOM_PATH, DEFAULT_DOWNLOAD_PATH)
            putBoolean(KEY_USE_CUSTOM_PATH, false)
            apply()
        }
        currentPathState.value = DEFAULT_DOWNLOAD_PATH
    }
    
    fun getCurrentPathState() = currentPathState
}
