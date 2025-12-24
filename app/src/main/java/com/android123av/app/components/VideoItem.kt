package com.android123av.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android123av.app.models.Video

// 视频列表项组件
@Composable
fun VideoItem(
    video: Video,
    onClick: () -> Unit
) {
    // 使用remember缓存所有属性，避免重复计算
    val imageModel = remember(video.thumbnailUrl) { video.thumbnailUrl }
    val durationText = remember(video.duration) { video.duration }
    val titleText = remember(video.title) { video.title }
    val videoId = remember(video.id) { video.id }
    
    // 预计算颜色值，避免在重组时重复创建
    val overlayColor = remember { Color.Black.copy(alpha = 0.7f) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(modifier = Modifier.height(200.dp)) {
            AsyncImage(
                model = imageModel,
                contentDescription = titleText,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // 启用内存缓存优化
                placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(overlayColor)
                    .padding(4.dp)
            ) {
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 12.sp,
                    // 使用remember避免字体重复计算
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
        Box(modifier = Modifier.padding(8.dp)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                // 添加行高缓存
                lineHeight = 20.sp
            )
        }
    }
}


