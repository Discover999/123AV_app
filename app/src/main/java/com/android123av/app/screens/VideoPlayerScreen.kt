package com.android123av.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.widget.Toast
import com.android123av.app.models.Video
import com.android123av.app.models.VideoDetails
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.fetchM3u8UrlWithWebView
import com.android123av.app.network.fetchVideoDetails
import kotlinx.coroutines.launch

// 视频播放器屏幕
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    video: Video,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var progress by remember { mutableStateOf(0f) }
    var isVideoLoading by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var videoDetails by remember { mutableStateOf<VideoDetails?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 获取视频URL
    LaunchedEffect(video) {
        isLoading = true
        try {
            println("DEBUG: VideoPlayerScreen - Starting to fetch video URL for videoId: ${video.id}")
            println("DEBUG: Video title: ${video.title}")
            
            // 检查视频是否有直接的videoUrl
            if (!video.videoUrl.isNullOrBlank()) {
                println("DEBUG: Using existing video.videoUrl: ${video.videoUrl}")
                videoUrl = video.videoUrl
            } else {
                println("DEBUG: No existing videoUrl, attempting to fetch...")
                
                // 先尝试使用fetchVideoUrl获取视频URL
                videoUrl = fetchVideoUrl(video.id)
                println("DEBUG: fetchVideoUrl result: $videoUrl")
                
                // 如果fetchVideoUrl返回null，尝试使用WebView方式获取M3U8链接
                if (videoUrl == null || !videoUrl!!.contains(".m3u8")) {
                    println("DEBUG: fetchVideoUrl failed or no M3U8, trying WebView method...")
                    videoUrl = fetchM3u8UrlWithWebView(context, video.id)
                    println("DEBUG: WebView fetch result: $videoUrl")
                }
            }
            
            println("DEBUG: Final video URL: $videoUrl")
            
            if (videoUrl == null) {
                Toast.makeText(context, "无法获取视频播放地址", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            println("DEBUG: Exception while fetching video URL: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "获取视频URL失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    // 获取视频详情信息
    LaunchedEffect(video) {
        if (video.details == null) { // 如果视频对象中没有详情信息，则从网络获取
            isLoadingDetails = true
            try {
                val details = fetchVideoDetails(video.id)
                videoDetails = details
            } catch (e: Exception) {
                e.printStackTrace()
                println("DEBUG: 获取视频详情失败: ${e.message}")
            } finally {
                isLoadingDetails = false
            }
        } else {
            // 如果视频对象中已有详情信息，直接使用
            videoDetails = video.details
        }
    }

    // 更新播放进度
    LaunchedEffect(exoPlayer) {
        exoPlayer?.let { player ->
            while (true) {
                if (player.isPlaying) {
                    val newPosition = player.currentPosition
                    val newDuration = player.duration
                    val newProgress = if (newDuration > 0) newPosition.toFloat() / newDuration.toFloat() else 0f
                    
                    currentPosition = newPosition
                    duration = newDuration
                    progress = newProgress
                }
                kotlinx.coroutines.delay(100) // 每100ms更新一次
            }
        }
    }

    // 释放播放器资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                // 优化的加载界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "正在加载视频...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
            
            videoUrl != null && videoUrl!!.isNotEmpty() -> {
                // 优化的视频播放区域
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 视频播放区域 (16:9 比例)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        // ExoPlayer 视频播放
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    val exoPlayerInstance = ExoPlayer.Builder(context).build().apply {
                                        // 创建合适的MediaSource
                                        val mediaSource = createMediaSource(videoUrl!!)
                                        setMediaSource(mediaSource)
                                        prepare()
                                        
                                        // 设置播放状态监听
                                        addListener(object : Player.Listener {
                                            override fun onPlaybackStateChanged(state: Int) {
                                                when (state) {
                                                    Player.STATE_READY -> {
                                                        isVideoLoading = false
                                                        // 自动开始播放
                                                        play()
                                                    }
                                                    Player.STATE_BUFFERING -> {
                                                        isVideoLoading = true
                                                    }
                                                }
                                            }
                                            
                                            override fun onIsPlayingChanged(playing: Boolean) {
                                                isPlaying = playing
                                            }
                                            
                                            override fun onPlayerError(error: PlaybackException) {
                                                isVideoLoading = false
                                                Toast.makeText(context, "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                    }
                                    player = exoPlayerInstance
                                    exoPlayer = exoPlayerInstance
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    useController = false // 使用自定义控制界面
                                    
                                    // 点击显示/隐藏控制界面
                                    setOnClickListener {
                                        showControls = !showControls
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // 优化的自定义播放控制界面
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showControls,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.1f),
                                                Color.Black.copy(alpha = 0.3f),
                                                Color.Black.copy(alpha = 0.7f)
                                            ),
                                            startY = 0f,
                                            endY = Float.POSITIVE_INFINITY
                                        )
                                    )
                            ) {
                                // 顶部控制栏
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 返回按钮
                                    IconButton(
                                        onClick = onBack,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "返回",
                                            tint = Color.White
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.weight(1f))
                                    
                                    // 其他控制按钮可以在这里添加
                                }
                                
                                // 中央播放/暂停按钮
                                IconButton(
                                    onClick = {
                                        exoPlayer?.let { player ->
                                            if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(80.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            CircleShape
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPlaying) {
                                            // 自定义暂停图标 (两个竖条)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(20.dp)
                                                        .background(Color.White)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(20.dp)
                                                        .background(Color.White)
                                                )
                                            }
                                        } else {
                                            // 播放图标
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "播放",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // 底部进度条区域
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    // 进度条
                                    Slider(
                                        value = progress,
                                        onValueChange = { value ->
                                            exoPlayer?.let { player ->
                                                val newPosition = (value * player.duration).toLong()
                                                player.seekTo(newPosition)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                        )
                                    )
                                    
                                    // 时间显示
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatTime(currentPosition),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = formatTime(duration),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                
                                // 加载指示器
                                if (isVideoLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color.White,
                                        strokeWidth = 4.dp
                                    )
                                }
                            }
                        }
                    }
                    
                    // 视频信息区域
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // 视频标题
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // 视频元信息
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                // 基础信息
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "视频时长",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = video.duration,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            text = "视频ID",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = video.id,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                // 显示解析的视频详情
                                if (isLoadingDetails) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "正在加载视频详情...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (videoDetails != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // 代码
                                    if (videoDetails!!.code.isNotEmpty()) {
                                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                            Text(
                                                text = "代码",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = videoDetails!!.code,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    // 发布日期
                                    if (videoDetails!!.releaseDate.isNotEmpty()) {
                                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                            Text(
                                                text = "发布日期",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = videoDetails!!.releaseDate,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    // 类型
                                    if (videoDetails!!.genres.isNotEmpty()) {
                                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                            Text(
                                                text = "类型",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = videoDetails!!.genres.joinToString(", "),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    // 制作人
                                    if (videoDetails!!.maker.isNotEmpty()) {
                                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                            Text(
                                                text = "制作人",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = videoDetails!!.maker,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    // 标签
                                    if (videoDetails!!.tags.isNotEmpty()) {
                                        Column {
                                            Text(
                                                text = "标签",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = videoDetails!!.tags.joinToString(", "),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { /* 刷新视频 */ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("刷新")
                            }
                            
                            OutlinedButton(
                                onClick = { /* 分享功能 */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("分享")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
            
            else -> {
                // 优化的错误界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "加载失败",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "无法加载视频",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请检查网络连接或稍后重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = onBack) {
                                Text("返回")
                            }
                            OutlinedButton(
                                onClick = {
                                    // 重新尝试加载
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            videoUrl = fetchVideoUrl(video.id)
                                            if (videoUrl == null || !videoUrl!!.contains(".m3u8")) {
                                                videoUrl = fetchM3u8UrlWithWebView(context, video.id)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "重新加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Text("重新加载")
                            }
                        }
                    }
                }
            }
        }
    }
}

// 格式化时间的辅助函数
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// 创建媒体源的辅助函数
fun createMediaSource(url: String): MediaSource {
    val dataSourceFactory = DefaultHttpDataSource.Factory()
    
    return if (url.contains(".m3u8")) {
        // HLS视频源
        HlsMediaSource.Factory(dataSourceFactory)
            .setExtractorFactory(DefaultHlsExtractorFactory())
            .createMediaSource(MediaItem.fromUri(url))
    } else if (url.contains(".mpd")) {
        // DASH视频源
        DashMediaSource.Factory(
            DefaultDashChunkSource.Factory(dataSourceFactory),
            dataSourceFactory
        )
            .createMediaSource(MediaItem.fromUri(url))
    } else {
        // 普通视频源
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
    }
}


