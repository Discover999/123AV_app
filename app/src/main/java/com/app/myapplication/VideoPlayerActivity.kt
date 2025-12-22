package com.app.myapplication

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
import com.app.myapplication.models.Video
import com.app.myapplication.screens.VideoPlayerScreen
import com.app.myapplication.ui.theme.MyApplicationTheme

class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 从Intent中获取视频数据
        val video = intent.getParcelableExtra<Video>("video")
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerScreen(
                        modifier = Modifier.padding(it),
                        video = video!!,
                        onBack = {
                            // 返回上一个Activity
                            finish()
                        }
                    )
                }
            }
        }
    }
}
