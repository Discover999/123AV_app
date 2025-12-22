package com.app.myapplication.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.myapplication.components.VideoItem
import com.app.myapplication.models.Video

// 收藏夹屏幕
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (Video) -> Unit
) {
    var favoriteVideos by remember { mutableStateOf(mutableListOf<Video>()) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var newFavoriteTitle by remember { mutableStateOf("") }
    var newFavoriteUrl by remember { mutableStateOf("") }

    // 添加示例收藏视频
    LaunchedEffect(Unit) {
        if (favoriteVideos.isEmpty()) {
            favoriteVideos.add(Video(id = "fav_custom", title = "自定义收藏视频", duration = "05:20", thumbnailUrl = "https://picsum.photos/id/101/300/200"))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn {
            items(favoriteVideos) { video ->
                VideoItem(video = video, onClick = { onVideoClick(video) })
            }
        }

        // 添加收藏按钮
        FloatingActionButton(
            onClick = { showAddFavoriteDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加收藏")
        }

        if (favoriteVideos.isEmpty()) {
            Text(
                text = "暂无收藏视频",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    // 添加收藏对话框
    if (showAddFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showAddFavoriteDialog = false },
            title = { Text("添加收藏视频") },
            text = {
                Column {
                    TextField(
                        value = newFavoriteTitle,
                        onValueChange = { newFavoriteTitle = it },
                        label = { Text("视频标题") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newFavoriteUrl,
                        onValueChange = { newFavoriteUrl = it },
                        label = { Text("视频URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFavoriteTitle.isNotBlank()) {
                            favoriteVideos.add(
                                Video(
                                    id = "fav_${System.currentTimeMillis()}",
                                    title = newFavoriteTitle,
                                    duration = "00:00",
                                    thumbnailUrl = "https://picsum.photos/id/${(100..200).random()}/300/200",
                                    videoUrl = if (newFavoriteUrl.isNotBlank()) newFavoriteUrl else null
                                )
                            )
                            newFavoriteTitle = ""
                            newFavoriteUrl = ""
                            showAddFavoriteDialog = false
                        }
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                Button(onClick = { showAddFavoriteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}