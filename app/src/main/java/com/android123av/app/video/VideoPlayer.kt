package com.android123av.app.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.android123av.app.models.Video
import com.android123av.app.network.fetchM3u8UrlWithWebView
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

// 创建带自定义请求头的MediaSource
@OptIn(UnstableApi::class)
private fun createMediaSource(url: String, okHttpClient: OkHttpClient): MediaSource {
    // 使用固定请求头
    val referer = "https://123av.com/"
    
    // 创建OkHttpDataSource.Factory以利用OkHttpClient的功能
    val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to referer,
                "Origin" to referer.substringBeforeLast("/"),
                "Accept" to "*/*",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Connection" to "keep-alive",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache"
            )
        )
    
    // 创建MediaItem
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .build()
    
    // 根据URL类型创建不同的MediaSource
    return when {
        url.endsWith(".m3u8") || url.contains(".m3u8?") -> {
            // HLS视频
            HlsMediaSource.Factory(okHttpDataSourceFactory)
                .setExtractorFactory(DefaultHlsExtractorFactory())
                .createMediaSource(mediaItem)
        }
        url.endsWith(".mpd") -> {
            // DASH视频
            DashMediaSource.Factory(
                DefaultDashChunkSource.Factory(okHttpDataSourceFactory),
                okHttpDataSourceFactory
            )
                .createMediaSource(mediaItem)
        }
        else -> {
            // 渐进式视频
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }
}

