package com.android123av.app.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android123av.app.components.*
import com.android123av.app.models.Video
import com.android123av.app.models.ViewMode
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml
import com.android123av.app.network.SiteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private enum class CategoryContentState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT
}

@Composable
private fun CategoryLoadingSkeleton() {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryTitle: String,
    categoryHref: String,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(true) }
    var hasPrevPage by remember { mutableStateOf(false) }
    var totalPages by remember { mutableIntStateOf(1) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    fun loadVideos() {
        coroutineScope.launch {
            isLoading = true
            error = null
            try {
                val url = SiteManager.buildZhUrl(categoryHref)
                val (newVideos, paginationInfo) = parseVideosFromHtml(
                    fetchVideosDataWithResponse(url, currentPage).second
                )
                videos = newVideos
                hasNextPage = paginationInfo.hasNextPage
                hasPrevPage = paginationInfo.hasPrevPage
                totalPages = paginationInfo.totalPages
            } catch (e: IOException) {
                error = "网络连接失败，请检查网络设置"
                videos = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                error = "加载失败，请稍后重试"
                videos = emptyList()
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(currentPage, categoryHref) {
        loadVideos()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        categoryTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                            contentDescription = "切换视图"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = isLoading && videos.isNotEmpty(),
                onRefresh = { loadVideos() },
                modifier = Modifier.fillMaxSize()
            ) {
                Crossfade(
                    targetState = when {
                        isLoading && videos.isEmpty() -> CategoryContentState.LOADING
                        error != null && videos.isEmpty() -> CategoryContentState.ERROR
                        videos.isEmpty() -> CategoryContentState.EMPTY
                        else -> CategoryContentState.CONTENT
                    },
                    animationSpec = tween(300),
                    label = "contentCrossfade"
                ) { state ->
                    when (state) {
                        CategoryContentState.LOADING -> {
                            CategoryLoadingSkeleton()
                        }
                        CategoryContentState.ERROR -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = error ?: "加载失败",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Button(onClick = { loadVideos() }) {
                                        Text("重试")
                                    }
                                }
                            }
                        }
                        CategoryContentState.EMPTY -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无数据")
                            }
                        }
                        CategoryContentState.CONTENT -> {
                            when (viewMode) {
                                ViewMode.LIST -> {
                                    VideoListContent(
                                        videos = videos,
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        hasNextPage = hasNextPage,
                                        hasPrevPage = hasPrevPage,
                                        isLoading = isLoading,
                                        isCategoryChanging = false,
                                        onVideoClick = onVideoClick,
                                        onLoadNext = { if (hasNextPage) currentPage++ },
                                        onLoadPrevious = { if (hasPrevPage) currentPage-- },
                                        onPageSelected = { page -> currentPage = page }
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
                                        isCategoryChanging = false,
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