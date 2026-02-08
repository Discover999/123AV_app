package com.android123av.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.android123av.app.screens.WatchHistoryScreen
import com.android123av.app.ui.theme.MyApplicationTheme

class WatchHistoryActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                WatchHistoryScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
