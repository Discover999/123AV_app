package com.android123av.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import com.android123av.app.screens.CategoryScreen
import com.android123av.app.models.Video
import com.android123av.app.ui.theme.MyApplicationTheme

class CategoryActivity : ComponentActivity() {
    private var categoryTitle: String = ""
    private var categoryHref: String = ""

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        categoryTitle = intent.getStringExtra("categoryTitle") ?: ""
        categoryHref = intent.getStringExtra("categoryHref") ?: ""

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                CategoryScreen(
                    categoryTitle = categoryTitle,
                    categoryHref = categoryHref,
                    onBack = { finish() },
                    onVideoClick = { video ->
                        val intent = Intent(this, VideoPlayerActivity::class.java)
                        intent.putExtra("video", video)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
