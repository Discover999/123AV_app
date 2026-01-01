package com.android123av.app.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
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
import com.android123av.app.models.ViewMode
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml

enum class SortOption(val displayName: String, val param: String) {
    RELEASE_DATE("发布日期", "release_date"),
    RECENT_UPDATE("最近更新", "recent_update"),
    TRENDING("热门", "trending"),
    MOST_VIEWED_TODAY("今天最多观看", "most_viewed_today"),
    MOST_VIEWED_WEEK("本周最多观看", "most_viewed_week"),
    MOST_VIEWED_MONTH("本月最多观看", "most_viewed_month"),
    MOST_VIEWED("最多观看", "most_viewed"),
    MOST_FAVOURITE("最受欢迎", "most_favourited")
}

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
    var selectedSortOption by remember { mutableStateOf(SortOption.RELEASE_DATE) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    fun getCategoryUrl(category: String): String {
        return when (category) {
            "新发布" -> "https://123av.com/zh/dm9/new-release"
            "最近更新" -> "https://123av.com/zh/dm9/recent-update"
            "正在观看" -> "https://123av.com/zh/dm9/trending"
            "未审查" -> "https://123av.com/zh/dm9/uncensored?sort=${selectedSortOption.param}"
            else -> "https://123av.com/zh/dm9"
        }
    }

    fun getUncensoredSortUrl(sortOption: SortOption): String {
        return "https://123av.com/zh/dm9/uncensored?sort=${sortOption.param}"
    }

    LaunchedEffect(currentPage, selectedCategory, refreshTrigger, selectedSortOption) {
        if (selectedCategory != "未审查") {
            selectedSortOption = SortOption.RELEASE_DATE
        }
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
                                        isUncensored = selectedCategory == "未审查",
                                        selectedSortOption = selectedSortOption,
                                        isSortMenuExpanded = isSortMenuExpanded,
                                        onVideoClick = onVideoClick,
                                        onLoadNext = { if (hasNextPage) currentPage++ },
                                        onLoadPrevious = { if (hasPrevPage) currentPage-- },
                                        onPageSelected = { page -> currentPage = page },
                                        onSortOptionSelected = { 
                                            selectedSortOption = it
                                            currentPage = 1
                                        },
                                        onSortMenuExpandChange = { isSortMenuExpanded = it }
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
                                        isUncensored = selectedCategory == "未审查",
                                        selectedSortOption = selectedSortOption,
                                        isSortMenuExpanded = isSortMenuExpanded,
                                        onVideoClick = onVideoClick,
                                        onLoadNext = { if (hasNextPage) currentPage++ },
                                        onLoadPrevious = { if (hasPrevPage) currentPage-- },
                                        onPageSelected = { page -> currentPage = page },
                                        onSortOptionSelected = { 
                                            selectedSortOption = it
                                            currentPage = 1
                                        },
                                        onSortMenuExpandChange = { isSortMenuExpanded = it }
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
    isUncensored: Boolean,
    selectedSortOption: SortOption,
    isSortMenuExpanded: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onSortMenuExpandChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (isUncensored) 100.dp else 80.dp
            ),
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

        if (isUncensored) {
            SortButton(
                selectedSortOption = selectedSortOption,
                isExpanded = isSortMenuExpanded,
                onExpandChange = onSortMenuExpandChange,
                onSortOptionSelected = onSortOptionSelected,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
            )
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
    isUncensored: Boolean,
    selectedSortOption: SortOption,
    isSortMenuExpanded: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onSortMenuExpandChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (isUncensored) 100.dp else 80.dp
            ),
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

        if (isUncensored) {
            SortButton(
                selectedSortOption = selectedSortOption,
                isExpanded = isSortMenuExpanded,
                onExpandChange = onSortMenuExpandChange,
                onSortOptionSelected = onSortOptionSelected,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
            )
        }

        LoadingOverlay(isLoading = isCategoryChanging)
    }
}

@Composable
private fun EmptyStateContent() {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
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

private enum class ContentState {
    LOADING, EMPTY, CONTENT, CATEGORY_LOADING
}

@Composable
fun SortButton(
    selectedSortOption: SortOption,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.wrapContentSize(Alignment.BottomEnd)) {
        Surface(
            onClick = { onExpandChange(!isExpanded) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = selectedSortOption.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandChange(false) },
            modifier = Modifier.widthIn(min = 180.dp)
        ) {
            SortOption.entries.forEach { sortOption ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sortOption.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sortOption == selectedSortOption) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onSortOptionSelected(sortOption)
                        onExpandChange(false)
                    },
                    leadingIcon = if (sortOption == selectedSortOption) {
                        {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}
