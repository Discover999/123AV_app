package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.android123av.app.constants.AppConstants

object ThemeStateManager {
    private var sharedPreferences: SharedPreferences? = null

    private val _currentTheme = MutableStateFlow(AppConstants.THEME_SYSTEM)
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _customColorSeed = MutableStateFlow(0xFF6750A4)
    val customColorSeed: StateFlow<Long> = _customColorSeed.asStateFlow()

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(AppConstants.THEME_PREFS_NAME, Context.MODE_PRIVATE)
        loadTheme()
        loadDynamicColor()
        loadCustomColorSeed()
    }

    private fun loadTheme() {
        sharedPreferences?.let { prefs ->
            _currentTheme.value = prefs.getInt(AppConstants.KEY_THEME_MODE, AppConstants.THEME_SYSTEM)
        }
    }

    private fun loadDynamicColor() {
        sharedPreferences?.let { prefs ->
            _dynamicColor.value = prefs.getBoolean(AppConstants.KEY_DYNAMIC_COLOR, true)
        }
    }

    private fun loadCustomColorSeed() {
        sharedPreferences?.let { prefs ->
            _customColorSeed.value = prefs.getLong(AppConstants.KEY_CUSTOM_COLOR_SEED, 0xFF6750A4)
        }
    }

    fun setTheme(mode: Int) {
        _currentTheme.value = mode
        sharedPreferences?.edit()?.apply {
            putInt(AppConstants.KEY_THEME_MODE, mode)
            apply()
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        sharedPreferences?.edit()?.apply {
            putBoolean(AppConstants.KEY_DYNAMIC_COLOR, enabled)
            apply()
        }
    }

    fun setCustomColorSeed(seed: Long) {
        _customColorSeed.value = seed
        sharedPreferences?.edit()?.apply {
            putLong(AppConstants.KEY_CUSTOM_COLOR_SEED, seed)
            apply()
        }
    }

    fun getTheme(): Int = _currentTheme.value

    fun isDynamicColorEnabled(): Boolean = _dynamicColor.value

    fun getCustomColorSeed(): Long = _customColorSeed.value

    fun isDarkTheme(): Boolean {
        return when (_currentTheme.value) {
            AppConstants.THEME_LIGHT -> false
            AppConstants.THEME_DARK -> true
            AppConstants.THEME_SYSTEM -> android.content.res.Configuration.UI_MODE_NIGHT_YES ==
                (android.content.res.Resources.getSystem().configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            else -> false
        }
    }
}
