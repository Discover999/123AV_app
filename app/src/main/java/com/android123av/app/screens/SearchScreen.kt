package com.android123av.app.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.android123av.app.components.VideoItem
import com.android123av.app.components.LoadMoreButton
import com.android123av.app.models.Video
import com.android123av.app.network.searchVideos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SearchHistory(
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (Video) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var searchHistory by remember { mutableStateOf<List<SearchHistory>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        loadSearchHistory { history ->
            searchHistory = history
            if (history.isNotEmpty() && isFocused && searchQuery.isEmpty()) {
                showHistory = true
            }
        }
    }

    suspend fun performSearch(query: String, page: Int = 1, append: Boolean = false) {
        if (query.isEmpty()) return

        try {
            withContext(Dispatchers.Main) {
                if (page == 1) {
                    isSearching = true
                } else {
                    isLoadingMore = true
                }
                searchError = null
                hasSearched = true
                showHistory = false
            }

            val results = searchVideos(query, page)

            withContext(Dispatchers.Main) {
                if (append) {
                    searchResults = searchResults + results
                } else {
                    searchResults = results
                }
                hasNextPage = results.isNotEmpty() && results.size >= 20
                currentPage = page
                isSearching = false
                isLoadingMore = false
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                searchError = "网络连接失败，请检查网络设置"
                isSearching = false
                isLoadingMore = false
                searchResults = emptyList()
            }
        } catch (e: java.net.SocketTimeoutException) {
            withContext(Dispatchers.Main) {
                searchError = "搜索超时，请检查网络连接或稍后重试"
                isSearching = false
                isLoadingMore = false
                searchResults = emptyList()
            }
        } catch (e: java.net.UnknownHostException) {
            withContext(Dispatchers.Main) {
                searchError = "无法连接到服务器，请检查网络设置"
                isSearching = false
                isLoadingMore = false
                searchResults = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                searchError = "搜索失败，请稍后重试"
                isSearching = false
                isLoadingMore = false
                searchResults = emptyList()
            }
        }
    }

    fun performManualSearch(query: String) {
        if (query.isEmpty()) {
            searchResults = emptyList()
            searchError = null
            hasSearched = false
            currentPage = 1
            hasNextPage = true
            return
        }

        currentPage = 1
        hasNextPage = true
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            performSearch(query, 1, false)
            addToSearchHistory(query) { history ->
                searchHistory = history
            }
        }
    }

    fun removeFromHistory(historyItem: SearchHistory) {
        coroutineScope.launch {
            removeSearchHistoryItem(historyItem) { history ->
                searchHistory = history
            }
        }
    }

    fun clearAllHistory() {
        coroutineScope.launch {
            clearSearchHistory { history ->
                searchHistory = history
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "搜索",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (searchHistory.isNotEmpty() && !isSearching && searchResults.isEmpty()) {
                        IconButton(onClick = { clearAllHistory() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "清除历史",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            AnimatedSearchBox(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { performManualSearch(searchQuery) },
                focusRequester = focusRequester,
                isFocused = isFocused,
                onFocusChanged = { focused ->
                    isFocused = focused
                    showHistory = focused && searchQuery.isEmpty() && searchHistory.isNotEmpty()
                    if (!focused) {
                        keyboardController?.hide()
                    }
                },
                onClear = {
                    searchQuery = ""
                    searchResults = emptyList()
                    searchError = null
                    hasSearched = false
                    showHistory = searchHistory.isNotEmpty()
                }
            )

            AnimatedVisibility(
                visible = showHistory && searchHistory.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(
                    animationSpec = tween(200)
                )
            ) {
                SearchHistorySection(
                    history = searchHistory,
                    onHistoryClick = { history ->
                        searchQuery = history.query
                        performManualSearch(history.query)
                    },
                    onRemoveHistory = { removeFromHistory(it) },
                    onClearAll = { clearAllHistory() }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isSearching -> {
                        SkeletonLoadingList()
                    }
                    searchError != null -> {
                        ErrorState(
                            error = searchError!!,
                            onRetry = { coroutineScope.launch { performSearch(searchQuery) } }
                        )
                    }
                    searchResults.isNotEmpty() -> {
                        SearchResultsList(
                            results = searchResults,
                            isLoadingMore = isLoadingMore,
                            hasNextPage = hasNextPage,
                            onVideoClick = onVideoClick,
                            onLoadMore = {
                                coroutineScope.launch {
                                    performSearch(searchQuery, currentPage + 1, true)
                                }
                            },
                            searchQuery = searchQuery
                        )
                    }
                    hasSearched && searchQuery.isNotEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = "未找到搜索结果",
                            subtitle = "尝试使用其他关键词进行搜索"
                        )
                    }
                    else -> {
                        InitialState()
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.98f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    val keyboardController = LocalSoftwareKeyboardController.current

    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .shadow(
                elevation = if (isFocused) 12.dp else 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "输入关键词并按回车搜索",
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
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清除搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Search
                ),
                maxLines = 1,
                singleLine = true,
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onSearch()
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun SearchHistorySection(
    history: List<SearchHistory>,
    onHistoryClick: (SearchHistory) -> Unit,
    onRemoveHistory: (SearchHistory) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "清除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Text(
                text = "暂无搜索历史",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(history.take(10), key = { it.timestamp }) { historyItem ->
                    HistoryChip(
                        historyItem = historyItem,
                        onClick = { onHistoryClick(historyItem) },
                        onRemove = { onRemoveHistory(historyItem) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryChip(
    historyItem: SearchHistory,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = historyItem.query,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun SkeletonLoadingList() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            SkeletonVideoItem()
        }
    }
}

@Composable
fun SkeletonVideoItem() {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .shimmerEffect(translateAnimation, shimmerColors),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
fun Modifier.shimmerEffect(
    translateAnimation: Float,
    colors: List<Color>
): Modifier {
    return this.background(
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(translateAnimation, translateAnimation),
            end = Offset(translateAnimation + 200f, translateAnimation + 200f)
        )
    )
}

@Composable
fun SearchResultsList(
    results: List<Video>,
    isLoadingMore: Boolean,
    hasNextPage: Boolean,
    onVideoClick: (Video) -> Unit,
    onLoadMore: () -> Unit,
    searchQuery: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "找到 ${results.size} 个结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(results, key = { it.id }) { video ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        delayMillis = (results.indexOf(video) % 8) * 50
                    )
                ) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    initialOffsetY = { it / 2 }
                )
            ) {
                VideoItem(
                    video = video,
                    onClick = { onVideoClick(video) }
                )
            }
        }

        if (isLoadingMore) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        if (hasNextPage && !isLoadingMore) {
            item {
                LoadMoreButton(
                    onClick = onLoadMore,
                    isLoading = isLoadingMore,
                    enabled = true
                )
            }
        }
    }
}

@Composable
fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "搜索错误",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedButton(
            onClick = onRetry,
            text = "重试"
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun InitialState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = "搜索提示",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "搜索视频",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "输入关键词并按回车键开始搜索",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    text: String
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(text)
    }
}

private val searchHistoryStore = mutableMapOf<String, MutableList<SearchHistory>>()

suspend fun loadSearchHistory(onResult: (List<SearchHistory>) -> Unit) {
    withContext(Dispatchers.IO) {
        val history = searchHistoryStore["default"]?.sortedByDescending { it.timestamp } ?: emptyList()
        withContext(Dispatchers.Main) {
            onResult(history)
        }
    }
}

suspend fun addToSearchHistory(query: String, onResult: (List<SearchHistory>) -> Unit) {
    withContext(Dispatchers.IO) {
        if (!searchHistoryStore.containsKey("default")) {
            searchHistoryStore["default"] = mutableListOf()
        }
        searchHistoryStore["default"]?.let { list ->
            list.removeAll { it.query == query }
            list.add(0, SearchHistory(query))
            if (list.size > 20) {
                list.removeAt(list.lastIndex)
            }
            val sorted = list.sortedByDescending { it.timestamp }
            withContext(Dispatchers.Main) {
                onResult(sorted)
            }
        }
    }
}

suspend fun removeSearchHistoryItem(item: SearchHistory, onResult: (List<SearchHistory>) -> Unit) {
    withContext(Dispatchers.IO) {
        searchHistoryStore["default"]?.let { list ->
            list.remove(item)
            val sorted = list.sortedByDescending { it.timestamp }
            withContext(Dispatchers.Main) {
                onResult(sorted)
            }
        }
    }
}

suspend fun clearSearchHistory(onResult: (List<SearchHistory>) -> Unit) {
    withContext(Dispatchers.IO) {
        searchHistoryStore["default"]?.clear()
        withContext(Dispatchers.Main) {
            onResult(emptyList())
        }
    }
}
