package com.android123av.app.utils

import android.app.Activity
import androidx.core.view.WindowCompat
import com.android123av.app.state.ThemeStateManager

object ActivityUtils {
    fun updateStatusBarColor(activity: Activity) {
        val isLightTheme = !ThemeStateManager.isDarkTheme()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = isLightTheme
        }
    }
}
