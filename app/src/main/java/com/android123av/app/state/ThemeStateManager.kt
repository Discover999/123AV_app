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

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(AppConstants.THEME_PREFS_NAME, Context.MODE_PRIVATE)
        loadTheme()
    }

    private fun loadTheme() {
        sharedPreferences?.let { prefs ->
            _currentTheme.value = prefs.getInt(AppConstants.KEY_THEME_MODE, AppConstants.THEME_SYSTEM)
        }
    }

    fun setTheme(mode: Int) {
        _currentTheme.value = mode
        sharedPreferences?.edit()?.apply {
            putInt(AppConstants.KEY_THEME_MODE, mode)
            apply()
        }
    }

    fun getTheme(): Int = _currentTheme.value

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
