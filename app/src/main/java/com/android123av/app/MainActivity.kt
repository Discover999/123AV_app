package com.android123av.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import coil.ImageLoader
import com.android123av.app.components.AppNavigationBar
import com.android123av.app.screens.*
import com.android123av.app.state.rememberAppState
import com.android123av.app.state.rememberUserState
import com.android123av.app.state.UserStateManager
import com.android123av.app.network.initializeNetworkService
import com.android123av.app.ui.theme.MyApplicationTheme
import java.io.File


// 主应用入口
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.3) // 增加内存缓存到30%
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(200 * 1024 * 1024) // 增加磁盘缓存到200MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(true) // 启用硬件加速
            .build()
        
        coil.Coil.setImageLoader(imageLoader)
        
        initializeNetworkService(this)
        
        UserStateManager.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp(loginActivityLauncher = loginActivityLauncher)
            }
        }
    }
    
    // LoginActivity结果处理器
    private val loginActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 处理登录结果，用户状态已经通过UserStateManager共享，这里只需要通知刷新UI
        if (result.resultCode == RESULT_OK) {
            // 登录成功，UI会自动刷新因为使用了共享的UserStateManager
        }
    }
}

// 主应用布局
@PreviewScreenSizes
@Composable
fun MyApplicationApp(loginActivityLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
    println("DEBUG: MyApplicationApp composable starting...")
    val appState = rememberAppState()
    val userState = rememberUserState()
    val coroutineScope = rememberCoroutineScope()
    
    println("DEBUG: MyApplicationApp - userState.isLoggedIn: ${userState.isLoggedIn}, userState.userName: ${userState.userName}")
    
    // 获取当前Activity引用
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
                    },
                    modifier = Modifier.padding(it)
                )
                AppDestinations.FAVORITES -> FavoritesScreen(
                    modifier = Modifier.padding(it),
                    onVideoClick = { video ->
                        // 启动VideoPlayerActivity
                        val intent = Intent(context, VideoPlayerActivity::class.java)
                        intent.putExtra("video", video)
                        context.startActivity(intent)
                    }
                )
                AppDestinations.SEARCH -> SearchScreen(
                    modifier = Modifier.padding(it),
                    onVideoClick = { video ->
                        // 启动VideoPlayerActivity
                        val intent = Intent(context, VideoPlayerActivity::class.java)
                        intent.putExtra("video", video)
                        context.startActivity(intent)
                    }
                )
                AppDestinations.PROFILE -> ProfileScreen(
                    modifier = Modifier.padding(it),
                    isLoggedIn = userState.isLoggedIn,
                    user = userState.getCurrentUser(),
                    onLogout = {
                        userState.performLogout()
                    },
                    onNavigateToLogin = {
                        // 启动LoginActivity
                        val intent = Intent(context, LoginActivity::class.java)
                        loginActivityLauncher.launch(intent)
                    }
                )
                // 移除VideoPlayer导航，因为现在使用独立的Activity
                else -> {
                    // 处理其他情况，比如VIDEO_PLAYER
                    // 如果意外进入此分支，默认导航到首页
                    appState.navigateTo(AppDestinations.HOME)
                }
            }
        }
    }

















