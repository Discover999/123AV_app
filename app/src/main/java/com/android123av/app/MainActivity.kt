package com.android123av.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.android123av.app.components.AppNavigationBar
import com.android123av.app.screens.*
import com.android123av.app.state.rememberAppState
import com.android123av.app.state.rememberUserState

// 协程相关import
import com.android123av.app.ui.theme.MyApplicationTheme


// 主应用入口
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp()
            }
        }
}

// 主应用布局
@PreviewScreenSizes
@Composable
fun MyApplicationApp() {
    val appState = rememberAppState()
    val userState = rememberUserState()
    val coroutineScope = rememberCoroutineScope()
    
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
                    modifier = Modifier.padding(it),
                    onVideoClick = { video ->
                        // 启动VideoPlayerActivity
                        val intent = Intent(context, VideoPlayerActivity::class.java)
                        intent.putExtra("video", video)
                        context.startActivity(intent)
                    }
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
                        appState.navigateToLogin()
                    }
                )
                AppDestinations.LOGIN -> LoginScreen(
                    modifier = Modifier.padding(it),
                    isLoggingIn = userState.isLoggingIn,
                    loginError = userState.loginError,
                    onLogin = { username, password ->
                        userState.performLogin(username, password, coroutineScope)
                        appState.navigateBackFromLogin()
                    },
                    onBack = {
                        appState.navigateBackFromLogin()
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
}

















