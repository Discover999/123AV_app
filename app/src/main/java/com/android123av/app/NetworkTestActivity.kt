package com.android123av.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.android123av.app.screens.NetworkTestScreen
import com.android123av.app.ui.theme.MyApplicationTheme

class NetworkTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NetworkTestScreen(
                    onNavigateBack = {
                        finish()
                    }
                )
            }
        }
    }
}
