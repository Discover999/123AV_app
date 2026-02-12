package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 画中画(PiP)设置管理器
 * 管理用户的PiP相关偏好设置，包括返回自动小窗播放、动画效果等
 */
object PipSettingsManager {
    private const val PREFS_NAME = "pip_settings"
    private const val KEY_AUTO_PIP_ON_BACK = "auto_pip_on_back"
    private const val KEY_PIP_ANIMATION_ENABLED = "pip_animation_enabled"
    private const val KEY_PIP_ANIMATION_DURATION = "pip_animation_duration"
    private const val KEY_LAST_PIP_SIZE_SCALE = "last_pip_size_scale"
    
    // 默认值
    private const val DEFAULT_AUTO_PIP_ON_BACK = true
    private const val DEFAULT_PIP_ANIMATION_ENABLED = true
    private const val DEFAULT_PIP_ANIMATION_DURATION = 300 // ms
    private const val DEFAULT_PIP_SIZE_SCALE = 0.5f
    
    // 运行时状态
    private val _autoPopOnBack = MutableStateFlow(DEFAULT_AUTO_PIP_ON_BACK)
    val autoPopOnBack: StateFlow<Boolean> = _autoPopOnBack.asStateFlow()
    
    private val _pipAnimationEnabled = MutableStateFlow(DEFAULT_PIP_ANIMATION_ENABLED)
    val pipAnimationEnabled: StateFlow<Boolean> = _pipAnimationEnabled.asStateFlow()
    
    private val _pipAnimationDuration = MutableStateFlow(DEFAULT_PIP_ANIMATION_DURATION)
    val pipAnimationDuration: StateFlow<Int> = _pipAnimationDuration.asStateFlow()
    
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
            _pipAnimationEnabled.value = it.getBoolean(KEY_PIP_ANIMATION_ENABLED, DEFAULT_PIP_ANIMATION_ENABLED)
            _pipAnimationDuration.value = it.getInt(KEY_PIP_ANIMATION_DURATION, DEFAULT_PIP_ANIMATION_DURATION)
            _lastPipSizeScale.value = it.getFloat(KEY_LAST_PIP_SIZE_SCALE, DEFAULT_PIP_SIZE_SCALE)
        }
    }
    
    /**
     * 设置返回时自动进入小窗播放
     */
    fun setAutoPopOnBack(enabled: Boolean) {
        _autoPopOnBack.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_PIP_ON_BACK, enabled)?.apply()
    }
    
    /**
     * 设置PiP动画是否启用
     */
    fun setPipAnimationEnabled(enabled: Boolean) {
        _pipAnimationEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_PIP_ANIMATION_ENABLED, enabled)?.apply()
    }
    
    /**
     * 设置PiP动画时长(毫秒)
     */
    fun setPipAnimationDuration(duration: Int) {
        _pipAnimationDuration.value = duration
        prefs?.edit()?.putInt(KEY_PIP_ANIMATION_DURATION, duration)?.apply()
    }
    
    /**
     * 保存上次PiP窗口大小
     */
    fun setSizeScale(scale: Float) {
        _lastPipSizeScale.value = scale
        prefs?.edit()?.putFloat(KEY_LAST_PIP_SIZE_SCALE, scale)?.apply()
    }
    
    /**
     * 获取当前自动弹出小窗设置
     */
    fun isAutoPopOnBackEnabled(): Boolean = _autoPopOnBack.value
    
    /**
     * 获取当前PiP动画是否启用
     */
    fun isPipAnimationEnabled(): Boolean = _pipAnimationEnabled.value
    
    /**
     * 获取当前PiP动画时长
     */
    fun getPipAnimationDuration(): Int = _pipAnimationDuration.value
    
    /**
     * 获取上次PiP窗口大小
     */
    fun getLastSizeScale(): Float = _lastPipSizeScale.value
    
    /**
     * 重置所有设置为默认值
     */
    fun resetToDefaults() {
        _autoPopOnBack.value = DEFAULT_AUTO_PIP_ON_BACK
        _pipAnimationEnabled.value = DEFAULT_PIP_ANIMATION_ENABLED
        _pipAnimationDuration.value = DEFAULT_PIP_ANIMATION_DURATION
        _lastPipSizeScale.value = DEFAULT_PIP_SIZE_SCALE
        prefs?.edit()?.clear()?.apply()
    }
}
