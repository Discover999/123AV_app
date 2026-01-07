package com.android123av.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.android123av.app.models.Video
import com.android123av.app.screens.VideoPlayerScreen
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.ui.theme.MyApplicationTheme

private fun ComponentActivity.updateStatusBarColor() {
    val isLightTheme = !ThemeStateManager.isDarkTheme()
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = isLightTheme
    }
}

class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ThemeStateManager.initialize(this)
        updateStatusBarColor()
        
        val video = intent.getParcelableExtra<Video>("video")
        val localVideoPath = intent.getStringExtra("localVideoPath")
        
        setContent {
            val currentTheme by ThemeStateManager.currentTheme.collectAsState()
            
            LaunchedEffect(currentTheme) {
                updateStatusBarColor()
            }
            
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerScreen(
                        modifier = Modifier.padding(it),
                        video = video,
                        localVideoPath = localVideoPath,
                        onBack = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}



