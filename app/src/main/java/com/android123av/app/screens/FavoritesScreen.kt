package com.android123av.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.android123av.app.components.VideoItem
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
        if (!isLoggedIn) return
        
        isLoading = true
        coroutineScope.launch {
            try {
                val (videos, pagination) = fetchUserFavorites(page)
                favoriteVideos = videos
                currentPage = pagination.currentPage
                totalPages = pagination.totalPages
                hasNextPage = pagination.hasNextPage
                hasPrevPage = pagination.hasPrevPage
                println("DEBUG: FavoritesScreen - Loaded ${videos.size} favorite videos for user: $userName, page: $page/$totalPages")
            } catch (e: Exception) {
                println("DEBUG: FavoritesScreen - Error loading favorites: ${e.message}")
                favoriteVideos = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    // 当用户登录状态改变时，重新加载收藏
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            // 用户已登录，获取收藏视频
            loadFavorites(1)
        } else {
            // 用户未登录，清空收藏列表
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
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            
            when {
                isLoading -> {
                    // 加载中状态
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
                    // 显示收藏视频列表
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(favoriteVideos, key = { it.id }) { video ->
                                VideoItem(video = video, onClick = { onVideoClick(video) })
                            }
                        }
                        
                        // 分页控制
                        if (totalPages > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 上一页按钮
                                TextButton(
                                    onClick = { if (hasPrevPage) loadFavorites(currentPage - 1) },
                                    enabled = hasPrevPage
                                ) {
                                    Text("上一页")
                                }
                                
                                // 页码信息
                                Text(
                                    text = "$currentPage / $totalPages",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                // 下一页按钮
                                TextButton(
                                    onClick = { if (hasNextPage) loadFavorites(currentPage + 1) },
                                    enabled = hasNextPage
                                ) {
                                    Text("下一页")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}