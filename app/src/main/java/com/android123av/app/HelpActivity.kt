package com.android123av.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.android123av.app.screens.HelpScreen
import com.android123av.app.ui.theme.MyApplicationTheme

class HelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HelpScreen(
                    onNavigateBack = { finish() },
                    onNavigateToFeedback = { },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
