package com.android123av.app.screens

import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.android123av.app.models.Video
import com.android123av.app.models.VideoDetails
import com.android123av.app.network.fetchM3u8UrlWithWebView
import com.android123av.app.network.fetchVideoDetails
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.fetchVideoUrlParallel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.pm.ActivityInfo
import kotlinx.coroutines.async

data class PlaybackSpeed(
    val speed: Float,
    val label: String
)

val playbackSpeeds = listOf(
    PlaybackSpeed(0.5f, "0.5x"),
    PlaybackSpeed(0.75f, "0.75x"),
    PlaybackSpeed(1.0f, "1.0x"),
    PlaybackSpeed(1.25f, "1.25x"),
    PlaybackSpeed(1.5f, "1.5x"),
    PlaybackSpeed(1.75f, "1.75x"),
    PlaybackSpeed(2.0f, "2.0x")
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    video: Video,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    val activity = context as? androidx.activity.ComponentActivity
    val window = activity?.window

    var isLoading by remember { mutableStateOf(true) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var videoDetails by remember { mutableStateOf<VideoDetails?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var exoPlayer by remember { mutableStateOf<Player?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    var currentSpeedIndex by remember { mutableIntStateOf(2) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    
    fun setSystemUIVisibility(isFullscreen: Boolean) {
        window?.let { win ->
            if (isFullscreen) {
                win.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                win.insetsController?.hide(WindowInsets.Type.statusBars())
                win.insetsController?.hide(WindowInsets.Type.navigationBars())
            } else {
                win.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                win.insetsController?.show(WindowInsets.Type.statusBars())
                win.insetsController?.show(WindowInsets.Type.navigationBars())
            }
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls && !isLocked) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "controlsAlpha"
    )

    val hideControlsJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    fun updateInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun scheduleHideControls() {
        hideControlsJob.value?.cancel()
        if (exoPlayer?.isPlaying == true && !isLocked && !showSpeedSelector) {
            hideControlsJob.value = coroutineScope.launch {
                val hideDelay = when {
                    playbackState == Player.STATE_BUFFERING -> 8000L
                    else -> 5000L
                }
                delay(hideDelay)
                if (System.currentTimeMillis() - lastInteractionTime >= hideDelay - 500) {
                    showControls = false
                }
            }
        }
    }

    fun showControlsTemporarily() {
        updateInteractionTime()
        showControls = true
        scheduleHideControls()
    }

    fun hideControls() {
        hideControlsJob.value?.cancel()
        showControls = false
    }

    fun createMediaSource(url: String): MediaSource {
        val factory = DefaultHttpDataSource.Factory()
        return if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        }
    }

    fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                when (state) {
                    Player.STATE_READY -> scheduleHideControls()
                    Player.STATE_ENDED -> {
                        showControls = true
                        hideControlsJob.value?.cancel()
                        isPlaying = false
                        // 播放完成后的处理
                        if (isFullscreen) {
                            isFullscreen = false
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        // 缓冲时显示控件
                        showControls = true
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    scheduleHideControls()
                } else {
                    hideControlsJob.value?.cancel()
                    showControls = true
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorMsg = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "直播已结束，请刷新重试"
                    PlaybackException.ERROR_CODE_TIMEOUT -> "网络连接超时，请检查网络"
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "网络错误，请检查网络连接"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "视频格式不支持"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败"
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> "视频解码失败"
                    else -> "播放错误: ${error.message ?: "未知错误"}"
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                // 播放错误时显示控件以便用户操作
                showControls = true
                hideControlsJob.value?.cancel()
            }
        })
    }

    LaunchedEffect(video) {
        isLoading = true
        errorMessage = null
        
        coroutineScope.launch {
            try {
                val videoUrlDeferred = async {
                    if (!video.videoUrl.isNullOrBlank()) {
                        video.videoUrl
                    } else {
                        fetchVideoUrlParallel(context, video.id, timeoutMs = 6000)
                            ?: fetchVideoUrl(video.id)?.takeIf { it.contains(".m3u8") }
                            ?: fetchM3u8UrlWithWebView(context, video.id)
                    }
                }
                
                val detailsDeferred = async {
                    if (video.details != null) {
                        video.details
                    } else {
                        fetchVideoDetails(video.id)
                    }
                }
                
                videoUrl = videoUrlDeferred.await()
                videoDetails = detailsDeferred.await()
                
                if (videoUrl == null) {
                    errorMessage = "无法获取视频播放地址"
                }
            } catch (e: Exception) {
                errorMessage = "获取视频失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    BackHandler(isFullscreen) {
        isFullscreen = false
    }
    
    LaunchedEffect(isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    LaunchedEffect(isFullscreen) {
        setSystemUIVisibility(isFullscreen)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            hideControlsJob.value?.cancel()
            setSystemUIVisibility(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (exoPlayer?.isPlaying == true) exoPlayer?.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when {
        isLoading -> LoadingState(video.title)
        errorMessage != null -> VideoErrorState(
            message = errorMessage!!,
            onBack = { onBack() },
            onRetry = {
                errorMessage = null
                isLoading = true
                videoUrl = null
                coroutineScope.launch {
                    try {
                        if (!video.videoUrl.isNullOrBlank()) {
                            videoUrl = video.videoUrl
                        } else {
                            videoUrl = fetchVideoUrlParallel(context, video.id, timeoutMs = 6000)
                            if (videoUrl == null) {
                                val httpUrl = fetchVideoUrl(video.id)
                                if (httpUrl != null && httpUrl.contains(".m3u8")) {
                                    videoUrl = httpUrl
                                } else {
                                    videoUrl = fetchM3u8UrlWithWebView(context, video.id)
                                }
                            }
                        }
                        if (videoUrl == null) {
                            errorMessage = "无法获取视频播放地址"
                        }
                    } catch (e: Exception) {
                        errorMessage = "获取视频失败: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
        videoUrl != null -> {
            Box(modifier = modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = isFullscreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "fullscreenTransition"
                ) { fullscreen ->
                    if (fullscreen) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer ?: ExoPlayer.Builder(ctx).build().apply {
                                            val source = createMediaSource(videoUrl!!)
                                            setMediaSource(source)
                                            prepare()
                                            playWhenReady = true
                                            setupPlayerListener(this)
                                            exoPlayer = this
                                        }
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        useController = false
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { playerView ->
                                    if (playerView.player != exoPlayer) {
                                        playerView.player = exoPlayer
                                    }
                                    playerView.resizeMode = resizeMode
                                }
                            )

                            PlayerControls(
                                exoPlayer = exoPlayer,
                                isFullscreen = isFullscreen,
                                isLocked = isLocked,
                                showSpeedSelector = showSpeedSelector,
                                currentSpeedIndex = currentSpeedIndex,
                                controlsAlpha = controlsAlpha,
                                showControls = showControls,
                                lastInteractionTime = lastInteractionTime,
                                onBack = if (isFullscreen) {{ isFullscreen = false }} else onBack,
                                onFullscreen = { isFullscreen = !isFullscreen },
                                onLock = { isLocked = !isLocked },
                                onSpeedChange = { currentSpeedIndex = it },
                                onSpeedSelectorToggle = { showSpeedSelector = !showSpeedSelector },
                                onPlayPause = {
                                    exoPlayer?.let { player ->
                                        if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                    }
                                },
                                onShowControls = { showControls = true },
                                onHideControls = { showControls = false },
                                onUpdateInteractionTime = { updateInteractionTime() },
                                onHideControlsNow = { hideControls() },
                                onShowControlsTemporarily = { showControlsTemporarily() },
                                onResizeModeChange = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                resizeMode = resizeMode
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = exoPlayer ?: ExoPlayer.Builder(ctx).build().apply {
                                                val source = createMediaSource(videoUrl!!)
                                                setMediaSource(source)
                                                prepare()
                                                playWhenReady = true
                                                setupPlayerListener(this)
                                                exoPlayer = this
                                            }
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            useController = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { playerView ->
                                        if (playerView.player != exoPlayer) {
                                            playerView.player = exoPlayer
                                        }
                                        playerView.resizeMode = resizeMode
                                    }
                                )

                                PlayerControls(
                                    exoPlayer = exoPlayer,
                                    isFullscreen = isFullscreen,
                                    isLocked = isLocked,
                                    showSpeedSelector = showSpeedSelector,
                                    currentSpeedIndex = currentSpeedIndex,
                                    controlsAlpha = controlsAlpha,
                                    showControls = showControls,
                                    lastInteractionTime = lastInteractionTime,
                                    onBack = if (isFullscreen) {{ isFullscreen = false }} else onBack,
                                    onFullscreen = { isFullscreen = !isFullscreen },
                                    onLock = { isLocked = !isLocked },
                                    onSpeedChange = { currentSpeedIndex = it },
                                    onSpeedSelectorToggle = { showSpeedSelector = !showSpeedSelector },
                                    onPlayPause = {
                                        exoPlayer?.let { player ->
                                            if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                        }
                                    },
                                    onShowControls = { showControls = true },
                                    onHideControls = { showControls = false },
                                    onUpdateInteractionTime = { updateInteractionTime() },
                                    onHideControlsNow = { hideControls() },
                                    onShowControlsTemporarily = { showControlsTemporarily() },
                                    onResizeModeChange = {
                                        resizeMode = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    },
                                    resizeMode = resizeMode
                                )
                            }

                            if (videoDetails != null) {
                                VideoInfoSection(
                                    video = video,
                                    videoDetails = videoDetails!!,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "正在加载视频...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(horizontal = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VideoErrorState(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // 错误图标卡片
            Card(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 错误标题
            Text(
                text = "播放失败",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 错误信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("返回")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新加载",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新加载")
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerControls(
    exoPlayer: Player?,
    isFullscreen: Boolean,
    isLocked: Boolean,
    showSpeedSelector: Boolean,
    currentSpeedIndex: Int,
    controlsAlpha: Float,
    showControls: Boolean,
    lastInteractionTime: Long,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onSpeedSelectorToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onShowControls: () -> Unit,
    onHideControls: () -> Unit,
    onUpdateInteractionTime: () -> Unit,
    onHideControlsNow: () -> Unit,
    onShowControlsTemporarily: () -> Unit,
    onResizeModeChange: () -> Unit,
    resizeMode: Int
) {
    val context = LocalContext.current
    
    var currentPosition by remember { mutableStateOf(exoPlayer?.currentPosition ?: 0L) }
    var duration by remember { mutableStateOf(exoPlayer?.duration?.takeIf { it > 0 } ?: 0L) }
    var isPlaying by remember { mutableStateOf(exoPlayer?.isPlaying ?: false) }
    var playbackState by remember { mutableStateOf(exoPlayer?.playbackState ?: Player.STATE_IDLE) }
    
    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(200)
            exoPlayer?.let { player ->
                currentPosition = player.currentPosition
                duration = player.duration.takeIf { it > 0 } ?: 0L
                isPlaying = player.isPlaying
                playbackState = player.playbackState
            }
        }
    }

    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

    val isBuffering = playbackState == Player.STATE_BUFFERING
    val isEnded = playbackState == Player.STATE_ENDED
    val isIdle = playbackState == Player.STATE_IDLE

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isBuffering -> CenterLoadingIndicator()
            isEnded -> PlaybackCompleteOverlay(
                isFullscreen = isFullscreen,
                exoPlayer = exoPlayer,
                onFullscreen = onFullscreen
            )
            isIdle -> CenterLoadingIndicator()
            else -> {
                if (!isLocked) {
                    VideoPlayerOverlay(
                        showControls = showControls,
                        showSpeedSelector = showSpeedSelector,
                        currentSpeedIndex = currentSpeedIndex,
                        currentPosition = currentPosition,
                        duration = duration,
                        progress = progress,
                        isPlaying = isPlaying,
                        controlsAlpha = controlsAlpha,
                        isFullscreen = isFullscreen,
                        onBack = onBack,
                        onFullscreen = onFullscreen,
                        onLock = onLock,
                        onSpeedChange = onSpeedChange,
                        onSpeedSelectorToggle = onSpeedSelectorToggle,
                        onPlayPause = onPlayPause,
                        onSeek = { seekPos ->
                            if (duration > 0) {
                                exoPlayer?.seekTo((seekPos * duration).toLong())
                            }
                        },
                        onSeekBackward = {
                            exoPlayer?.seekTo((currentPosition - 10000).coerceAtLeast(0))
                        },
                        onSeekForward = {
                            exoPlayer?.seekTo((currentPosition + 10000).coerceAtMost(duration))
                        },
                        onTap = onShowControlsTemporarily,
                        onHideControls = onHideControlsNow,
                        onUpdateInteractionTime = onUpdateInteractionTime,
                        onResizeModeChange = onResizeModeChange,
                        resizeMode = resizeMode
                    )
                } else {
                    LockedOverlay(onUnlock = onLock)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerOverlay(
    showControls: Boolean,
    showSpeedSelector: Boolean,
    currentSpeedIndex: Int,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    isPlaying: Boolean,
    controlsAlpha: Float,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onSpeedSelectorToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onTap: () -> Unit,
    onHideControls: () -> Unit,
    onUpdateInteractionTime: () -> Unit,
    onResizeModeChange: () -> Unit,
    resizeMode: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TapGestureLayer(
            showControls = showControls,
            onTap = onTap,
            onDoubleTapLeft = {
                onUpdateInteractionTime()
                onSeekBackward()
            },
            onDoubleTapRight = {
                onUpdateInteractionTime()
                onSeekForward()
            }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                TopBar(
                    isFullscreen = isFullscreen,
                    onBack = onBack,
                    onFullscreen = onFullscreen,
                    onLock = onLock,
                    onResizeModeChange = onResizeModeChange,
                    resizeMode = resizeMode
                )

                Spacer(modifier = Modifier.weight(1f))

                CenterControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onSeekBackward = onSeekBackward,
                    onSeekForward = onSeekForward
                )

                Spacer(modifier = Modifier.weight(1f))

                BottomBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    progress = progress,
                    showSpeedSelector = showSpeedSelector,
                    currentSpeedIndex = currentSpeedIndex,
                    onSeek = onSeek,
                    onSpeedSelectorToggle = onSpeedSelectorToggle,
                    onSpeedChange = onSpeedChange
                )
            }
        }
    }
}

@Composable
private fun TapGestureLayer(
    showControls: Boolean,
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        val isRightSide = offset.x > size.width / 2
                        if (isRightSide) onDoubleTapRight() else onDoubleTapLeft()
                    }
                )
            }
    )
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun TopBar(
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onResizeModeChange: () -> Unit,
    resizeMode: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isFullscreen) {
                ControlButton(
                    onClick = onResizeModeChange,
                    icon = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.AspectRatio
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.ZoomOutMap
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Default.CenterFocusStrong
                        else -> Icons.Default.AspectRatio
                    },
                    contentDescription = "缩放模式"
                )
            }
            ControlButton(
                onClick = onLock,
                icon = Icons.Default.Lock,
                contentDescription = "锁定"
            )
            ControlButton(
                onClick = onFullscreen,
                icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "退出全屏" else "全屏"
            )
        }
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            onClick = onSeekBackward,
            icon = Icons.Default.Replay10,
            contentDescription = "快退 10 秒",
            size = 48.dp
        )

        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause
        )

        ControlButton(
            onClick = onSeekForward,
            icon = Icons.Default.Forward10,
            contentDescription = "快进 10 秒",
            size = 48.dp
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "playPauseScale"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(150),
        label = "playPauseAlpha"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
            .size(72.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "controlButtonScale"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .size(size)
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        onClick()
                    }
                )
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun BottomBar(
    currentPosition: Long,
    duration: Long,
    progress: Float,
    showSpeedSelector: Boolean,
    currentSpeedIndex: Int,
    onSeek: (Float) -> Unit,
    onSpeedSelectorToggle: () -> Unit,
    onSpeedChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(
            visible = showSpeedSelector,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SpeedSelector(
                currentIndex = currentSpeedIndex,
                onSpeedSelect = { index ->
                    onSpeedChange(index)
                    onSpeedSelectorToggle()
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentPosition),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                modifier = Modifier.width(44.dp)
            )

            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Text(
                text = formatTime(duration),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                modifier = Modifier.width(44.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(48.dp))

            TextButton(
                onClick = onSpeedSelectorToggle,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = playbackSpeeds[currentSpeedIndex].label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showSpeedSelector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "播放速度",
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    progress: Float,
    onSeek: (Float) -> Unit
) {
    Slider(
        value = progress,
        onValueChange = onSeek,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
        )
    )
}

@Composable
private fun SpeedSelector(
    currentIndex: Int,
    onSpeedSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            playbackSpeeds.forEachIndexed { index, speed ->
                FilterChip(
                    selected = currentIndex == index,
                    onClick = { onSpeedSelect(index) },
                    label = {
                        Text(
                            text = speed.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun LockedOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onUnlock() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "已锁定",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun CenterLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PlaybackCompleteOverlay(
    isFullscreen: Boolean,
    exoPlayer: Player?,
    onFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            },
            modifier = Modifier
                .width(160.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("重新播放")
        }
    }
}

@Composable
private fun IdleIndicator() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "准备播放...",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoInfoSection(
    video: Video,
    videoDetails: VideoDetails,
    modifier: Modifier = Modifier
) {
    var isTitleExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        
        if (video.title.length > 50) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { isTitleExpanded = !isTitleExpanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (isTitleExpanded) "收起" else "更多",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isTitleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoChip(
                icon = Icons.Default.Timer,
                text = videoDetails.duration
            )
            InfoChip(
                icon = Icons.Default.Person,
                text = videoDetails.maker.ifEmpty { "未知" }
            )
            InfoChip(
                icon = Icons.Default.Favorite,
                text = formatCount(videoDetails.favouriteCount)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        if (videoDetails.tags.isNotEmpty()) {
            Text(
                text = "标签: ${videoDetails.tags.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (videoDetails.genres.isNotEmpty()) {
            Text(
                text = "类型: ${videoDetails.genres.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            FilledTonalButton(
                onClick = { },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "收藏",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
        count >= 1000 -> String.format("%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
