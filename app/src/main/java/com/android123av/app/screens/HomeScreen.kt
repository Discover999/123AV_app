package com.android123av.app.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.android123av.app.components.*
import com.android123av.app.models.Video
import com.android123av.app.models.ViewMode
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml
import com.android123av.app.network.searchVideos
import com.android123av.app.network.SiteManager
import com.android123av.app.state.SearchHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class SortOption(val displayName: String, val param: String) {
    RELEASE_DATE("发布日期", "release_date"),
    RECENT_UPDATE("最近更新", "recent_update"),
    TRENDING("热门", "trending"),
    MOST_FAVOURITE("最受欢迎", "most_favourited"),
    MOST_VIEWED_TODAY("今天最多观看", "most_viewed_today"),
    MOST_VIEWED_WEEK("本周最多观看", "most_viewed_week"),
    MOST_VIEWED_MONTH("本月最多观看", "most_viewed_month"),
    MOST_VIEWED("最多观看", "most_viewed")
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
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchCurrentPage by remember { mutableStateOf(1) }
    var searchTotalPages by remember { mutableStateOf(1) }
    var searchTotalResults by remember { mutableStateOf(0) }
    var isEditingHistory by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }

    val searchHistory by remember { derivedStateOf { SearchHistoryManager.searchHistory } }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    fun getCategoryUrl(category: String): String {
        return when (category) {
            "新发布" -> SiteManager.buildZhUrl("dm9/new-release")
            "最近更新" -> SiteManager.buildZhUrl("dm9/recent-update")
            "正在观看" -> SiteManager.buildZhUrl("dm9/trending")
            "未审查" -> SiteManager.buildZhUrl("dm9/uncensored?sort=${selectedSortOption.param}")
            else -> SiteManager.buildZhUrl("dm9")
        }
    }

    fun getUncensoredSortUrl(sortOption: SortOption): String {
        return SiteManager.buildZhUrl("dm9/uncensored?sort=${sortOption.param}")
    }

    suspend fun performSearch(query: String, page: Int = 1) {
        if (query.isEmpty()) return

        try {
            withContext(Dispatchers.Main) {
                isSearching = true
                searchError = null
                hasSearched = true
            }

            val (results, paginationInfo) = searchVideos(query, page)

            withContext(Dispatchers.Main) {
                searchResults = results
                searchCurrentPage = paginationInfo.currentPage
                searchTotalPages = paginationInfo.totalPages
                searchTotalResults = paginationInfo.totalResults
                isSearching = false
                if (page == 1) {
                    SearchHistoryManager.addSearchHistory(query)
                }
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                searchError = "网络连接失败，请检查网络设置"
                isSearching = false
                searchResults = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                searchError = "搜索失败，请稍后重试"
                isSearching = false
                searchResults = emptyList()
            }
        }
    }

    fun performManualSearch(query: String) {
        if (query.isEmpty()) {
            searchResults = emptyList()
            searchError = null
            hasSearched = false
            return
        }

        coroutineScope.launch {
            performSearch(query)
        }
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
                            Row {
                                Text(
                                    text = "123",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "AV",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE85A83)
                                )
                            }
                            Text(
                                text = "精选视频",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                        )
                    }
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

            if (selectedCategory == "未审查") {
                SortOptionsBar(
                    selectedSortOption = selectedSortOption,
                    onSortOptionSelected = { sortOption ->
                        selectedSortOption = sortOption
                        currentPage = 1
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
                                        isCategoryChanging = isCategoryChanging,
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

    if (showSearchDialog) {
        SearchDialog(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { 
                performManualSearch(searchQuery)
            },
            onDismiss = { 
                showSearchDialog = false
                searchQuery = ""
                searchResults = emptyList()
                searchError = null
                hasSearched = false
                searchTotalResults = 0
                isEditingHistory = false
            },
            searchResults = searchResults,
            isSearching = isSearching,
            searchError = searchError,
            hasSearched = hasSearched,
            onVideoClick = onVideoClick,
            searchHistory = searchHistory,
            onHistoryClick = { query ->
                searchQuery = query
                performManualSearch(query)
            },
            focusRequester = focusRequester,
            isFocused = isFocused,
            onFocusChanged = { isFocused = it },
            onClear = {
                searchQuery = ""
                searchResults = emptyList()
                searchError = null
                hasSearched = false
                searchTotalResults = 0
            },
            currentPage = searchCurrentPage,
            totalPages = searchTotalPages,
            totalResults = searchTotalResults,
            onPageChange = { page ->
                coroutineScope.launch {
                    performSearch(searchQuery, page)
                }
            },
            isEditingHistory = isEditingHistory,
            onToggleEditMode = { isEditingHistory = !isEditingHistory },
            onDeleteHistory = { SearchHistoryManager.removeSearchHistory(it) },
            onClearAllHistory = { 
                SearchHistoryManager.clearSearchHistory()
                isEditingHistory = false
            },
            isHistoryExpanded = isHistoryExpanded,
            onToggleHistoryExpanded = { isHistoryExpanded = !isHistoryExpanded }
        )
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
    onLoadPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 80.dp
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
    onLoadPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 80.dp
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
private fun SortOptionsBar(
    selectedSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ) {}
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortOption.entries.forEach { sortOption ->
                    val isSelected = sortOption == selectedSortOption
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortOptionSelected(sortOption) },
                        label = {
                            Text(
                                text = sortOption.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (sortOption) {
                                    SortOption.RELEASE_DATE -> if (isSelected) Icons.Filled.DateRange else Icons.Outlined.DateRange
                                    SortOption.RECENT_UPDATE -> if (isSelected) Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp
                                    SortOption.TRENDING -> if (isSelected) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Outlined.TrendingUp
                                    SortOption.MOST_FAVOURITE -> if (isSelected) Icons.Filled.Star else Icons.Outlined.Star
                                    SortOption.MOST_VIEWED_TODAY -> if (isSelected) Icons.Filled.Visibility else Icons.Outlined.Visibility
                                    SortOption.MOST_VIEWED_WEEK -> if (isSelected) Icons.Filled.Visibility else Icons.Outlined.Visibility
                                    SortOption.MOST_VIEWED_MONTH -> if (isSelected) Icons.Filled.Visibility else Icons.Outlined.Visibility
                                    SortOption.MOST_VIEWED -> if (isSelected) Icons.Filled.Visibility else Icons.Outlined.Visibility
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    searchResults: List<Video>,
    isSearching: Boolean,
    searchError: String?,
    hasSearched: Boolean,
    onVideoClick: (Video) -> Unit,
    searchHistory: List<String>,
    onHistoryClick: (String) -> Unit,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    currentPage: Int,
    totalPages: Int,
    totalResults: Int,
    onPageChange: (Int) -> Unit,
    isEditingHistory: Boolean,
    onToggleEditMode: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    isHistoryExpanded: Boolean,
    onToggleHistoryExpanded: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 850.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "搜索视频",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                "输入关键词搜索",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { onFocusChanged(it.isFocused) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = onClear) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "清除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearch()
                            }
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (hasSearched && query.isNotEmpty()) {
                    Text(
                        text = if (totalResults > 0) {
                            "找到 $totalResults 个结果"
                        } else {
                            "未找到搜索结果"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (searchHistory.isNotEmpty() && !hasSearched && query.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "搜索历史",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditingHistory) {
                                TextButton(
                                    onClick = onClearAllHistory,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "全部删除",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            if (searchHistory.size > 6) {
                                IconButton(
                                    onClick = onToggleHistoryExpanded,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHistoryExpanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = if (isHistoryExpanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = onToggleEditMode,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditingHistory) {
                                        Icons.Default.Done
                                    } else {
                                        Icons.Default.Delete
                                    },
                                    contentDescription = if (isEditingHistory) "完成" else "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayHistory = if (isHistoryExpanded || searchHistory.size <= 6) {
                        searchHistory
                    } else {
                        searchHistory.take(6)
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 160.dp)
                    ) {
                        items(displayHistory.chunked(3)) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { historyItem ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .heightIn(min = 24.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = historyItem,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable(
                                                        enabled = !isEditingHistory,
                                                        onClick = { onHistoryClick(historyItem) }
                                                    )
                                            )
                                            
                                            if (isEditingHistory) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clickable { onDeleteHistory(historyItem) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isSearching -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        searchError != null -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = searchError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        searchResults.isNotEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(searchResults, key = { it.id }) { video ->
                                        VideoItem(
                                            video = video,
                                            onClick = { onVideoClick(video) }
                                        )
                                    }
                                }

                                if (totalPages > 1) {
                                    SearchPagination(
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        onPageChange = onPageChange,
                                        isLoading = isSearching
                                    )
                                }
                            }
                        }
                        hasSearched && query.isNotEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "未找到搜索结果",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
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
private fun SearchPagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
            enabled = currentPage > 1 && !isLoading
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一页"
            )
        }

        Text(
            text = "$currentPage / $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        IconButton(
            onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
            enabled = currentPage < totalPages && !isLoading
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一页"
            )
        }
    }
}
