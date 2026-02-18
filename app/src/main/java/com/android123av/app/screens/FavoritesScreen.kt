package com.android123av.app.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android123av.app.components.PaginationComponent
import com.android123av.app.components.VideoCardGridItem
import com.android123av.app.components.VideoItem
import com.android123av.app.models.Video
import com.android123av.app.models.ViewMode
import com.android123av.app.network.fetchUserFavorites
import com.android123av.app.state.UserStateManager
import kotlinx.coroutines.launch

private enum class FavoritesContentState {
    LOADING, EMPTY, NOT_LOGGED_IN, CONTENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (Video) -> Unit
) {
    var favoriteVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var hasPrevPage by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val isLoggedIn = UserStateManager.isLoggedIn
    val userName = UserStateManager.userName

    fun loadFavorites(page: Int = 1) {
        if (!isLoggedIn) {
            return
        }

        isLoading = true
        coroutineScope.launch {
            try {
                val (videos, pagination) = fetchUserFavorites(page)
                favoriteVideos = videos
                if (pagination.totalPages > 0) {
                    totalPages = pagination.totalPages
                }
                hasNextPage = pagination.hasNextPage
                hasPrevPage = pagination.hasPrevPage
            } catch (e: Exception) {
                favoriteVideos = emptyList()
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(isLoggedIn, currentPage, refreshTrigger) {
        if (isLoggedIn) {
            loadFavorites(currentPage)
        } else {
            favoriteVideos = emptyList()
            isLoading = false
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏") },
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) {
                                Icons.Default.Menu
                            } else {
                                Icons.Default.Apps
                            },
                            contentDescription = "切换视图",
                        )
                    }
                },
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

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    refreshTrigger++
                    isRefreshing = true
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Crossfade(
                    targetState = when {
                        !isLoggedIn -> FavoritesContentState.NOT_LOGGED_IN
                        isLoading && favoriteVideos.isEmpty() -> FavoritesContentState.LOADING
                        favoriteVideos.isEmpty() -> FavoritesContentState.EMPTY
                        else -> FavoritesContentState.CONTENT
                    },
                    animationSpec = tween(300),
                    label = "favoritesContentCrossfade"
                ) { state ->
                    when (state) {
                        FavoritesContentState.LOADING -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        FavoritesContentState.NOT_LOGGED_IN -> {
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

                        FavoritesContentState.EMPTY -> {
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

                        FavoritesContentState.CONTENT -> {
                            when (viewMode) {
                                ViewMode.LIST -> {
                                    FavoritesListContent(
                                        videos = favoriteVideos,
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        hasNextPage = hasNextPage,
                                        hasPrevPage = hasPrevPage,
                                        isLoading = isLoading,
                                        onVideoClick = onVideoClick,
                                        onLoadNext = { if (hasNextPage) currentPage++ },
                                        onLoadPrevious = { if (hasPrevPage) currentPage-- },
                                        onPageSelected = { page -> currentPage = page }
                                    )
                                }
                                ViewMode.GRID -> {
                                    FavoritesGridContent(
                                        videos = favoriteVideos,
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        hasNextPage = hasNextPage,
                                        hasPrevPage = hasPrevPage,
                                        isLoading = isLoading,
                                        onVideoClick = onVideoClick,
                                        onLoadNext = { if (hasNextPage) currentPage++ },
                                        onLoadPrevious = { if (hasPrevPage) currentPage-- },
                                        onPageSelected = { page -> currentPage = page }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesListContent(
    videos: List<Video>,
    currentPage: Int,
    totalPages: Int,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    isLoading: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                VideoItem(
                    video = video,
                    onClick = { onVideoClick(video) }
                )
            }
            item {
                PaginationComponent(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    hasNextPage = hasNextPage,
                    hasPrevPage = hasPrevPage,
                    isLoading = isLoading,
                    onLoadNext = onLoadNext,
                    onLoadPrevious = onLoadPrevious,
                    onPageSelected = onPageSelected
                )
            }
        }
        if (isLoading && videos.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }
    }
}

@Composable
private fun FavoritesGridContent(
    videos: List<Video>,
    currentPage: Int,
    totalPages: Int,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    isLoading: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCardGridItem(
                    video = video,
                    onClick = { onVideoClick(video) }
                )
            }
            item(span = { GridItemSpan(2) }) {
                PaginationComponent(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    hasNextPage = hasNextPage,
                    hasPrevPage = hasPrevPage,
                    isLoading = isLoading,
                    onLoadNext = onLoadNext,
                    onLoadPrevious = onLoadPrevious,
                    onPageSelected = onPageSelected
                )
            }
        }
        if (isLoading && videos.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }
    }
}
