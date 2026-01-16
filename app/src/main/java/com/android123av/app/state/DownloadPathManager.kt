package com.android123av.app.state

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import java.io.File
import com.android123av.app.constants.AppConstants

object DownloadPathManager {
    private var currentPathState = mutableStateOf("")
    
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(AppConstants.DOWNLOAD_PATH_PREFS_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(AppConstants.KEY_CUSTOM_PATH, "") ?: ""
        val useCustom = prefs.getBoolean(AppConstants.KEY_USE_CUSTOM_PATH, false)
        currentPathState.value = if (useCustom) customPath else ""
    }
    
    fun getDefaultPath(context: Context): String {
        val defaultDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(defaultDir, AppConstants.DEFAULT_DOWNLOAD_DIR).absolutePath
    }
    
    fun getCurrentPath(context: Context): String {
        val prefs = context.getSharedPreferences(AppConstants.DOWNLOAD_PATH_PREFS_NAME, Context.MODE_PRIVATE)
        val useCustom = prefs.getBoolean(AppConstants.KEY_USE_CUSTOM_PATH, false)
        val customPath = prefs.getString(AppConstants.KEY_CUSTOM_PATH, "") ?: ""
        
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
        val prefs = context.getSharedPreferences(AppConstants.DOWNLOAD_PATH_PREFS_NAME, Context.MODE_PRIVATE)
        val useCustom = prefs.getBoolean(AppConstants.KEY_USE_CUSTOM_PATH, false)
        return !useCustom
    }
    
    fun setCustomPath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(AppConstants.DOWNLOAD_PATH_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(AppConstants.KEY_CUSTOM_PATH, path)
            putBoolean(AppConstants.KEY_USE_CUSTOM_PATH, path.isNotEmpty())
            apply()
        }
        currentPathState.value = path
    }
    
    fun resetToDefault(context: Context) {
        val prefs = context.getSharedPreferences(AppConstants.DOWNLOAD_PATH_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(AppConstants.KEY_CUSTOM_PATH, "")
            putBoolean(AppConstants.KEY_USE_CUSTOM_PATH, false)
            apply()
        }
        currentPathState.value = ""
    }
    
    fun getCurrentPathState() = currentPathState
}
