package com.android123av.app.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android123av.app.download.DownloadDatabase
import com.android123av.app.download.DownloadStatus
import com.android123av.app.download.DownloadTask
import com.android123av.app.download.M3U8DownloadManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val downloadManager = remember { M3U8DownloadManager(context) }
    val database = remember { DownloadDatabase.getInstance(context) }
    val downloadTaskDao = remember { database.downloadTaskDao() }
    
    var downloadTasks by remember { mutableStateOf<List<DownloadTask>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var taskToDelete by remember { mutableStateOf<DownloadTask?>(null) }
    
    LaunchedEffect(Unit) {
        downloadTaskDao.getAllTasks().collectLatest { tasks ->
            downloadTasks = tasks
        }
    }
    
    val downloadingTasks = downloadTasks.filter { 
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING 
    }
    val completedTasks = downloadTasks.filter { it.status == DownloadStatus.COMPLETED }
    val pausedTasks = downloadTasks.filter { it.status == DownloadStatus.PAUSED }
    val failedTasks = downloadTasks.filter { it.status == DownloadStatus.FAILED }
    
    val tabs = listOf(
        TabInfo("下载中", downloadingTasks.size, downloadingTasks),
        TabInfo("已完成", completedTasks.size, completedTasks),
        TabInfo("已暂停", pausedTasks.size, pausedTasks),
        TabInfo("失败", failedTasks.size, failedTasks)
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = "${tab.title} (${tab.count})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }
            
            val currentTasks = tabs[selectedTab].tasks
            
            if (currentTasks.isEmpty()) {
                EmptyState(
                    icon = when (selectedTab) {
                        0 -> Icons.Default.CloudDownload
                        1 -> Icons.Default.DownloadDone
                        2 -> Icons.Default.Pause
                        else -> Icons.Default.Error
                    },
                    message = when (selectedTab) {
                        0 -> "暂无下载任务"
                        1 -> "暂无已下载视频"
                        2 -> "暂无暂停的下载"
                        else -> "暂无失败的下载"
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = currentTasks,
                        key = { it.id }
                    ) { task ->
                        DownloadTaskCard(
                            task = task,
                            onPause = {
                                if (task.status == DownloadStatus.DOWNLOADING) {
                                    downloadManager.pauseDownload(task.id)
                                }
                            },
                            onResume = {
                                if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                                    coroutineScope.launch {
                                        downloadManager.resumeDownload(task.id)
                                    }
                                }
                            },
                            onCancel = {
                                taskToDelete = task
                            },
                            onOpen = {
                                openDownloadedVideo(context, task)
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除\"${taskToDelete?.title}\"吗？此操作不可恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        taskToDelete?.let { task ->
                            downloadManager.deleteDownload(task.id)
                        }
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private data class TabInfo(
    val title: String,
    val count: Int,
    val tasks: List<DownloadTask>
)

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                AsyncImage(
                    model = task.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge(status = task.status)
                        
                        if (task.status == DownloadStatus.DOWNLOADING) {
                            Text(
                                text = "${task.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Text(
                        text = formatDate(task.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (task.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                        IconButton(onClick = onResume) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "继续",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onOpen) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "播放",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {}
                }
                
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val (icon, color, text) = when (status) {
        DownloadStatus.PENDING -> Triple(
            Icons.Default.Schedule,
            MaterialTheme.colorScheme.tertiary,
            "等待中"
        )
        DownloadStatus.DOWNLOADING -> Triple(
            Icons.Default.Download,
            MaterialTheme.colorScheme.primary,
            "下载中"
        )
        DownloadStatus.PAUSED -> Triple(
            Icons.Default.Pause,
            MaterialTheme.colorScheme.secondary,
            "已暂停"
        )
        DownloadStatus.COMPLETED -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            "已完成"
        )
        DownloadStatus.FAILED -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            "失败"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun openDownloadedVideo(context: android.content.Context, task: DownloadTask) {
    try {
        val videoFile = File(task.savePath, "video.mp4")
        if (videoFile.exists()) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.fromFile(videoFile),
                    "video/mp4"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } else {
            android.widget.Toast.makeText(
                context,
                "视频文件不存在",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "无法打开视频: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
