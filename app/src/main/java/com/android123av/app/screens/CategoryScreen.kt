package com.android123av.app.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.android123av.app.components.*
import com.android123av.app.models.Video
import com.android123av.app.models.Actress
import com.android123av.app.models.ActressDetail
import com.android123av.app.models.ViewMode
import com.android123av.app.models.SortOption
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml
import com.android123av.app.network.fetchActresses
import com.android123av.app.network.SiteManager
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    onVideoClick: (Video) -> Unit,
    onActressClick: (String) -> Unit = {}
) {
    val isActressesListPage = categoryHref.contains("actresses?")
    val isActressDetailPage = categoryHref.contains("actresses/") && !categoryHref.contains("actresses?")
    
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var actresses by remember { mutableStateOf<List<Actress>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(true) }
    var hasPrevPage by remember { mutableStateOf(false) }
    var totalPages by remember { mutableIntStateOf(1) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var error by remember { mutableStateOf<String?>(null) }
    var categoryInfo by remember { mutableStateOf(Pair("", "")) }
    var actressDetail by remember { mutableStateOf<ActressDetail?>(null) }
    var sortOptions by remember { mutableStateOf<List<SortOption>>(emptyList()) }
    var currentSort by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    fun extractSortFromUrl(url: String): String {
        return url.substringAfter("?sort=", "").substringBefore("&")
    }
    
    fun loadVideos(sortParam: String = "", isRefresh: Boolean = false) {
        coroutineScope.launch {
            if (isRefresh) {
                isRefreshing = true
            } else {
                isLoading = true
            }
            error = null
            try {
                val baseUrl = categoryHref.substringBefore("?")
                val url = if (sortParam.isNotEmpty()) {
                    val separator = if (baseUrl.contains("?")) "&" else "?"
                    SiteManager.buildZhUrl("$baseUrl${separator}sort=$sortParam")
                } else {
                    SiteManager.buildZhUrl(categoryHref)
                }
                
                val paginationInfo = if (isActressesListPage) {
                    val (newActresses, info) = fetchActresses(url, currentPage)
                    actresses = newActresses
                    videos = emptyList()
                    info
                } else {
                    val (newVideos, info) = parseVideosFromHtml(
                        fetchVideosDataWithResponse(url, currentPage).second
                    )
                    videos = newVideos
                    actresses = emptyList()
                    info
                }
                
                hasNextPage = paginationInfo.hasNextPage
                hasPrevPage = paginationInfo.hasPrevPage
                totalPages = paginationInfo.totalPages
                categoryInfo = Pair(paginationInfo.categoryTitle, paginationInfo.videoCount)
                actressDetail = paginationInfo.actressDetail
                
                val actualSortParam = if (sortParam.isNotEmpty()) sortParam else extractSortFromUrl(categoryHref)
                currentSort = paginationInfo.currentSort
                sortOptions = paginationInfo.sortOptions.map { option ->
                    option.copy(isSelected = option.value == actualSortParam)
                }
            } catch (e: IOException) {
                error = "网络连接失败，请检查网络设置"
                videos = emptyList()
                actresses = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                error = "加载失败，请稍后重试"
                videos = emptyList()
                actresses = emptyList()
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }
    
    fun loadVideosWithSort(sortValue: String) {
        currentPage = 1
        loadVideos(sortValue)
    }
    
    LaunchedEffect(currentPage, categoryHref) {
        val sortToUse = if (currentSort.isNotEmpty()) currentSort else extractSortFromUrl(categoryHref)
        loadVideos(sortToUse)
    }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (categoryInfo.first.isNotEmpty()) categoryInfo.first else categoryTitle,
                                fontWeight = FontWeight.Bold
                            )
                            if (categoryInfo.second.isNotEmpty()) {
                                Text(
                                    text = categoryInfo.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                actions = {
                    if (sortOptions.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "排序",
                                    tint = if (currentSort.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                properties = PopupProperties(focusable = true)
                            ) {
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (option.isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(
                                                    text = option.title,
                                                    fontWeight = if (option.isSelected) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Normal
                                                    },
                                                    color = if (option.isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (!option.isSelected) {
                                                loadVideosWithSort(option.value)
                                            }
                                            showSortMenu = false
                                        },
                                        colors = if (option.isSelected) {
                                            MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.primary,
                                                leadingIconColor = MaterialTheme.colorScheme.primary,
                                                trailingIconColor = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            MenuDefaults.itemColors()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (!isActressesListPage) {
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            
            if (isActressDetailPage) {
                actressDetail?.let { detail ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (detail.avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = detail.avatarUrl,
                                    contentDescription = detail.name,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = detail.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (detail.birthday.isNotEmpty()) {
                                    Text(
                                        text = detail.birthday,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                if (detail.height.isNotEmpty() || detail.measurements.isNotEmpty()) {
                                    Text(
                                        text = "${detail.height} - ${detail.measurements}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (detail.videoCount > 0) {
                                    Text(
                                        text = "${detail.videoCount} 视频",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                    end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
                )
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { loadVideos(isRefresh = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                Crossfade(
                    targetState = when {
                        isLoading && videos.isEmpty() && actresses.isEmpty() -> CategoryContentState.LOADING
                        error != null && videos.isEmpty() && actresses.isEmpty() -> CategoryContentState.ERROR
                        videos.isEmpty() && actresses.isEmpty() -> CategoryContentState.EMPTY
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
                            if (isActressesListPage) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            top = 16.dp,
                                            end = 16.dp,
                                            bottom = 16.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(actresses) { actress ->
                                            ActressCard(
                                                actress = actress,
                                                onClick = { onActressClick("actresses/${actress.id}") }
                                            )
                                        }
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            PaginationComponent(
                                                currentPage = currentPage,
                                                totalPages = totalPages,
                                                hasNextPage = hasNextPage,
                                                hasPrevPage = hasPrevPage,
                                                isLoading = isLoading,
                                                onLoadNext = { if (hasNextPage) currentPage++ },
                                                onLoadPrevious = { if (hasPrevPage) currentPage-- },
                                                onPageSelected = { page -> currentPage = page }
                                            )
                                        }
                                    }
                                }
                            } else {
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
                                            onPageSelected = { page -> currentPage = page },
                                            bottomPadding = 16.dp
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
                                            onPageSelected = { page -> currentPage = page },
                                            bottomPadding = 16.dp
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
}