// 视频播放页面组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    video: Video,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videoUrl by remember { mutableStateOf(video.videoUrl) }
    var isLoading by remember { mutableStateOf(videoUrl == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 初始化ExoPlayer
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                playWhenReady = true
            }
    }
    
    // 释放ExoPlayer资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    // 播放视频的辅助函数
    suspend fun playVideo(url: String) {
        withContext(Dispatchers.Main) {
            try {
                isLoading = true
                errorMessage = null
                
                // 记录视频播放请求信息
                println("DEBUG: 开始播放视频")
                println("DEBUG: 视频ID: ${video.id}")
                println("DEBUG: 视频标题: ${video.title}")
                println("DEBUG: 视频URL: $url")
                println("DEBUG: 是否为收藏视频: ${video.id == "fav_custom"}")
                
                // 针对收藏视频添加更详细的请求信息
                if (video.id == "fav_custom") {
                    println("DEBUG: 收藏视频播放请求详情")
                    println("DEBUG: 请求URL: $url")
                    println("DEBUG: 请求时间: ${System.currentTimeMillis()}")
                    println("DEBUG: 请求头信息:")
                    println("DEBUG: Referer: https://123av.com/")
                    println("DEBUG: Origin: https://123av.com")
                    println("DEBUG: User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 9 Build/AD1A.240411.003.A5; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.54 Mobile Safari/537.36")
                    println("DEBUG: Accept: */*")
                    println("DEBUG: Accept-Language: zh-CN,zh;q=0.9,en;q=0.8")
                }
                
                // 创建带自定义请求头的MediaSource
                val mediaSource = createMediaSource(url, okHttpClient)
                
                // 针对收藏视频添加媒体源设置日志
                if (video.id == "fav_custom") {
                    println("DEBUG: 收藏视频媒体源设置")
                    println("DEBUG: 开始设置媒体源时间: ${System.currentTimeMillis()}")
                }
                
                // 设置播放器的媒体源并准备播放
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                
                // 针对收藏视频添加准备播放日志
                if (video.id == "fav_custom") {
                    println("DEBUG: 收藏视频开始准备播放")
                    println("DEBUG: 准备播放时间: ${System.currentTimeMillis()}")
                }
                
                // 监听播放状态
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateStr = when(playbackState) {
                            Player.STATE_IDLE -> "STATE_IDLE"
                            Player.STATE_BUFFERING -> "STATE_BUFFERING"
                            Player.STATE_READY -> "STATE_READY"
                            Player.STATE_ENDED -> "STATE_ENDED"
                            else -> "UNKNOWN_STATE"
                        }
                        println("DEBUG: 播放状态变化: $stateStr")
                        
                        // 针对收藏视频添加更详细的状态日志
                        if (video.id == "fav_custom") {
                            println("DEBUG: 收藏视频播放状态响应")
                            println("DEBUG: 状态代码: $playbackState")
                            println("DEBUG: 状态描述: $stateStr")
                            println("DEBUG: 响应时间: ${System.currentTimeMillis()}")
                        }
                        
                        if (playbackState == Player.STATE_READY) {
                            isLoading = false
                            println("DEBUG: 视频准备完成，可以开始播放")
                            
                            if (video.id == "fav_custom") {
                                println("DEBUG: 收藏视频播放成功响应")
                                println("DEBUG: 视频时长: ${exoPlayer.duration / 1000}秒")
                                println("DEBUG: 视频宽度: ${exoPlayer.videoSize.width}")
                                println("DEBUG: 视频高度: ${exoPlayer.videoSize.height}")
                                println("DEBUG: 准备完成时间: ${System.currentTimeMillis()}")
                            }
                        } else if (playbackState == Player.STATE_BUFFERING) {
                            println("DEBUG: 视频缓冲中")
                        } else if (playbackState == Player.STATE_ENDED) {
                            println("DEBUG: 视频播放结束")
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "视频播放失败: ${error.message}"
                        println("DEBUG: 视频播放错误发生")
                        println("DEBUG: ExoPlayer error - ${error.javaClass.name} - ${error.message}")
                        println("DEBUG: Error code - ${error.errorCode}")
                        println("DEBUG: Error cause - ${error.cause}")
                        println("DEBUG: Error stack trace:")
                        error.printStackTrace()
                        
                        // 针对收藏视频添加更详细的错误日志
                        if (video.id == "fav_custom") {
                            println("DEBUG: 收藏视频播放错误响应详情")
                            println("DEBUG: 错误发生时间: ${System.currentTimeMillis()}")
                            println("DEBUG: 错误类型: ${error.javaClass.name}")
                            println("DEBUG: 错误消息: ${error.message}")
                            println("DEBUG: 错误代码: ${error.errorCode}")
                        }
                        
                        // 检查是否是HTTP错误
                        if (error.cause is HttpDataSource.InvalidResponseCodeException) {
                            val httpError = error.cause as HttpDataSource.InvalidResponseCodeException
                            println("DEBUG: HTTP错误代码 - ${httpError.responseCode}")
                            println("DEBUG: HTTP错误信息 - ${httpError.message}")
                            
                            if (video.id == "fav_custom") {
                                println("DEBUG: 收藏视频HTTP请求失败详情")
                                println("DEBUG: HTTP响应代码: ${httpError.responseCode}")
                                println("DEBUG: HTTP响应消息: ${httpError.message}")
                                println("DEBUG: 请求URL: ${httpError.dataSpec.uri}")
                            }
                        }
                        
                        isLoading = false
                    }
                })
                
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "视频播放失败: ${e.message}"
                println("DEBUG: Playback error details: ${e.javaClass.name} - ${e.message}")
                isLoading = false
            }
        }
    }
    
    // 异步获取视频URL并播放
    LaunchedEffect(video.id) {
        if (videoUrl == null) {
            isLoading = true
            
            try {
                // 针对收藏视频添加获取URL的详细日志
                if (video.id == "fav_custom") {
                    println("DEBUG: 开始获取收藏视频URL")
                    println("DEBUG: 请求时间: ${System.currentTimeMillis()}")
                    println("DEBUG: 视频ID: ${video.id}")
                    println("DEBUG: 视频标题: ${video.title}")
                    println("DEBUG: 当前视频URL: ${video.videoUrl}")
                }

                // 首先尝试使用WebView获取M3U8链接
                var url: String? = fetchM3u8UrlWithWebView(context, video.id)
                println("DEBUG: WebView M3U8 URL: $url")
                
                // 如果WebView方法失败，回退到原有的HTML解析方法
                if (url == null) {
                    url = fetchVideoUrl(video.id)
                    println("DEBUG: HTML parse URL: $url")
                } else {
                    // 针对收藏视频添加成功获取URL的详细日志
                    if (video.id == "fav_custom") {
                        println("DEBUG: 收藏视频URL获取成功")
                        println("DEBUG: 获取方法: WebView")
                        println("DEBUG: 获取到的URL: $url")
                        println("DEBUG: 响应时间: ${System.currentTimeMillis()}")
                    }
                }
                
                if (url != null) {
                    videoUrl = url as String
                    println("DEBUG: Final video URL: $url")
                    // 处理视频播放
                    playVideo(url as String)
                } else {
                    errorMessage = "无法获取视频播放地址"
                    println("DEBUG: No video URL found")
                    
                    // 针对收藏视频添加获取URL失败的详细日志
                    if (video.id == "fav_custom") {
                        println("DEBUG: 收藏视频URL获取失败")
                        println("DEBUG: 失败时间: ${System.currentTimeMillis()}")
                        println("DEBUG: 错误消息: 无法获取视频播放地址")
                    }
                    
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "获取视频地址失败: ${e.message}"
                println("DEBUG: Error fetching video URL: ${e.javaClass.name} - ${e.message}")
                isLoading = false
            }
        } else {
            // 如果videoUrl已经存在，直接播放
            videoUrl?.let { playVideo(it) }
        }
    }
    
    // 页面UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = video.title) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        content = {
            Column(modifier = modifier.fillMaxSize().padding(it)) {
                // 视频播放器区域
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)) {
                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                player = exoPlayer
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // 加载指示器
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    // 错误提示
                    errorMessage?.let {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = it,
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                
                // 视频信息区域
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = video.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "时长: ${video.duration}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    )
}


