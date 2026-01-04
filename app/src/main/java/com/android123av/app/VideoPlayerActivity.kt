package com.android123av.app

import android.content.Intent
import android.os.Bundle
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
import com.android123av.app.ui.theme.MyApplicationTheme
import java.io.File

class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val video = intent.getParcelableExtra<Video>("video")
        val localVideoPath = intent.getStringExtra("localVideoPath")
        
        setContent {
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



