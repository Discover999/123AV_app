package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import com.android123av.app.constants.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PipSettingsManager {
    private const val PREFS_NAME = "pip_settings"
    private const val KEY_AUTO_PIP_ON_BACK = "auto_pip_on_back"
    private const val KEY_LAST_PIP_SIZE_SCALE = "last_pip_size_scale"
    
    private const val DEFAULT_AUTO_PIP_ON_BACK = true
    private const val DEFAULT_PIP_SIZE_SCALE = 0.5f
    
    private val _autoPopOnBack = MutableStateFlow(DEFAULT_AUTO_PIP_ON_BACK)
    val autoPopOnBack: StateFlow<Boolean> = _autoPopOnBack.asStateFlow()
    
    private val _lastPipSizeScale = MutableStateFlow(DEFAULT_PIP_SIZE_SCALE)
    val lastPipSizeScale: StateFlow<Float> = _lastPipSizeScale.asStateFlow()
    
    private var prefs: SharedPreferences? = null
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) 
        loadSettings()
    }
    
    private fun loadSettings() {
        prefs?.let {
            _autoPopOnBack.value = it.getBoolean(KEY_AUTO_PIP_ON_BACK, DEFAULT_AUTO_PIP_ON_BACK)
            _lastPipSizeScale.value = it.getFloat(KEY_LAST_PIP_SIZE_SCALE, DEFAULT_PIP_SIZE_SCALE)
        }
    }
    
    fun setAutoPopOnBack(enabled: Boolean) {
        _autoPopOnBack.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_PIP_ON_BACK, enabled)?.apply()
    }
    
    fun setSizeScale(scale: Float) {
        _lastPipSizeScale.value = scale
        prefs?.edit()?.putFloat(KEY_LAST_PIP_SIZE_SCALE, scale)?.apply()
    }
    
    fun isAutoPopOnBackEnabled(): Boolean = _autoPopOnBack.value
    
    fun isPipAnimationEnabled(): Boolean = AppConstants.PIP_ANIMATION_ENABLED
    
    fun getPipAnimationDuration(): Int = AppConstants.PIP_ANIMATION_DURATION_MS
    
    fun getLastSizeScale(): Float = _lastPipSizeScale.value
    
    fun resetToDefaults() {
        _autoPopOnBack.value = DEFAULT_AUTO_PIP_ON_BACK
        _lastPipSizeScale.value = DEFAULT_PIP_SIZE_SCALE
        prefs?.edit()?.clear()?.apply()
    }
}
