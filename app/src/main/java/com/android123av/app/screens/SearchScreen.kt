package com.android123av.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android123av.app.components.VideoItem
import com.android123av.app.models.Video
import com.android123av.app.network.searchVideos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 搜索功能页面
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (Video) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    
    // 创建协程作用域
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // 页面标题
        Text(
            text = "搜索",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally)
        )
        
        // 搜索框组件
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索视频...") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        isSearching = true
                        // 使用协程调用搜索函数
                        coroutineScope.launch(Dispatchers.IO) {
                            val results = searchVideos(searchQuery)
                            launch(Dispatchers.Main) {
                                searchResults = results
                                isSearching = false
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 搜索结果列表
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else if (searchResults.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { video ->
                    VideoItem(
                        video = video,
                        onClick = { onVideoClick(video) }
                    )
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "未找到搜索结果",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}


