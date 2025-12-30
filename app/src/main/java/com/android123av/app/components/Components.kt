package com.android123av.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android123av.app.models.RequestInfo

@Composable
fun RequestInfoScreen(requestInfo: RequestInfo, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "请求信息",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(label = "请求URL", value = requestInfo.url)
                InfoRow(label = "请求状态", value = requestInfo.status)
                InfoRow(label = "请求耗时", value = "${requestInfo.duration} 毫秒")
                InfoRow(label = "响应大小", value = formatBytes(requestInfo.responseSize))
                InfoRow(
                    label = "请求结果",
                    value = when (requestInfo.success) {
                        true -> "成功"
                        false -> "失败"
                        null -> "未请求"
                    }
                )
            }
        }
    }
}

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

fun formatBytes(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
        else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
    }
}
