package com.android123av.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.android123av.app.screens.SettingsScreen
import com.android123av.app.state.DownloadPathManager
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.ui.theme.MyApplicationTheme
import com.android123av.app.utils.ActivityUtils

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ThemeStateManager.initialize(this)
        DownloadPathManager.initialize(this)
        ActivityUtils.updateStatusBarColor(this)
        
        setContent {
            val currentTheme by ThemeStateManager.currentTheme.collectAsState()
            
            LaunchedEffect(currentTheme) {
                ActivityUtils.updateStatusBarColor(this@SettingsActivity)
            }
            
            MyApplicationTheme {
                SettingsScreen(
                    onNavigateBack = { finish() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
