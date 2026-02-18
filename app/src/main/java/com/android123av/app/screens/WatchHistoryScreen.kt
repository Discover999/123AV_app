package com.android123av.app.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android123av.app.VideoPlayerActivity
import com.android123av.app.models.Video
import com.android123av.app.state.WatchHistoryItem
import com.android123av.app.state.WatchHistoryManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    
    val watchHistoryState = remember { WatchHistoryManager.watchHistory }
    val watchHistoryList by watchHistoryState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史观看") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (watchHistoryList.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清空历史"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (watchHistoryList.isEmpty()) {
            EmptyHistoryState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            AnimatedVisibility(
                visible = watchHistoryList.isEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                EmptyHistoryState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            AnimatedVisibility(
                visible = watchHistoryList.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = watchHistoryList,
                        key = { item: WatchHistoryItem -> item.videoId }
                    ) { historyItem ->
                        WatchHistoryItemCard(
                            historyItem = historyItem,
                            onClick = {
                                val intent = Intent(context, VideoPlayerActivity::class.java)
                                val video = Video(
                                    id = historyItem.videoId,
                                    title = historyItem.title,
                                    duration = "",
                                    thumbnailUrl = historyItem.thumbnailUrl,
                                    videoUrl = null,
                                    details = null
                                )
                                intent.putExtra("video", video)
                                context.startActivity(intent)
                            },
                            onDelete = {
                                WatchHistoryManager.removeWatchHistory(historyItem.videoId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空历史观看") },
            text = { Text("确定要清空所有观看历史吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        WatchHistoryManager.clearWatchHistory()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "暂无观看历史",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "观看视频后会自动记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryItemCard(
    historyItem: WatchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val peekThreshold = with(density) { 65.dp.toPx() }
    val deleteThreshold = with(density) { 100.dp.toPx() }

    var targetOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }
    var hasPassedPeek by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "offset"
    )

    val peekAlpha by animateFloatAsState(
        targetValue = if (hasPassedPeek) 1f else 0f,
        animationSpec = tween(150),
        label = "peekAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(200)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(200)
        ) + shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { this.alpha = peekAlpha }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.toInt(), 0) }
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                hasPassedPeek = targetOffsetX <= -peekThreshold
                            },
                            onDragEnd = {
                                isDragging = false
                                when {
                                    targetOffsetX <= -deleteThreshold -> {
                                        targetOffsetX = -deleteThreshold
                                        isVisible = false
                                        kotlinx.coroutines.GlobalScope.launch {
                                            delay(200)
                                            onDelete()
                                        }
                                    }
                                    hasPassedPeek -> {
                                        targetOffsetX = -peekThreshold
                                    }
                                    else -> {
                                        targetOffsetX = 0f
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                targetOffsetX = 0f
                                hasPassedPeek = false
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val newOffset = (targetOffsetX + dragAmount).coerceIn(-deleteThreshold, 0f)
                                targetOffsetX = newOffset
                                hasPassedPeek = newOffset <= -peekThreshold
                            }
                        )
                    }
                    .then(
                        if (hasPassedPeek) {
                            Modifier.combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    targetOffsetX = 0f
                                    hasPassedPeek = false
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDragging) 8.dp else 2.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (targetOffsetX >= -peekThreshold) {
                                    Modifier.clickable {
                                        if (animatedOffsetX < -peekThreshold / 2) {
                                            targetOffsetX = -deleteThreshold
                                        } else {
                                            targetOffsetX = 0f
                                            onClick()
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp, 70.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = historyItem.thumbnailUrl,
                                contentDescription = historyItem.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = historyItem.title,
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
                                if (historyItem.duration.isNotBlank()) {
                                    Text(
                                        text = historyItem.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (historyItem.performer.isNotBlank()) {
                                    Text(
                                        text = historyItem.performer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = formatWatchTime(historyItem.watchedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatWatchTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
