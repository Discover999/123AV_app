package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeStateManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM = 2

    private var sharedPreferences: SharedPreferences? = null

    private val _currentTheme = MutableStateFlow(THEME_SYSTEM)
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadTheme()
    }

    private fun loadTheme() {
        sharedPreferences?.let { prefs ->
            _currentTheme.value = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        }
    }

    fun setTheme(mode: Int) {
        _currentTheme.value = mode
        sharedPreferences?.edit()?.apply {
            putInt(KEY_THEME_MODE, mode)
            apply()
        }
    }

    fun getTheme(): Int = _currentTheme.value

    fun isDarkTheme(): Boolean {
        return when (_currentTheme.value) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            THEME_SYSTEM -> android.content.res.Configuration.UI_MODE_NIGHT_YES ==
                (android.content.res.Resources.getSystem().configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            else -> false
        }
    }
}
