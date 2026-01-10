package com.android123av.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

// 导航目的地
enum class AppDestinations(
    val label: String,
    val icon: ImageVector
) {
    HOME("首页", Icons.Default.Home),
    SEARCH("其他", Icons.Default.Menu),
    FAVORITES("收藏", Icons.Default.Favorite),
    PROFILE("我的", Icons.Default.AccountBox),
    VIDEO_PLAYER("视频播放", Icons.Default.PlayArrow),
    LOGIN("登录", Icons.Default.AccountBox),
    DOWNLOADS("下载管理", Icons.Default.Favorite)
}


