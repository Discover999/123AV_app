package com.android123av.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import coil.ImageLoader
import com.android123av.app.components.AppNavigationBar
import com.android123av.app.screens.*
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.state.UserStateManager
import com.android123av.app.state.rememberUserState
import com.android123av.app.network.initializeNetworkService
import com.android123av.app.state.SearchHistoryManager
import com.android123av.app.state.rememberAppState
import com.android123av.app.ui.theme.MyApplicationTheme
import java.io.File

private fun updateStatusBarColor(activity: ComponentActivity) {
    val isLightTheme = !ThemeStateManager.isDarkTheme()
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = isLightTheme
    }
}

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.3)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(200 * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
        
        coil.Coil.setImageLoader(imageLoader)
        
        initializeNetworkService(this)
        UserStateManager.initialize(this)
        ThemeStateManager.initialize(this)
        SearchHistoryManager.initialize(this)

        enableEdgeToEdge()
        updateStatusBarColor(this)
        
        setContent {
            val currentTheme by ThemeStateManager.currentTheme.collectAsState()
            
            LaunchedEffect(currentTheme) {
                updateStatusBarColor(this@MainActivity)
            }
            
            MyApplicationTheme {
                MyApplicationApp()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun MyApplicationApp() {
    val appState = rememberAppState()
    val userState = rememberUserState()

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppNavigationBar(
                currentDestination = appState.currentDestination.value,
                onNavigateTo = { appState.navigateTo(it) }
            )
        }
    ) {
        when (appState.currentDestination.value) {
            AppDestinations.HOME -> HomeScreen(
                onVideoClick = { video ->
                    val intent = Intent(context, VideoPlayerActivity::class.java)
                    intent.putExtra("video", video)
                    context.startActivity(intent)
                }
            )
            AppDestinations.FAVORITES -> FavoritesScreen(
                modifier = Modifier.padding(it),
                onVideoClick = { video ->
                    val intent = Intent(context, VideoPlayerActivity::class.java)
                    intent.putExtra("video", video)
                    context.startActivity(intent)
                }
            )
            AppDestinations.SEARCH -> SearchScreen(
                modifier = Modifier.padding(it),
                onVideoClick = { video ->
                    val intent = Intent(context, VideoPlayerActivity::class.java)
                    intent.putExtra("video", video)
                    context.startActivity(intent)
                }
            )
            AppDestinations.PROFILE -> ProfileScreen(
                context = context,
                isLoggedIn = userState.isLoggedIn,
                onLogout = {
                    userState.performLogout()
                },
                onNavigateToLogin = {
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                },
                onNavigateToNetworkTest = {
                    val intent = Intent(context, NetworkTestActivity::class.java)
                    context.startActivity(intent)
                },
                onNavigateToSettings = {
                    val intent = Intent(context, SettingsActivity::class.java)
                    context.startActivity(intent)
                },
                onNavigateToHelp = {
                    val intent = Intent(context, HelpActivity::class.java)
                    context.startActivity(intent)
                },
                onNavigateToDownloads = {
                    val intent = Intent(context, DownloadsActivity::class.java)
                    context.startActivity(intent)
                }
            )
            else -> {
                appState.navigateTo(AppDestinations.HOME)
            }
        }
    }
}
