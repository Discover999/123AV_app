package com.android123av.app.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android123av.app.components.*
import com.android123av.app.models.Video
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit
) {
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isCategoryChanging by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(true) }
    var hasPrevPage by remember { mutableStateOf(false) }
    var totalPages by remember { mutableStateOf(1) }
    var selectedCategory by remember { mutableStateOf("新发布") }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun getCategoryUrl(category: String): String {
        return when (category) {
            "新发布" -> "https://123av.com/zh/dm9/new-release"
            "最近更新" -> "https://123av.com/zh/dm9/recent-update"
            "正在观看" -> "https://123av.com/zh/dm9/trending"
            "未审查" -> "https://123av.com/zh/dm9/uncensored"
            else -> "https://123av.com/zh/dm9"
        }
    }

    LaunchedEffect(currentPage, selectedCategory, refreshTrigger) {
        isLoading = true
        isCategoryChanging = true
        try {
            val categoryUrl = getCategoryUrl(selectedCategory)
            val (newVideos, paginationInfo) = parseVideosFromHtml(
                fetchVideosDataWithResponse(categoryUrl, currentPage).second
            )
            videos = newVideos
            hasNextPage = paginationInfo.hasNextPage
            hasPrevPage = paginationInfo.hasPrevPage
            totalPages = paginationInfo.totalPages
        } catch (e: Exception) {
            e.printStackTrace()
            videos = emptyList()
        } finally {
            isLoading = false
            isRefreshing = false
            isCategoryChanging = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFE85A83),
                                            Color(0xFFE85A83).copy(alpha = 0.7f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "123AV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "精选视频",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            CategoryTabs(
                modifier = Modifier.fillMaxWidth(),
                onCategoryChange = { category ->
                    selectedCategory = category
                    currentPage = 1
                }
            )

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
                        isCategoryChanging -> ContentState.CATEGORY_LOADING
                        isLoading && videos.isEmpty() -> ContentState.LOADING
                        videos.isEmpty() -> ContentState.EMPTY
                        else -> ContentState.CONTENT
                    },
                    animationSpec = tween(300),
                    label = "contentCrossfade"
                ) { state ->
                    when (state) {
                        ContentState.CATEGORY_LOADING -> {
                            CategoryChangeLoading()
                        }
                        ContentState.LOADING -> {
                            LoadingSkeleton()
                        }
                        ContentState.EMPTY -> {
                            EmptyStateContent()
                        }
                        ContentState.CONTENT -> {
                            when (viewMode) {
                                ViewMode.LIST -> {
                                    VideoListContent(
                                        videos = videos,
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        hasNextPage = hasNextPage,
                                        hasPrevPage = hasPrevPage,
                                        isLoading = isLoading,
                                        isCategoryChanging = isCategoryChanging,
                                        onVideoClick = onVideoClick,
                                        onLoadNext = { if (hasNextPage) currentPage++ },
                                        onLoadPrevious = { if (hasPrevPage) currentPage-- }
                                    )
                                }
                                ViewMode.GRID -> {
                                    VideoGridContent(
                                        videos = videos,
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        hasNextPage = hasNextPage,
                                        hasPrevPage = hasPrevPage,
                                        isLoading = isLoading,
                                        isCategoryChanging = isCategoryChanging,
                                        onVideoClick = onVideoClick,
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
    }
}

@Composable
private fun LoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
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

@Composable
private fun VideoListContent(
    videos: List<Video>,
    currentPage: Int,
    totalPages: Int,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    isLoading: Boolean,
    isCategoryChanging: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit
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
                    onLoadPrevious = onLoadPrevious
                )
            }
        }

        LoadingOverlay(isLoading = isCategoryChanging)
    }
}

@Composable
private fun VideoGridContent(
    videos: List<Video>,
    currentPage: Int,
    totalPages: Int,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    isLoading: Boolean,
    isCategoryChanging: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit
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
                    onLoadPrevious = onLoadPrevious
                )
            }
        }

        LoadingOverlay(isLoading = isCategoryChanging)
    }
}

@Composable
private fun EmptyStateContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "暂无视频数据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下拉刷新重新加载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(5) {
            item {
                ShimmerVideoCard()
            }
        }
    }
}

@Composable
private fun CategoryChangeLoading() {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingAnimation")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            strokeWidth = 4.dp
        )
    }
}

enum class ViewMode {
    LIST, GRID
}

private enum class ContentState {
    LOADING, EMPTY, CONTENT, CATEGORY_LOADING
}
