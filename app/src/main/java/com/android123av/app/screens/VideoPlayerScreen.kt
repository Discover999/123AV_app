package com.android123av.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.fetchM3u8UrlWithWebView

// 视频播放器屏幕
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    video: Video,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 获取视频URL
    LaunchedEffect(video) {
        isLoading = true
        try {
            // 先尝试使用fetchVideoUrl获取视频URL
            videoUrl = fetchVideoUrl(video.id)
            
            // 如果fetchVideoUrl返回null，尝试使用WebView方式获取M3U8链接
            if (videoUrl == null || !videoUrl!!.contains(".m3u8")) {
                videoUrl = fetchM3u8UrlWithWebView(context, video.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "获取视频URL失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载视频...")
            }
        } else if (videoUrl != null) {
            // 使用ExoPlayer播放视频
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = ExoPlayer.Builder(it).build().apply {
                            // 创建合适的MediaSource
                            val mediaSource = createMediaSource(videoUrl!!)
                            setMediaSource(mediaSource)
                            prepare()
                            play()
                        }
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    // 可以在这里更新播放器状态
                },
                onRelease = {
                    it.player?.release()
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "加载失败",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Red
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("无法加载视频，请稍后重试")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
        }

        // 返回按钮
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
        }

        // 视频标题
        Text(
            text = video.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
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


