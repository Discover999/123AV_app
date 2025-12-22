package com.app.myapplication.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.R
import com.app.myapplication.models.RequestInfo
import com.app.myapplication.models.Video
import coil.compose.AsyncImage

// 请求信息显示组件
@Composable
fun RequestInfoScreen(requestInfo: RequestInfo, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "请求信息",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 请求信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // URL
                InfoRow(label = "请求URL", value = requestInfo.url)
                
                // 请求状态
                InfoRow(label = "请求状态", value = requestInfo.status)
                
                // 请求耗时
                InfoRow(label = "请求耗时", value = "${requestInfo.duration} 毫秒")
                
                // 响应大小
                InfoRow(label = "响应大小", value = formatBytes(requestInfo.responseSize))
                
                // 请求结果
                InfoRow(label = "请求结果", value = when (requestInfo.success) {
                    true -> "成功"
                    false -> "失败"
                    null -> "未请求"
                })
            }
        }
    }
}

// 信息行组件
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = "$label: ",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}

// 字节格式化函数
fun formatBytes(bytes: Int): String {
    if (bytes < 1024) {
        return "$bytes B"
    } else if (bytes < 1024 * 1024) {
        return "${String.format("%.2f", bytes / 1024.0)} KB"
    } else {
        return "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
    }
}

// 视频卡片组件
@Composable
fun VideoCard(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 视频缩略图（使用网络图片或占位图）
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                placeholder = painterResource(id = R.drawable.ic_dialog_info),
                error = painterResource(id = R.drawable.ic_dialog_info)
            )
            // 视频时长（显示在右下角）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Gray.copy(alpha = 0.8f))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
            // 占位空间，将标题推到底部
            Spacer(modifier = Modifier.weight(1f))
            // 视频标题
            Text(
                text = video.title,
                modifier = Modifier.padding(8.dp, 4.dp, 8.dp, 8.dp),
                maxLines = 2,
                fontSize = 14.sp
            )
        }
    }
}

// 分类标签组件 - 使用MD3设计语言
@Composable
fun CategoryTabs(
    modifier: Modifier = Modifier,
    onCategoryChange: (String) -> Unit
) {
    val categories = listOf(
        "新发布", "最近更新", "正在观看", "未审查"
    )
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    
    Column(modifier = modifier) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            edgePadding = 16.dp,
            modifier = modifier
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        onCategoryChange(category)
                    },
                    text = {
                        Text(
                            text = category,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
