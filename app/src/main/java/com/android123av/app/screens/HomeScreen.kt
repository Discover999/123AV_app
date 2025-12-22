package com.android123av.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.android123av.app.components.VideoItem
import com.android123av.app.components.CategoryTabs
import com.android123av.app.models.Video
import com.android123av.app.network.fetchVideosDataWithResponse
import com.android123av.app.network.parseVideosFromHtml

// 首页屏幕
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (Video) -> Unit
) {
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("新发布") }
    val coroutineScope = rememberCoroutineScope()

    // 根据分类获取对应的URL
    fun getCategoryUrl(category: String): String {
        return when (category) {
            "新发布" -> "https://123av.com/zh/dm9/new-release"
            "最近更新" -> "https://123av.com/zh/dm9/recent-update"
            "正在观看" -> "https://123av.com/zh/dm9/trending"
            "未审查" -> "https://123av.com/zh/dm9/uncensored"
            else -> "https://123av.com/zh/dm9"
        }
    }
    
    // 加载视频数据
    LaunchedEffect(currentPage, selectedCategory) {
        isLoading = true
        try {
            val categoryUrl = getCategoryUrl(selectedCategory)
            val (newVideos, paginationInfo) = parseVideosFromHtml(fetchVideosDataWithResponse(categoryUrl, currentPage).second)
            if (currentPage == 1) {
                videos = newVideos
            } else {
                videos = videos + newVideos
            }
            hasNextPage = paginationInfo.hasNextPage
        } catch (e: Exception) {
            e.printStackTrace()
            // 异常情况下保持视频列表为空
            videos = emptyList()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            // 分类标签组件
            CategoryTabs(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onCategoryChange = { category ->
                    selectedCategory = category
                    currentPage = 1 // 切换分类时重置页码
                }
            )
            
            if (videos.isEmpty() && !isLoading) {
                Text(
                    text = "暂无视频数据",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(32.dp)
                )
            } else {
                LazyColumn {
                    items(videos) { video ->
                        VideoItem(video = video, onClick = { onVideoClick(video) })
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            }
                        }
                    }

                    if (hasNextPage && !isLoading) {
                        item {
                            Button(
                                onClick = { currentPage++ },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text("加载更多")
                            }
                        }
                    }
                }
            }
        }
    }
}


