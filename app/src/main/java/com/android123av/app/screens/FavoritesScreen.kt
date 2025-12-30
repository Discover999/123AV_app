package com.android123av.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.android123av.app.components.VideoItem
import com.android123av.app.components.PaginationComponent
import com.android123av.app.models.Video
import com.android123av.app.state.UserStateManager
import com.android123av.app.network.fetchUserFavorites
import kotlinx.coroutines.launch

// 收藏夹屏幕
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (Video) -> Unit
) {
    var favoriteVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var hasPrevPage by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // 获取用户登录状态
    val isLoggedIn = UserStateManager.isLoggedIn
    val userName = UserStateManager.userName

    // 加载收藏视频的函数
    fun loadFavorites(page: Int = 1) {
        if (!isLoggedIn) {
            println("DEBUG: loadFavorites skipped - not logged in")
            return
        }
        
        isLoading = true
        println("DEBUG: loadFavorites started - page=$page, isLoading=$isLoading")
        coroutineScope.launch {
            try {
                val (videos, pagination) = fetchUserFavorites(page)
                println("DEBUG: fetchUserFavorites returned - videos.size=${videos.size}, pagination=$pagination")
                favoriteVideos = videos
                if (pagination.totalPages > 0) {
                    totalPages = pagination.totalPages
                }
                hasNextPage = pagination.hasNextPage
                hasPrevPage = pagination.hasPrevPage
                println("DEBUG: FavoritesScreen - Loaded ${videos.size} favorite videos, currentPage=$page, hasNextPage=$hasNextPage, hasPrevPage=$hasPrevPage")
            } catch (e: Exception) {
                println("DEBUG: FavoritesScreen - Error loading favorites: ${e.message}")
                favoriteVideos = emptyList()
            } finally {
                isLoading = false
                println("DEBUG: loadFavorites completed - isLoading=$isLoading")
            }
        }
    }

    // 当用户登录状态或页码改变时，重新加载收藏
    LaunchedEffect(isLoggedIn, currentPage) {
        println("DEBUG: LaunchedEffect triggered - isLoggedIn=$isLoggedIn, currentPage=$currentPage")
        if (isLoggedIn) {
            loadFavorites(currentPage)
        } else {
            favoriteVideos = emptyList()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(it)) {
            
            when {
                isLoading && favoriteVideos.isEmpty() -> {
                    // 首次加载中状态 - 显示加载动画
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                !isLoggedIn -> {
                    // 未登录状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "收藏提示",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "暂无收藏视频",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "请先登录以查看您的收藏视频",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                favoriteVideos.isEmpty() -> {
                    // 已登录但没有收藏视频
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "暂无收藏视频",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "您还没有收藏任何视频",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(favoriteVideos, key = { it.id }) { video ->
                            VideoItem(video = video, onClick = { onVideoClick(video) })
                        }

                        item {
                            PaginationComponent(
                                currentPage = currentPage,
                                totalPages = totalPages,
                                hasNextPage = hasNextPage,
                                hasPrevPage = hasPrevPage,
                                isLoading = isLoading,
                                onLoadNext = { if (hasNextPage) currentPage++ },
                                onLoadPrevious = { if (hasPrevPage) currentPage-- }
                            )
                        }
                    }
                }
            }
        }
    }
}
