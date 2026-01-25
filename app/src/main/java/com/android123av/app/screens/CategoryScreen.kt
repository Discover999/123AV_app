package com.android123av.app.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.android123av.app.components.*
import com.android123av.app.models.Video
import com.android123av.app.models.Actress
import com.android123av.app.models.Series
import com.android123av.app.models.Genre
import com.android123av.app.models.Studio
import com.android123av.app.models.ActressDetail
import com.android123av.app.models.ViewMode
import com.android123av.app.models.SortOption
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml
import com.android123av.app.network.fetchActresses
import com.android123av.app.network.fetchSeries
import com.android123av.app.network.fetchGenres
import com.android123av.app.network.fetchStudios
import com.android123av.app.network.SiteManager
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.android123av.app.models.PaginationInfo
import kotlinx.coroutines.launch
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
    onActressClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {}
) {
    val isActressesListPage = categoryHref.contains("actresses?") ||
            (categoryTitle.contains(
                "女演员",
                ignoreCase = true
            ) && categoryHref.contains("actresses"))
    val isActressDetailPage =
        categoryHref.contains("actresses/") && !categoryHref.contains("actresses?")
    val isSeriesListPage = categoryHref.matches(Regex(".*/series\\??$")) ||
            categoryHref.matches(Regex(".*/series\\?.*")) ||
            categoryHref.matches(Regex("^series\\??$")) ||
            categoryHref.matches(Regex("^series\\?.*"))
    val isGenresListPage = categoryHref.matches(Regex(".*/genres\\??$")) ||
            categoryHref.matches(Regex(".*/genres\\?.*")) ||
            categoryHref.matches(Regex("^genres\\??$")) ||
            categoryHref.matches(Regex("^genres\\?.*"))
    val isStudiosListPage = categoryHref.matches(Regex(".*/makers\\??$")) ||
            categoryHref.matches(Regex(".*/makers\\?.*")) ||
            categoryHref.matches(Regex("^makers\\??$")) ||
            categoryHref.matches(Regex("^makers\\?.*")) ||
            categoryHref.matches(Regex(".*/studios\\??$")) ||
            categoryHref.matches(Regex(".*/studios\\?.*")) ||
            categoryHref.matches(Regex("^studios\\??$")) ||
            categoryHref.matches(Regex("^studios\\?.*"))
    val isStudioDetailPage = (categoryHref.matches(Regex(".*/makers/[^?]+$")) ||
            categoryHref.matches(Regex(".*/studios/[^?]+$"))) &&
            !categoryHref.matches(Regex(".*/makers\\??$")) &&
            !categoryHref.matches(Regex(".*/studios\\??$"))

    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var actresses by remember { mutableStateOf<List<Actress>>(emptyList()) }
    var series by remember { mutableStateOf<List<Series>>(emptyList()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var studios by remember { mutableStateOf<List<Studio>>(emptyList()) }
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

    val seriesListState = rememberLazyGridState()
    val genericListState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    var isTopBarExpanded by remember { mutableStateOf(true) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val topBarHeight by animateDpAsState(
        targetValue = if (isActressDetailPage && actressDetail != null) {
            if (isTopBarExpanded) 160.dp else 64.dp
        } else {
            64.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "topBarHeight"
    )

    val avatarSize by animateDpAsState(
        targetValue = if (isTopBarExpanded) 64.dp else 40.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "avatarSize"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0 && isTopBarExpanded) {
                    isTopBarExpanded = false
                } else if (delta > 0 && !isTopBarExpanded) {
                    isTopBarExpanded = true
                }
                return Offset.Zero
            }
        }
    }

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

                val paginationInfo = if (isSeriesListPage) {
                    val (newSeries, info) = fetchSeries(url, currentPage)
                    series = newSeries
                    videos = emptyList()
                    actresses = emptyList()
                    genres = emptyList()
                    studios = emptyList()
                    info
                } else if (isGenresListPage) {
                    val (newGenres, info) = fetchGenres(url, currentPage)
                    genres = newGenres
                    videos = emptyList()
                    actresses = emptyList()
                    series = emptyList()
                    studios = emptyList()
                    info
                } else if (isStudiosListPage) {
                    val (newStudios, info) = fetchStudios(url, currentPage)
                    studios = newStudios
                    videos = emptyList()
                    actresses = emptyList()
                    series = emptyList()
                    genres = emptyList()
                    info
                } else if (isStudioDetailPage) {
                    val studioId = if (categoryHref.contains("/makers/")) {
                        categoryHref.substringAfterLast("/makers/").substringBefore("?")
                    } else {
                        categoryHref.substringAfterLast("/studios/").substringBefore("?")
                    }
                    val studioName = studioId.replace("-", " ")
                    val (newVideos, info) = parseVideosFromHtml(
                        fetchVideosDataWithResponse(url, currentPage).second
                    )
                    videos = newVideos
                    actresses = emptyList()
                    series = emptyList()
                    genres = emptyList()
                    studios = emptyList()
                    val studioInfo = PaginationInfo(
                        currentPage = info.currentPage,
                        totalPages = info.totalPages,
                        hasNextPage = info.hasNextPage,
                        hasPrevPage = info.hasPrevPage,
                        categoryTitle = studioName,
                        videoCount = info.videoCount,
                        currentSort = info.currentSort,
                        sortOptions = info.sortOptions
                    )
                    studioInfo
                } else if (isActressesListPage) {
                    val (newActresses, info) = fetchActresses(url, currentPage)
                    actresses = newActresses
                    videos = emptyList()
                    series = emptyList()
                    genres = emptyList()
                    studios = emptyList()
                    info
                } else {
                    val (newVideos, info) = parseVideosFromHtml(
                        fetchVideosDataWithResponse(url, currentPage).second
                    )
                    videos = newVideos
                    actresses = emptyList()
                    series = emptyList()
                    genres = emptyList()
                    studios = emptyList()
                    info
                }

                hasNextPage = paginationInfo.hasNextPage
                hasPrevPage = paginationInfo.hasPrevPage
                totalPages = paginationInfo.totalPages
                categoryInfo = Pair(paginationInfo.categoryTitle, paginationInfo.videoCount)
                actressDetail = paginationInfo.actressDetail

                val actualSortParam = sortParam.ifEmpty { extractSortFromUrl(categoryHref) }
                currentSort = paginationInfo.currentSort
                val mappedOptions = paginationInfo.sortOptions.map { option ->
                    option.copy(isSelected = option.value == actualSortParam)
                }
                sortOptions = if (mappedOptions.any { it.isSelected }) {
                    mappedOptions
                } else if (mappedOptions.isNotEmpty()) {
                    mappedOptions.mapIndexed { index, option ->
                        if (index == 0) option.copy(isSelected = true) else option
                    }
                } else {
                    mappedOptions
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
        val sortToUse = currentSort.ifEmpty { extractSortFromUrl(categoryHref) }
        loadVideos(sortToUse)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topBarHeight)
                            .padding(horizontal = 4.dp)
                            .then(
                                if (isGenresListPage || isStudiosListPage || isSeriesListPage) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - lastTapTime < 300) {
                                                    coroutineScope.launch {
                                                        when {
                                                            isGenresListPage || isStudiosListPage -> genericListState.animateScrollToItem(
                                                                0
                                                            )

                                                            isSeriesListPage -> seriesListState.animateScrollToItem(
                                                                0
                                                            )
                                                        }
                                                    }
                                                }
                                                lastTapTime = currentTime
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isActressDetailPage && actressDetail != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (actressDetail!!.avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = actressDetail!!.avatarUrl,
                                            contentDescription = actressDetail!!.name,
                                            modifier = Modifier
                                                .size(avatarSize)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column {
                                        Text(
                                            text = actressDetail!!.name,
                                            fontWeight = FontWeight.Bold,
                                            style = if (isTopBarExpanded) {
                                                MaterialTheme.typography.titleLarge
                                            } else {
                                                MaterialTheme.typography.titleMedium
                                            },
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isTopBarExpanded) {
                                            if (actressDetail!!.birthday.isNotEmpty()) {
                                                Text(
                                                    text = actressDetail!!.birthday,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                            if (actressDetail!!.height.isNotEmpty() || actressDetail!!.measurements.isNotEmpty()) {
                                                Text(
                                                    text = "${actressDetail!!.height} - ${actressDetail!!.measurements}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                            if (actressDetail!!.videoCount > 0) {
                                                Text(
                                                    text = "${actressDetail!!.videoCount} 视频",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        categoryInfo.first.ifEmpty { categoryTitle },
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (categoryInfo.second.isNotEmpty()) {
                                        Text(
                                            text = categoryInfo.second,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Row {
                            if (sortOptions.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "排序",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        sortOptions.forEachIndexed { index, option ->
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = 150,
                                                        delayMillis = index * 30
                                                    )
                                                ) + slideInVertically(
                                                    initialOffsetY = { it / 2 },
                                                    animationSpec = tween(
                                                        durationMillis = 200,
                                                        delayMillis = index * 30
                                                    )
                                                ),
                                                exit = fadeOut(animationSpec = tween(100))
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                4.dp
                                                            )
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
                                                            coroutineScope.launch {
                                                                seriesListState.animateScrollToItem(
                                                                    0
                                                                )
                                                            }
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
                            }
                            if (!isActressesListPage && !isSeriesListPage && !isGenresListPage && !isStudiosListPage) {
                                IconButton(onClick = {
                                    viewMode =
                                        if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                                }) {
                                    Icon(
                                        imageVector = if (viewMode == ViewMode.LIST) {
                                            Icons.Default.Menu
                                        } else {
                                            Icons.Default.Apps
                                        },
                                        contentDescription = "切换视图",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        })
    { paddingValues ->
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
                onRefresh = {
                    loadVideos(
                        currentSort.ifEmpty { extractSortFromUrl(categoryHref) },
                        isRefresh = true
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Crossfade(
                    targetState = when {
                        isLoading && videos.isEmpty() && actresses.isEmpty() && series.isEmpty() && genres.isEmpty() && studios.isEmpty() -> CategoryContentState.LOADING
                        error != null && videos.isEmpty() && actresses.isEmpty() && series.isEmpty() && genres.isEmpty() && studios.isEmpty() -> CategoryContentState.ERROR
                        videos.isEmpty() && actresses.isEmpty() && series.isEmpty() && genres.isEmpty() && studios.isEmpty() -> CategoryContentState.EMPTY
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
                                    Button(onClick = {
                                        loadVideos(currentSort.ifEmpty {
                                            extractSortFromUrl(
                                                categoryHref
                                            )
                                        })
                                    }) {
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
                            if (isSeriesListPage) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        state = seriesListState,
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
                                        itemsIndexed(
                                            items = series,
                                            key = { index, series -> series.id.ifEmpty { "series_$index" } }
                                        ) { _, seriesItem ->
                                            SeriesCard(
                                                series = seriesItem,
                                                onClick = { onSeriesClick("series/${seriesItem.id}") }
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
                            } else if (isActressesListPage) {
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
                                        itemsIndexed(
                                            items = actresses,
                                            key = { index, actress -> actress.id.ifEmpty { "actress_$index" } }
                                        ) { _, actress ->
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
                            } else if (isGenresListPage || isStudiosListPage) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        state = genericListState,
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
                                        if (isGenresListPage) {
                                            itemsIndexed(
                                                items = genres,
                                                key = { index, genre -> genre.id.ifEmpty { "genre_$index" } }
                                            ) { _, genre ->
                                                SeriesCard(
                                                    series = Series(
                                                        genre.id,
                                                        genre.name,
                                                        genre.videoCount
                                                    ),
                                                    onClick = { onSeriesClick("genres/${genre.id}") }
                                                )
                                            }
                                        } else {
                                            itemsIndexed(
                                                items = studios,
                                                key = { index, studio -> studio.id.ifEmpty { "studio_$index" } }
                                            ) { _, studio ->
                                                SeriesCard(
                                                    series = Series(
                                                        studio.id,
                                                        studio.name,
                                                        studio.videoCount
                                                    ),
                                                    onClick = { onSeriesClick("makers/${studio.id}") }
                                                )
                                            }
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