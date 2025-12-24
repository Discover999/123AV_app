package com.android123av.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.android123av.app.components.VideoItem
import com.android123av.app.models.Video
import com.android123av.app.network.searchVideos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// 搜索功能页面
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
    
    // 创建协程作用域
    val coroutineScope = rememberCoroutineScope()
    // 防抖计时器
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 焦点请求器
    val focusRequester = remember { FocusRequester() }
    // 键盘控制器
    val keyboardController = LocalSoftwareKeyboardController.current
    // 焦点状态
    var isFocused by remember { mutableStateOf(true) }

    // 搜索功能函数
    suspend fun performSearch(query: String) {
        if (query.isEmpty()) return
        
        try {
            withContext(Dispatchers.Main) {
                isSearching = true
                searchError = null
                hasSearched = true
            }
            
            val results = searchVideos(query)
            
            withContext(Dispatchers.Main) {
                searchResults = results
                isSearching = false
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                searchError = "网络连接失败，请检查网络设置"
                isSearching = false
                searchResults = emptyList()
            }
        } catch (e: java.net.SocketTimeoutException) {
            withContext(Dispatchers.Main) {
                searchError = "搜索超时，请检查网络连接或稍后重试"
                isSearching = false
                searchResults = emptyList()
            }
        } catch (e: java.net.UnknownHostException) {
            withContext(Dispatchers.Main) {
                searchError = "无法连接到服务器，请检查网络设置"
                isSearching = false
                searchResults = emptyList()
            }
        } catch (e: java.net.SocketException) {
            withContext(Dispatchers.Main) {
                searchError = "连接中断，请检查网络连接"
                isSearching = false
                searchResults = emptyList()
            }
        } catch (e: Exception) {
            // 记录异常日志（在实际应用中应该使用日志框架）
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                searchError = "搜索失败，请稍后重试"
                isSearching = false
                searchResults = emptyList()
            }
        }
    }

    // 手动搜索函数（按换行时调用）
    fun performManualSearch(query: String) {
        if (query.isEmpty()) {
            searchResults = emptyList()
            searchError = null
            hasSearched = false
            return
        }
        
        // 取消之前的搜索任务
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            performSearch(query)
        }
    }
    
    // 当页面加载时自动聚焦搜索框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = 16.dp)
        ) {
            // 搜索框组件
            TextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    searchQuery = newQuery
                },
                placeholder = { Text("输入关键词并按回车搜索") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            keyboardController?.hide()
                        }
                    },
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = emptyList()
                            searchError = null
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
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
                        performManualSearch(searchQuery)
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
            )

            // 搜索结果列表
            when {
                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "搜索中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                searchError != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "搜索错误",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = searchError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { coroutineScope.launch { performSearch(searchQuery) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
                searchResults.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "找到 ${searchResults.size} 个结果",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(searchResults, key = { it.id }) { video ->
                            VideoItem(
                                video = video,
                                onClick = { onVideoClick(video) }
                            )
                        }
                    }
                }
                hasSearched && searchQuery.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "无结果",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "未找到搜索结果",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "尝试使用其他关键词进行搜索",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    // 初始状态，显示提示信息
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索提示",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }
            }
        }
    }
}