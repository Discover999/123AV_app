package com.android123av.app.screens

import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android123av.app.DownloadsActivity
import com.android123av.app.VideoPlayerActivity
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
        DownloadTabInfo("下载中", Icons.Default.CloudDownload, downloadingTasks.size, downloadingTasks),
        DownloadTabInfo("已完成", Icons.Default.DownloadDone, completedTasks.size, completedTasks),
        DownloadTabInfo("已暂停", Icons.Default.Pause, pausedTasks.size, pausedTasks),
        DownloadTabInfo("失败", Icons.Default.Error, failedTasks.size, failedTasks)
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
            DownloadCategoryTabs(
                tabs = tabs,
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
            
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

private data class DownloadTabInfo(
    val title: String,
    val icon: ImageVector,
    val count: Int,
    val tasks: List<DownloadTask>
)

@Composable
private fun DownloadCategoryTabs(
    tabs: List<DownloadTabInfo>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            DownloadCategoryChip(
                tab = tab,
                isSelected = selectedIndex == index,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
private fun DownloadCategoryChip(
    tab: DownloadTabInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(300),
        label = "chipBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "chipContent"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Text(
                text = "${tab.title} (${tab.count})",
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = task.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Black.copy(alpha = 0.3f),
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Red
                            )
                        }
                    }
                    else -> {}
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusBadge(status = task.status)
                            Text(
                                text = task.progressDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.speedDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "${DownloadTask.formatBytes(task.downloadedBytes)} / ${DownloadTask.formatBytes(task.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusBadge(status = task.status)
                            if (task.duration > 0) {
                                Text(
                                    text = DownloadTask.formatDuration(task.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = DownloadTask.formatBytes(task.totalBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusBadge(status = task.status)
                            Text(
                                text = task.progressDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "${DownloadTask.formatBytes(task.downloadedBytes)} / ${DownloadTask.formatBytes(task.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    DownloadStatus.FAILED -> {
                        StatusBadge(status = task.status)
                        task.errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    DownloadStatus.PENDING -> {
                        StatusBadge(status = task.status)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatDate(task.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        FilledTonalIconButton(
                            onClick = onPause,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                        FilledTonalIconButton(
                            onClick = onResume,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "继续",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        FilledTonalIconButton(
                            onClick = onOpen,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "播放",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {}
                }
                
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra("localVideoPath", videoFile.absolutePath)
                putExtra("videoTitle", task.title)
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
