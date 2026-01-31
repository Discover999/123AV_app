package com.android123av.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.android123av.app.models.Video
import com.android123av.app.screens.VideoPlayerScreen
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.ui.theme.MyApplicationTheme
import com.android123av.app.utils.ActivityUtils

class VideoPlayerActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ThemeStateManager.initialize(this)
        ActivityUtils.updateStatusBarColor(this)
        
        val video = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("video", Video::class.java)
        } else {
            intent.getParcelableExtra("video")
        }
        val localVideoPath = intent.getStringExtra("localVideoPath")
        val videoId = intent.getStringExtra("videoId")
        
        Log.d("VideoPlayerActivity", "onCreate: videoId=$videoId, localVideoPath=$localVideoPath")
        
        setContent {
            val currentTheme by ThemeStateManager.currentTheme.collectAsState()
            
            LaunchedEffect(currentTheme) {
                ActivityUtils.updateStatusBarColor(this@VideoPlayerActivity)
            }
            
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerScreen(
                        modifier = Modifier.padding(it),
                        video = video,
                        localVideoPath = localVideoPath,
                        localVideoId = videoId,
                        onBack = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}



