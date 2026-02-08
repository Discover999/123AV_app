package com.android123av.app.screens

import android.content.Intent
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.android123av.app.CategoryActivity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android123av.app.state.rememberUserState
import com.android123av.app.utils.HapticUtils
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import java.io.File
import com.android123av.app.models.Video
import com.android123av.app.models.VideoDetails
import com.android123av.app.network.fetchM3u8UrlWithWebView
import com.android123av.app.network.fetchVideoDetails
import com.android123av.app.network.fetchVideoUrl
import com.android123av.app.network.fetchVideoUrlParallel
import com.android123av.app.network.fetchAllVideoParts
import com.android123av.app.network.fetchFavouriteStatus
import com.android123av.app.network.toggleFavourite
import com.android123av.app.network.improvedFetchVideoUrl
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.delay
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowInsetsController
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.android123av.app.DownloadsActivity
import com.android123av.app.download.DownloadStatus
import com.android123av.app.download.DownloadTask
import com.android123av.app.download.M3U8DownloadManager
import com.android123av.app.download.VideoDetailsCacheManager
import com.android123av.app.state.DownloadPathManager
import com.android123av.app.state.UserStateManager.isLoggedIn
import com.android123av.app.state.WatchHistoryManager
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

private val AlphaHigh = 0.9f
private val AlphaMediumHigh = 0.8f
private val AlphaMedium = 0.7f
private val AlphaMediumLow = 0.6f
private val AlphaLow = 0.5f
private val AlphaVeryLow = 0.3f

private val ControlsAnimationSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing
)

private val PlaybackErrorMessages = mapOf(
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW to "直播已结束，请刷新重试",
    PlaybackException.ERROR_CODE_TIMEOUT to "网络连接超时，请检查网络",
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED to "网络错误，请检查网络连接",
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to "网络连接失败",
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE to "视频格式不支持",
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED to "解码器初始化失败",
    PlaybackException.ERROR_CODE_DECODING_FAILED to "视频解码失败"
)

private const val PLAYBACK_ERROR_UNKNOWN = "未知错误"
private const val TAG = "VideoPlayer"
private const val BUFFERING_HIDE_DELAY_MS = 8000L
private const val DEFAULT_HIDE_DELAY_MS = 5000L
private const val HIDE_DELAY_THRESHOLD_MS = 500L
private const val ENABLE_DEBUG_LOG = true

private fun debugLog(message: String) {
    if (ENABLE_DEBUG_LOG) {
        Log.d(TAG, message)
    }
}

private val OverlayDark = Color.Black.copy(alpha = 0.75f)
private val OverlayMedium = Color.Black.copy(alpha = 0.6f)
private val OverlayLight = Color.Black.copy(alpha = 0.35f)
private val OverlayVeryLight = Color.Black.copy(alpha = 0.3f)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    video: Video? = null,
    localVideoPath: String? = null,
    localVideoId: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val userState = rememberUserState()
    
    val activity = context as? androidx.activity.ComponentActivity
    val window = activity?.window

    var isLoading by remember { mutableStateOf(true) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var videoDetails by remember { mutableStateOf<VideoDetails?>(null) }
    var cachedTitle by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoParts by remember { mutableStateOf<List<com.android123av.app.models.VideoPart>>(emptyList()) }
    var selectedPartIndex by remember { mutableIntStateOf(0) }
    var isLoadingParts by remember { mutableStateOf(false) }

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
    var isSeeking by remember { mutableStateOf(false) }
    
    val downloadManager = remember { M3U8DownloadManager(context) }
    var existingDownloadTask by remember { mutableStateOf<DownloadTask?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var isFavourite by remember { mutableStateOf(false) }
    var isTogglingFavourite by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    
    val videoTitle = cachedTitle ?: video?.title ?: ""
    
    fun setSystemUIVisibility(isFullscreen: Boolean) {
        window?.let { win ->
            if (isFullscreen) {
                @Suppress("DEPRECATION")
                win.setDecorFitsSystemWindows(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    win.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    win.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    @Suppress("DEPRECATION")
                    win.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                win.setDecorFitsSystemWindows(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    win.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                } else {
                    @Suppress("DEPRECATION")
                    win.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    fun hideSystemBars() {
        window?.let { win ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                win.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                win.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls && !isLocked) 1f else 0f,
        animationSpec = ControlsAnimationSpec,
        label = "controlsAlpha"
    )
    
    val controlsScale by animateFloatAsState(
        targetValue = if (showControls && !isLocked) 1f else 0.9f,
        animationSpec = ControlsAnimationSpec,
        label = "controlsScale"
    )

    val hideControlsJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val isLockedState = rememberUpdatedState(isLocked)
    val showSpeedSelectorState = rememberUpdatedState(showSpeedSelector)
    val isSeekingState = rememberUpdatedState(isSeeking)
    val exoPlayerState = rememberUpdatedState(exoPlayer)

    fun updateInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun scheduleHideControls() {
        hideControlsJob.value?.cancel()
        if (exoPlayerState.value?.isPlaying == true && !isLockedState.value && !showSpeedSelectorState.value && !isSeekingState.value) {
            hideControlsJob.value = coroutineScope.launch {
                val hideDelay = if (playbackState == Player.STATE_BUFFERING) {
                    BUFFERING_HIDE_DELAY_MS
                } else {
                    DEFAULT_HIDE_DELAY_MS
                }
                delay(hideDelay)
                if (System.currentTimeMillis() - lastInteractionTime >= hideDelay - HIDE_DELAY_THRESHOLD_MS) {
                    showControls = false
                }
            }
        }
    }

    fun toggleControls() {
        updateInteractionTime()
        if (isFullscreen) {
            hideSystemBars()
            showControls = true
            scheduleHideControls()
        } else {
            showControls = !showControls
            if (showControls) {
                scheduleHideControls()
            } else {
                hideControlsJob.value?.cancel()
            }
        }
    }

    fun showControlsTemporarily() {
        updateInteractionTime()
        if (isFullscreen) {
            hideSystemBars()
        }
        showControls = true
        scheduleHideControls()
    }

    fun hideControls() {
        hideControlsJob.value?.cancel()
        showControls = false
    }

    fun createMediaSource(url: String): MediaSource {
        return if (url.contains(".m3u8")) {
            val factory = DefaultHttpDataSource.Factory()
            HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        } else if (url.startsWith("/") || url.startsWith("file://")) {
            val fileUri = if (url.startsWith("file://")) url else Uri.fromFile(File(url)).toString()
            val factory = DefaultDataSource.Factory(context)
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(fileUri))
        } else {
            val factory = DefaultHttpDataSource.Factory()
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        }
    }

    fun setupPlayerListener(player: Player, onPlayerError: (String) -> Unit) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                when (state) {
                    Player.STATE_READY -> scheduleHideControls()
                    Player.STATE_ENDED -> {
                        showControls = true
                        hideControlsJob.value?.cancel()
                        isPlaying = false
                        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    hideControlsJob.value?.cancel()
                    showControls = true
                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorMsg = PlaybackErrorMessages[error.errorCode]
                    ?: "播放错误: ${error.message ?: PLAYBACK_ERROR_UNKNOWN}"
                onPlayerError(errorMsg)
                showControls = true
                hideControlsJob.value?.cancel()
            }
        })
    }

    LaunchedEffect(localVideoPath) {
        if (!localVideoPath.isNullOrBlank()) {
            val file = File(localVideoPath)
            if (file.exists()) {
                videoUrl = localVideoPath
                isLoading = false
                
                if (!localVideoId.isNullOrBlank()) {
                    coroutineScope.launch {
                        try {
                            val cachedDetails = VideoDetailsCacheManager.getVideoDetails(context, localVideoId)
                            if (cachedDetails != null) {
                                videoDetails = cachedDetails
                                cachedTitle = VideoDetailsCacheManager.getCachedTitle(context, localVideoId)
                                Log.d(TAG, "✅ 从缓存加载视频详情成功: ${cachedDetails.code}")
                            } else {
                                Log.d(TAG, "⚠️ 未找到视频详情缓存, videoId: $localVideoId")
                            }
                        } catch (e: Exception) {
                            Log.e("VideoPlayer", "❌ 加载缓存视频详情失败: ${e.message}")
                        }
                    }
                }
            } else {
                errorMessage = "视频文件不存在"
                isLoading = false
            }
        }
    }

    LaunchedEffect(video) {
        if (video == null) return@LaunchedEffect
        
        isLoading = true
        errorMessage = null
        isLoadingParts = true
        
        coroutineScope.launch {
            try {
                val videoUrlDeferred = async {
                    // 直接使用WebView拦截方式获取m3u8地址（最可靠的方式）
                    val cachedUrl = video.videoUrl
                    if (!cachedUrl.isNullOrBlank()) {
                        Log.d(TAG, "✅ 使用缓存视频URL: $cachedUrl")
                        cachedUrl
                    } else {
                        val webViewUrl = fetchM3u8UrlWithWebView(context, video.id)
                        if (!webViewUrl.isNullOrBlank()) {
                            Log.d(TAG, "✅ WebView拦截获取到URL: $webViewUrl")
                            webViewUrl
                        } else {
                            Log.e("VideoPlayer", "❌ 无法获取视频URL")
                            null
                        }
                    }
                }
                
                val detailsDeferred = async {
                    video.details ?: fetchVideoDetails(video.id)
                }
                
                val partsDeferred = async {
                    try {
                        val parts = fetchAllVideoParts(context, video.id)
                        Log.d(TAG, "✅ 获取到 ${parts.size} 个视频部分")
                        parts
                    } catch (e: Exception) {
                        Log.e("VideoPlayer", "❌ 获取视频部分失败: ${e.message}")
                        emptyList()
                    }
                }
                
                videoUrl = videoUrlDeferred.await()
                videoDetails = detailsDeferred.await()
                videoParts = partsDeferred.await()
                isLoadingParts = false
                
                val favouriteStatusDeferred = async {
                    try {
                        val realId = videoDetails?.realId ?: video.id
                        val status = fetchFavouriteStatus(realId)
                        Log.d(TAG, "✅ 收藏状态 (ID: $realId): $status")
                        status
                    } catch (e: Exception) {
                        Log.e("VideoPlayer", "❌ 获取收藏状态失败: ${e.message}")
                        false
                    }
                }
                
                isFavourite = favouriteStatusDeferred.await()

                val videoIdValue = video.id
                WatchHistoryManager.addWatchHistory(
                    videoId = videoIdValue,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl ?: "",
                    videoCode = videoDetails?.code ?: "",
                    releaseDate = videoDetails?.releaseDate ?: "",
                    duration = videoDetails?.duration ?: "",
                    performer = videoDetails?.performer ?: ""
                )

                if (videoUrl == null) {
                    Log.d(TAG, "⚠️ 主视频URL为空，尝试从视频部分获取")
                    val firstPartWithUrl = videoParts.firstOrNull { !it.url.isNullOrBlank() }
                    if (firstPartWithUrl != null) {
                        videoUrl = firstPartWithUrl.url
                        Log.d(TAG, "✅ 从视频部分获取到URL: $videoUrl")
                    } else {
                        errorMessage = "无法获取视频播放地址"
                        Log.e("VideoPlayer", "❌ 视频部分中也没有可用的URL")
                    }
                }
                
                val existingTask = downloadManager.getTaskByVideoId(video.id)
                if (existingTask != null) {
                    existingDownloadTask = existingTask
                    debugLog("✅ 找到现有下载任务: ${existingTask.id}, 进度: ${existingTask.progress}%")
                }
            } catch (e: Exception) {
                Log.e("VideoPlayer", "❌ 获取视频失败: ${e.message}")
                errorMessage = "获取视频失败: ${e.message}"
                isLoadingParts = false
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(existingDownloadTask?.id) {
        val taskId = existingDownloadTask?.id ?: return@LaunchedEffect

        debugLog("🔄 开始观察下载任务: $taskId")

        downloadManager.observeTaskById(taskId).collect { updatedTask ->
            if (updatedTask != null) {
                debugLog("📥 下载任务更新: 进度=${updatedTask.progress}%, 速度=${updatedTask.speedDisplay}")
                existingDownloadTask = updatedTask

                if (updatedTask.status == DownloadStatus.DOWNLOADING) {
                    isDownloading = true
                } else if (updatedTask.status == DownloadStatus.COMPLETED) {
                    isDownloading = false
                }
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

    fun toggleResizeMode() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            hideControlsJob.value?.cancel()
            setSystemUIVisibility(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        isLoading -> LoadingState(video?.title ?: "加载中...")
        errorMessage != null -> VideoErrorState(
            message = errorMessage!!,
            onBack = { onBack() },
            onRetry = {
                if (localVideoPath != null) {
                    errorMessage = null
                    isLoading = true
                    val file = File(localVideoPath)
                    if (file.exists()) {
                        videoUrl = localVideoPath
                        isLoading = false
                    } else {
                        errorMessage = "视频文件不存在"
                        isLoading = false
                    }
                } else if (video != null) {
                    errorMessage = null
                    isLoading = true
                    videoUrl = null
                    coroutineScope.launch {
                        try {
                            videoUrl = improvedFetchVideoUrl(context, video)
                            if (videoUrl == null) {
                                Log.d(TAG, "⚠️ 使用改进方法获取失败，尝试从视频部分获取")
                                val firstPartWithUrl = videoParts.firstOrNull { !it.url.isNullOrBlank() }
                                if (firstPartWithUrl != null) {
                                    videoUrl = firstPartWithUrl.url
                                    Log.d(TAG, "✅ 从视频部分获取到URL: $videoUrl")
                                } else {
                                    errorMessage = "无法获取视频播放地址"
                                    Log.e("VideoPlayer", "❌ 视频部分中也没有可用的URL")
                                }
                            } else {
                                Log.d(TAG, "✅ 使用改进方法成功获取URL: $videoUrl")
                            }
                        } catch (e: Exception) {
                            errorMessage = "获取视频失败: ${e.message}"
                            Log.e("VideoPlayer", "❌ 获取视频异常: ${e.message}")
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = "无法加载视频"
                    isLoading = false
                }
            }
        )
        videoUrl != null -> {
            Box(modifier = modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = isFullscreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
                    },
                    label = "fullscreenTransition"
                ) { fullscreen ->
                    if (fullscreen) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                        layoutParams = android.widget.FrameLayout.LayoutParams(
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        setPadding(0, 0, 0, 0)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { playerView ->
                                    val currentPlayer = exoPlayer
                                    if (currentPlayer != null) {
                                        if (playerView.player !== currentPlayer) {
                                            playerView.player = currentPlayer
                                        }
                                        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    } else {
                                        val newPlayer = ExoPlayer.Builder(context).build().apply {
                                            val source = createMediaSource(videoUrl!!)
                                            setMediaSource(source)
                                            prepare()
                                            playWhenReady = true
                                            setupPlayerListener(this) { error ->
                                                playerError = error
                                            }
                                        }
                                        exoPlayer = newPlayer
                                        playerView.player = newPlayer
                                        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            )

                            if (playerError != null) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(64.dp),
                                    shape = CircleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(onClick = {
                                            playerError = null
                                            isLoading = true
                                            videoUrl = null
                                            coroutineScope.launch {
                                                try {
                                                    if (localVideoPath != null) {
                                                        val file = File(localVideoPath)
                                                        if (file.exists()) {
                                                            videoUrl = localVideoPath
                                                            isLoading = false
                                                        } else {
                                                            errorMessage = "视频文件不存在"
                                                            isLoading = false
                                                        }
                                                    } else if (video != null) {
                                                        videoUrl = improvedFetchVideoUrl(context, video)
                                                        if (videoUrl == null) {
                                                            val firstPartWithUrl = videoParts.firstOrNull { !it.url.isNullOrBlank() }
                                                            if (firstPartWithUrl != null) {
                                                                videoUrl = firstPartWithUrl.url
                                                            } else {
                                                                errorMessage = "无法获取视频播放地址"
                                                                isLoading = false
                                                                return@launch
                                                            }
                                                        }
                                                        isLoading = false
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "获取视频失败: ${e.message}"
                                                    isLoading = false
                                                }
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "重新加载",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            PlayerControls(
                                exoPlayer = exoPlayer,
                                isFullscreen = isFullscreen,
                                isLocked = isLocked,
                                showSpeedSelector = showSpeedSelector,
                                currentSpeedIndex = currentSpeedIndex,
                                controlsAlpha = controlsAlpha,
                                showControls = showControls,
                                lastInteractionTime = lastInteractionTime,
                                isSeeking = isSeeking,
                                onBack = if (isFullscreen) {{ isFullscreen = false }} else onBack,
                                onFullscreen = {
                                    val willBeFullscreen = !isFullscreen
                                    setSystemUIVisibility(willBeFullscreen)
                                    isFullscreen = willBeFullscreen
                                },
                                onLock = { isLocked = !isLocked },
                                onSpeedChange = { index ->
                                    currentSpeedIndex = index
                                    exoPlayer?.setPlaybackSpeed(playbackSpeeds[index].speed)
                                },
                                onSpeedSelectorToggle = {
                                    showSpeedSelector = !showSpeedSelector
                                    updateInteractionTime()
                                    scheduleHideControls()
                                },
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
                                onShowControlsTemporarily = { toggleControls() },
                                onResizeModeChange = { toggleResizeMode() },
                                onIsSeekingChange = { isSeeking = it },
                                resizeMode = resizeMode,
                                videoTitle = cachedTitle ?: video?.title ?: "",
                                videoWidth = videoWidth,
                                videoHeight = videoHeight,
                                isFavourite = isFavourite,
                                onFavouriteToggle = {
                                    if (video == null) return@PlayerControls
                                    if (!userState.isLoggedIn) {
                                        Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                        return@PlayerControls
                                    }
                                    coroutineScope.launch {
                                        isTogglingFavourite = true
                                        try {
                                            val realId = videoDetails?.realId ?: video.id
                                            val success = toggleFavourite(realId, !isFavourite)
                                            if (success) {
                                                isFavourite = !isFavourite
                                                Toast.makeText(context, if (isFavourite) "已添加到收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "操作失败，请重试", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isTogglingFavourite = false
                                        }
                                    }
                                },
                                onInfoClick = { showInfoDialog = true }
                            )
                        }
                    } else {
                        if (isPlaying && !isLocked) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                useController = false
                                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                                )
                                                setPadding(0, 0, 0, 0)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = { playerView ->
                                            val currentPlayer = exoPlayer
                                            if (currentPlayer != null) {
                                                if (playerView.player !== currentPlayer) {
                                                    playerView.player = currentPlayer
                                                }
                                                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            } else {
                                                val newPlayer = ExoPlayer.Builder(context).build().apply {
                                                    val source = createMediaSource(videoUrl!!)
                                                    setMediaSource(source)
                                                    prepare()
                                                    playWhenReady = true
                                                    setupPlayerListener(this) { error ->
                                                        playerError = error
                                                    }
                                                }
                                                exoPlayer = newPlayer
                                                playerView.player = newPlayer
                                                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        }
                                    )

                                    if (playerError != null) {
                                        Card(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(64.dp),
                                            shape = CircleShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                IconButton(onClick = {
                                                    playerError = null
                                                    isLoading = true
                                                    videoUrl = null
                                                    coroutineScope.launch {
                                                        try {
                                                            if (localVideoPath != null) {
                                                                val file = File(localVideoPath)
                                                                if (file.exists()) {
                                                                    videoUrl = localVideoPath
                                                                    isLoading = false
                                                                } else {
                                                                    errorMessage = "视频文件不存在"
                                                                    isLoading = false
                                                                }
                                                            } else if (video != null) {
                                                                videoUrl = improvedFetchVideoUrl(context, video)
                                                                if (videoUrl == null) {
                                                                    val firstPartWithUrl = videoParts.firstOrNull { !it.url.isNullOrBlank() }
                                                                    if (firstPartWithUrl != null) {
                                                                        videoUrl = firstPartWithUrl.url
                                                                    } else {
                                                                        errorMessage = "无法获取视频播放地址"
                                                                        isLoading = false
                                                                        return@launch
                                                                    }
                                                                }
                                                                isLoading = false
                                                            }
                                                        } catch (e: Exception) {
                                                            errorMessage = "获取视频失败: ${e.message}"
                                                            isLoading = false
                                                        }
                                                    }
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "重新加载",
                                                        modifier = Modifier.size(32.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    PlayerControls(
                                        exoPlayer = exoPlayer,
                                        isFullscreen = isFullscreen,
                                        isLocked = isLocked,
                                        showSpeedSelector = showSpeedSelector,
                                        currentSpeedIndex = currentSpeedIndex,
                                        controlsAlpha = controlsAlpha,
                                        showControls = showControls,
                                        lastInteractionTime = lastInteractionTime,
                                        isSeeking = isSeeking,
                                        onBack = if (isFullscreen) {{ isFullscreen = false }} else onBack,
                                        onFullscreen = {
                                            val willBeFullscreen = !isFullscreen
                                            setSystemUIVisibility(willBeFullscreen)
                                            isFullscreen = willBeFullscreen
                                        },
                                        onLock = { isLocked = !isLocked },
                                        onSpeedChange = { index ->
                                            currentSpeedIndex = index
                                            exoPlayer?.setPlaybackSpeed(playbackSpeeds[index].speed)
                                        },
                                        onSpeedSelectorToggle = {
                                            showSpeedSelector = !showSpeedSelector
                                            updateInteractionTime()
                                            scheduleHideControls()
                                        },
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
                                        onShowControlsTemporarily = { toggleControls() },
                                        onResizeModeChange = { toggleResizeMode() },
                                        onIsSeekingChange = { isSeeking = it },
                                        resizeMode = resizeMode,
                                        videoTitle = cachedTitle ?: video?.title ?: "",
                                        videoWidth = videoWidth,
                                        videoHeight = videoHeight,
                                        isFavourite = isFavourite,
                                        onFavouriteToggle = {
                                            if (video == null) return@PlayerControls
                                            if (!userState.isLoggedIn) {
                                                Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                                return@PlayerControls
                                            }
                                            coroutineScope.launch {
                                                isTogglingFavourite = true
                                                try {
                                                    val realId = videoDetails?.realId ?: video.id
                                                    val success = toggleFavourite(realId, !isFavourite)
                                                    if (success) {
                                                        isFavourite = !isFavourite
                                                        Toast.makeText(context, if (isFavourite) "已添加到收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "操作失败，请重试", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isTogglingFavourite = false
                                                }
                                            }
                                        },
                                        onInfoClick = { showInfoDialog = true }
                                    )
                                }

                                if (videoDetails != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        VideoInfoSection(
                                            video = video ?: Video(
                                                 id = localVideoId ?: "",
                                                 title = cachedTitle ?: "",
                                                 duration = "",
                                                 thumbnailUrl = null,
                                                 videoUrl = null,
                                                 details = null
                                             ),
                                            videoDetails = videoDetails!!,
                                            cachedTitle = cachedTitle,
                                            existingDownloadTask = existingDownloadTask,
                                            downloadProgress = downloadProgress,
                                            videoUrl = videoUrl,
                                            downloadManager = downloadManager,
                                            context = context,
                                            coroutineScope = coroutineScope,
                                            onDownloadTaskUpdated = { task -> existingDownloadTask = task },
                                            onDownloadingStateChanged = { downloading -> isDownloading = downloading },
                                            onDownloadProgressChanged = { progress -> downloadProgress = progress },
                                            videoParts = videoParts,
                                            selectedPartIndex = selectedPartIndex,
                                            onPartSelected = { index ->
                                                selectedPartIndex = index
                                                val partUrl = videoParts.getOrNull(index)?.url
                                                android.util.Log.d(TAG, "点击部分${index + 1}，播放链接: $partUrl")
                                                if (!partUrl.isNullOrBlank()) {
                                                    coroutineScope.launch {
                                                        isLoading = true
                                                        videoUrl = partUrl
                                                        exoPlayer?.let { player ->
                                                            player.stop()
                                                            player.release()
                                                        }
                                                        exoPlayer = null
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            isFavourite = isFavourite,
                                            isTogglingFavourite = isTogglingFavourite,
                                            onFavouriteToggle = {
                                                if (video == null) return@VideoInfoSection
                                                if (!userState.isLoggedIn) {
                                                    Toast.makeText(
                                                        context,
                                                        "请先登录",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    return@VideoInfoSection
                                                }
                                                coroutineScope.launch {
                                                    isTogglingFavourite = true
                                                    try {
                                                        val realId = videoDetails?.realId ?: video.id
                                                        val success = toggleFavourite(realId, !isFavourite)
                                                        if (success) {
                                                            isFavourite = !isFavourite
                                                            Toast.makeText(
                                                                context,
                                                                if (isFavourite) "已添加到收藏" else "已取消收藏",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "操作失败，请重试",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "操作失败: ${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } finally {
                                                        isTogglingFavourite = false
                                                    }
                                                }
                                            },
                                            isLoggedIn = userState.isLoggedIn,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                useController = false
                                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                                )
                                                setPadding(0, 0, 0, 0)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = { playerView ->
                                            val currentPlayer = exoPlayer
                                            if (currentPlayer != null) {
                                                if (playerView.player !== currentPlayer) {
                                                    playerView.player = currentPlayer
                                                }
                                                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            } else {
                                                val newPlayer = ExoPlayer.Builder(context).build().apply {
                                                    val source = createMediaSource(videoUrl!!)
                                                    setMediaSource(source)
                                                    prepare()
                                                    playWhenReady = true
                                                    setupPlayerListener(this) { error ->
                                                        playerError = error
                                                    }
                                                }
                                                exoPlayer = newPlayer
                                                playerView.player = newPlayer
                                                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        }
                                    )

                                    if (playerError != null) {
                                        Card(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(64.dp),
                                            shape = CircleShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                IconButton(onClick = {
                                                    playerError = null
                                                    isLoading = true
                                                    videoUrl = null
                                                    coroutineScope.launch {
                                                        try {
                                                            if (localVideoPath != null) {
                                                                val file = File(localVideoPath)
                                                                if (file.exists()) {
                                                                    videoUrl = localVideoPath
                                                                    isLoading = false
                                                                } else {
                                                                    errorMessage = "视频文件不存在"
                                                                    isLoading = false
                                                                }
                                                            } else if (video != null) {
                                                                videoUrl = improvedFetchVideoUrl(context, video)
                                                                if (videoUrl == null) {
                                                                    val firstPartWithUrl = videoParts.firstOrNull { !it.url.isNullOrBlank() }
                                                                    if (firstPartWithUrl != null) {
                                                                        videoUrl = firstPartWithUrl.url
                                                                    } else {
                                                                        errorMessage = "无法获取视频播放地址"
                                                                        isLoading = false
                                                                        return@launch
                                                                    }
                                                                }
                                                                isLoading = false
                                                            }
                                                        } catch (e: Exception) {
                                                            errorMessage = "获取视频失败: ${e.message}"
                                                            isLoading = false
                                                        }
                                                    }
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "重新加载",
                                                        modifier = Modifier.size(32.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    PlayerControls(
                                        exoPlayer = exoPlayer,
                                        isFullscreen = isFullscreen,
                                        isLocked = isLocked,
                                        showSpeedSelector = showSpeedSelector,
                                        currentSpeedIndex = currentSpeedIndex,
                                        controlsAlpha = controlsAlpha,
                                        showControls = showControls,
                                        lastInteractionTime = lastInteractionTime,
                                        isSeeking = isSeeking,
                                        onBack = if (isFullscreen) {{ isFullscreen = false }} else onBack,
                                        onFullscreen = {
                                            val willBeFullscreen = !isFullscreen
                                            setSystemUIVisibility(willBeFullscreen)
                                            isFullscreen = willBeFullscreen
                                        },
                                        onLock = { isLocked = !isLocked },
                                        onSpeedChange = { index ->
                                            currentSpeedIndex = index
                                            exoPlayer?.setPlaybackSpeed(playbackSpeeds[index].speed)
                                        },
                                        onSpeedSelectorToggle = {
                                            showSpeedSelector = !showSpeedSelector
                                            updateInteractionTime()
                                            scheduleHideControls()
                                        },
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
                                        onShowControlsTemporarily = { toggleControls() },
                                        onResizeModeChange = { toggleResizeMode() },
                                        onIsSeekingChange = { isSeeking = it },
                                        resizeMode = resizeMode,
                                        videoTitle = cachedTitle ?: video?.title ?: "",
                                        videoWidth = videoWidth,
                                        videoHeight = videoHeight,
                                        isFavourite = isFavourite,
                                        onFavouriteToggle = {
                                            if (video == null) return@PlayerControls
                                            if (!userState.isLoggedIn) {
                                                Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                                return@PlayerControls
                                            }
                                            coroutineScope.launch {
                                                isTogglingFavourite = true
                                                try {
                                                    val realId = videoDetails?.realId ?: video.id
                                                    val success = toggleFavourite(realId, !isFavourite)
                                                    if (success) {
                                                        isFavourite = !isFavourite
                                                        Toast.makeText(context, if (isFavourite) "已添加到收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "操作失败，请重试", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isTogglingFavourite = false
                                                }
                                            }
                                        },
                                        onInfoClick = { showInfoDialog = true }
                                    )
                                }

                                if (videoDetails != null) {
                                    VideoInfoSection(
                                        video = video ?: Video(
                                            id = localVideoId ?: "",
                                            title = cachedTitle ?: "",
                                            duration = "",
                                            thumbnailUrl = null,
                                            videoUrl = null,
                                            details = null
                                        ),
                                        videoDetails = videoDetails!!,
                                        cachedTitle = cachedTitle,
                                        existingDownloadTask = existingDownloadTask,
                                        downloadProgress = downloadProgress,
                                        videoUrl = videoUrl,
                                        downloadManager = downloadManager,
                                        context = context,
                                        coroutineScope = coroutineScope,
                                        onDownloadTaskUpdated = { task -> existingDownloadTask = task },
                                        onDownloadingStateChanged = { downloading -> isDownloading = downloading },
                                        onDownloadProgressChanged = { progress -> downloadProgress = progress },
                                        videoParts = videoParts,
                                        selectedPartIndex = selectedPartIndex,
                                        onPartSelected = { index ->
                                            selectedPartIndex = index
                                            val partUrl = videoParts.getOrNull(index)?.url
                                            android.util.Log.d(TAG, "点击部分${index + 1}，播放链接: $partUrl")
                                            if (!partUrl.isNullOrBlank()) {
                                                coroutineScope.launch {
                                                    isLoading = true
                                                    videoUrl = partUrl
                                                    exoPlayer?.let { player ->
                                                        player.stop()
                                                        player.release()
                                                    }
                                                    exoPlayer = null
                                                    isLoading = false
                                                }
                                            }
                                        },
                                        isFavourite = isFavourite,
                                        isTogglingFavourite = isTogglingFavourite,
                                        onFavouriteToggle = {
                                            if (video == null) return@VideoInfoSection
                                            if (!userState.isLoggedIn) {
                                                Toast.makeText(
                                                    context,
                                                    "请先登录",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@VideoInfoSection
                                            }
                                            coroutineScope.launch {
                                                isTogglingFavourite = true
                                                try {
                                                    val realId = videoDetails?.realId ?: video.id
                                                    val success = toggleFavourite(realId, !isFavourite)
                                                    if (success) {
                                                        isFavourite = !isFavourite
                                                        Toast.makeText(
                                                            context,
                                                            if (isFavourite) "已添加到收藏" else "已取消收藏",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "操作失败，请重试",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "操作失败: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    isTogglingFavourite = false
                                                }
                                            }
                                        },
                                        isLoggedIn = userState.isLoggedIn,
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

    VideoInfoDialog(
        showDialog = showInfoDialog,
        onDismiss = { showInfoDialog = false },
        videoTitle = videoTitle,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        videoUrl = videoUrl,
        videoParts = videoParts
    )
}

@Composable
private fun VideoInfoDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    videoTitle: String,
    videoWidth: Int,
    videoHeight: Int,
    videoUrl: String?,
    videoParts: List<com.android123av.app.models.VideoPart>
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("视频标题", videoTitle)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "标题已复制", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                ) {
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (videoWidth > 0 && videoHeight > 0) {
                        Text(
                            text = "分辨率: $videoWidth x $videoHeight",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!videoUrl.isNullOrBlank()) {
                        Text(
                            text = "播放链接:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ClickableUrlText(
                            url = videoUrl,
                            onUrlClick = {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))
                                context.startActivity(intent)
                            }
                        )
                    }

                    if (videoParts.size > 1) {
                        Text(
                            text = "部分视频:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        videoParts.forEachIndexed { index, part ->
                            if (!part.url.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "部分${index + 1}: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMedium)
                                    )
                                    ClickableUrlText(
                                        url = part.url,
                                        onUrlClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(part.url))
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun ClickableUrlText(
    url: String,
    onUrlClick: () -> Unit
) {
    Text(
        text = url,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUrlClick),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaHigh),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(horizontal = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaLow)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMedium),
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaLow)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMediumHigh),
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
    isSeeking: Boolean,
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
    onIsSeekingChange: (Boolean) -> Unit,
    resizeMode: Int,
    videoTitle: String = "",
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    isFavourite: Boolean = false,
    onFavouriteToggle: () -> Unit = {},
    onInfoClick: () -> Unit = {}
) {
    val context = LocalContext.current

    var currentPosition by remember(exoPlayer) { mutableLongStateOf(exoPlayer?.currentPosition ?: 0L) }
    var bufferedPosition by remember(exoPlayer) { mutableLongStateOf(exoPlayer?.bufferedPosition ?: 0L) }
    var duration by remember(exoPlayer) { mutableLongStateOf(exoPlayer?.duration?.takeIf { it > 0 } ?: 0L) }
    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer?.isPlaying ?: false) }
    var playbackState by remember(exoPlayer) { mutableIntStateOf(exoPlayer?.playbackState ?: Player.STATE_IDLE) }
    var lastBufferedPosition by remember(exoPlayer) { mutableLongStateOf(exoPlayer?.bufferedPosition ?: 0L) }
    var bufferSpeed by remember { mutableStateOf("0.0") }
    
    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            exoPlayer?.let { player ->
                currentPosition = player.currentPosition
                bufferedPosition = player.bufferedPosition
                duration = player.duration.takeIf { it > 0 } ?: 0L
                isPlaying = player.isPlaying
                playbackState = player.playbackState

                val positionDelta = bufferedPosition - lastBufferedPosition
                lastBufferedPosition = bufferedPosition
                if (positionDelta > 0 && duration > 0) {
                    val estimatedBitrate = when {
                        videoHeight >= 2160 -> 16_000_000L
                        videoHeight >= 1080 -> 8_000_000L
                        videoHeight >= 720 -> 5_000_000L
                        videoHeight >= 480 -> 2_500_000L
                        videoHeight >= 360 -> 1_000_000L
                        else -> 500_000L
                    }
                    val bytesPerMs = estimatedBitrate / 1000.0 / 8.0
                    val speedMBPerSecond = (positionDelta * bytesPerMs * 5) / (1024.0 * 1024.0)
                    bufferSpeed = String.format("%.1f", speedMBPerSecond.coerceAtLeast(0.1))
                } else {
                    bufferSpeed = "0.0"
                }
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
                        bufferedPosition = bufferedPosition,
                        duration = duration,
                        progress = progress,
                        isPlaying = isPlaying,
                        controlsAlpha = controlsAlpha,
                        isFullscreen = isFullscreen,
                        bufferSpeed = bufferSpeed,
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
                        onSeekStart = {
                            onIsSeekingChange(true)
                            onUpdateInteractionTime()
                        },
                        onSeekStop = { onIsSeekingChange(false) },
                        onTap = onShowControlsTemporarily,
                        onDoubleTap = onPlayPause,
                        onLongPress = { isPressed ->
                            exoPlayer?.setPlaybackSpeed(if (isPressed) 2.0f else 1.0f)
                        },
                        onHideControls = onHideControlsNow,
                        onUpdateInteractionTime = onUpdateInteractionTime,
                        onResizeModeChange = onResizeModeChange,
                        resizeMode = resizeMode,
                        videoTitle = videoTitle,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        isFavourite = isFavourite,
                        onFavouriteToggle = onFavouriteToggle,
                        onCastClick = { /* TODO: 投屏功能 */ },
                        onInfoClick = onInfoClick,
                        onSettingsClick = { /* TODO: 设置功能 */ }
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
    bufferedPosition: Long,
    duration: Long,
    progress: Float,
    isPlaying: Boolean,
    controlsAlpha: Float,
    isFullscreen: Boolean,
    bufferSpeed: String = "0.0",
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onSpeedSelectorToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekStop: () -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: (Boolean) -> Unit,
    onHideControls: () -> Unit,
    onUpdateInteractionTime: () -> Unit,
    onResizeModeChange: () -> Unit,
    resizeMode: Int,
    videoTitle: String = "",
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    isFavourite: Boolean = false,
    onFavouriteToggle: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var isLongPressed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        TapGestureLayer(
            showControls = showControls,
            onTap = onTap,
            onDoubleTap = {
                onUpdateInteractionTime()
                onDoubleTap()
            },
            onLongPress = { isPressed ->
                isLongPressed = isPressed
                onLongPress(isPressed)
            }
        )

        // 长按快进提示 - 顶部小胶囊样式
        AnimatedVisibility(
            visible = isLongPressed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(200)
            ) + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(150)
            ) + slideOutHorizontally(targetOffsetX = { it / 2 })
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = OverlayDark,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "2X",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 控件可见性动画：使用更现代的淡入淡出和轻微缩放
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.98f, animationSpec = tween(220))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变层
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = AlphaMedium),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 底部渐变层
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    OverlayMedium
                                )
                            )
                        )
                )

                // 顶栏：标题和分辨率信息，右侧功能按钮
                TopBar(
                    isFullscreen = isFullscreen,
                    onBack = onBack,
                    onFullscreen = onFullscreen,
                    onLock = onLock,
                    onResizeModeChange = onResizeModeChange,
                    resizeMode = resizeMode,
                    currentSpeedIndex = currentSpeedIndex,
                    showSpeedSelector = showSpeedSelector,
                    onSpeedSelectorToggle = onSpeedSelectorToggle,
                    onSpeedChange = onSpeedChange,
                    videoTitle = videoTitle,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    bufferSpeed = bufferSpeed,
                    isFavourite = isFavourite,
                    onFavouriteToggle = onFavouriteToggle,
                    onCastClick = onCastClick,
                    onInfoClick = onInfoClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // 中央控制：播放/暂停按钮
                CenterControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause
                )

                // 底部进度条：时间、进度条、总时长、全屏按钮
                BottomBar(
                    currentPosition = currentPosition,
                    bufferedPosition = bufferedPosition,
                    duration = duration,
                    progress = progress,
                    onSeek = onSeek,
                    onSeekStart = onSeekStart,
                    onSeekStop = onSeekStop,
                    onFullscreen = onFullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun TapGestureLayer(
    showControls: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLongPressHandled by remember { mutableStateOf(false) }
    var wasLongPressActive by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isLongPressHandled) {
                            onTap()
                        }
                        isLongPressHandled = false
                    },
                    onDoubleTap = {
                        isLongPressHandled = false
                        onDoubleTap()
                    },
                    onPress = {
                        isLongPressHandled = false
                        wasLongPressActive = false
                        val longPressJob = scope.launch {
                            delay(500)
                            isLongPressHandled = true
                            wasLongPressActive = true
                            onLongPress(true)
                        }
                        try {
                            awaitRelease()
                        } finally {
                            longPressJob.cancel()
                            if (wasLongPressActive) {
                                wasLongPressActive = false
                                onLongPress(false)
                            }
                        }
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
    resizeMode: Int,
    currentSpeedIndex: Int = 2,
    showSpeedSelector: Boolean = false,
    onSpeedSelectorToggle: () -> Unit = {},
    onSpeedChange: (Int) -> Unit = {},
    videoTitle: String = "",
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    bufferSpeed: String = "0.0",
    isFavourite: Boolean = false,
    onFavouriteToggle: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：返回按钮和标题（同一行）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 返回按钮
            IconButton(
                onClick = {
                    HapticUtils.vibrateClick(context)
                    onBack()
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp)
                )
            }

            // 标题和分辨率信息
            if (videoTitle.isNotBlank()) {
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (videoWidth > 0 && videoHeight > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$videoWidth x $videoHeight",
                                color = Color.White.copy(alpha = AlphaMedium),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${bufferSpeed} M/s",
                                color = Color.White.copy(alpha = AlphaMedium),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 右侧：功能按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 倍速按钮
            SpeedButton(
                currentSpeedIndex = currentSpeedIndex,
                showSpeedSelector = showSpeedSelector,
                onSpeedSelectorToggle = {
                    HapticUtils.vibrateClick(context)
                    onSpeedSelectorToggle()
                },
                onSpeedChange = { index ->
                    HapticUtils.vibrateClick(context)
                    onSpeedChange(index)
                }
            )

            // 信息按钮
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "信息",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        HapticUtils.vibrateClick(context)
                        onInfoClick()
                    }
            )

            // 设置按钮
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        HapticUtils.vibrateClick(context)
                        onSettingsClick()
                    }
            )
        }
    }
}

@Composable
private fun SpeedButton(
    currentSpeedIndex: Int,
    showSpeedSelector: Boolean,
    onSpeedSelectorToggle: () -> Unit,
    onSpeedChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var isLongPressed by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = CircleShape,
            color = OverlayLight,
            contentColor = Color.White,
            tonalElevation = 2.dp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSpeedSelectorToggle() },
                    onLongPress = {
                        HapticUtils.vibrateLongPress(context)
                        isLongPressed = true
                    }
                )
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playbackSpeeds[currentSpeedIndex].label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Icon(
                    imageVector = if (showSpeedSelector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "播放速度",
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showSpeedSelector,
            onDismissRequest = {
                onSpeedSelectorToggle()
                isLongPressed = false
            },
            offset = androidx.compose.ui.unit.DpOffset(x = (-8).dp, y = 4.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = Color.Black.copy(alpha = AlphaHigh)
        ) {
            playbackSpeeds.forEachIndexed { index, speed ->
                val isSelected = currentSpeedIndex == index
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = speed.label,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = AlphaHigh)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        HapticUtils.vibrateClick(context)
                        onSpeedChange(index)
                        onSpeedSelectorToggle()
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = MenuDefaults.itemColors(
                        textColor = Color.White.copy(alpha = AlphaHigh),
                        disabledTextColor = Color.White.copy(alpha = AlphaMediumLow),
                        leadingIconColor = Color.White.copy(alpha = AlphaHigh),
                        trailingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val animatedScale by animateFloatAsState(
        targetValue = if (isPlaying) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playPauseScale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.85f else 1f,
        animationSpec = tween(150),
        label = "playPauseAlpha"
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 20.dp else 32.dp,
        animationSpec = tween(200),
        label = "playPauseCornerRadius"
    )

    val pulseScale by rememberInfiniteTransition(label = "pulseTransition").animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = animatedScale * pulseScale
                scaleY = animatedScale * pulseScale
                this.alpha = animatedAlpha
            }
            .clip(RoundedCornerShape(animatedCornerRadius))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isPlaying) 0.15f else 0.1f),
                        Color.Transparent
                    )
                )
            )
            .clickable {
                HapticUtils.vibrateClick(context)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(36.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(36.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = 1.1f
                        scaleY = 1.1f
                    }
            )
        }
    }
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.36f))
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
    bufferedPosition: Long,
    duration: Long,
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekStop: () -> Unit,
    onFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableLongStateOf(0L) }
    var previewProgress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val displayProgress = if (isDragging) previewProgress else progress

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 当前时间
            Text(
                text = formatTime(currentPosition),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 进度条
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                val density = LocalDensity.current
                val trackWidthPx = with(density) { maxWidth.toPx() }

                // 点击和拖动区域
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val p = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                onSeekStart()
                                onSeek(p)
                                onSeekStop()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    showPreview = true
                                    onSeekStart()
                                    val calculatedProgress = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                    previewProgress = calculatedProgress
                                    previewPosition = (calculatedProgress * duration).toLong()
                                    },
                                    onDrag = { change, _ ->
                                        val calculatedProgress = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                        previewProgress = calculatedProgress
                                        previewPosition = (calculatedProgress * duration).toLong()
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        onSeek(previewProgress)
                                        showPreview = false
                                        onSeekStop()
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        showPreview = false
                                        onSeekStop()
                                    }
                                )
                            }
                    ) {
                        val activeColor = MaterialTheme.colorScheme.primary // 主题色滑块
                        val trackBackgroundColor = Color.White.copy(alpha = AlphaVeryLow)
                        val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val trackHeight = 4.dp.toPx()
                            val centerY = size.height / 2
                            val activeWidth = size.width * displayProgress
                            val bufferedWidth = size.width * bufferedProgress

                            // 绘制未播放部分背景
                            drawRoundRect(
                                color = trackBackgroundColor,
                                topLeft = Offset(0f, centerY - trackHeight / 2),
                                size = Size(size.width, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
                            )

                            // 绘制缓冲部分
                            if (bufferedWidth > 0) {
                                val bufferTrackHeight = 2.dp.toPx()
                                val bufferCenterY = centerY
                                drawRoundRect(
                                    color = Color.White.copy(alpha = AlphaMedium),
                                    topLeft = Offset(0f, bufferCenterY - bufferTrackHeight / 2),
                                    size = Size(bufferedWidth, bufferTrackHeight),
                                    cornerRadius = CornerRadius(bufferTrackHeight / 2, bufferTrackHeight / 2)
                                )
                            }

                            // 绘制已播放部分（主题色）
                            if (activeWidth > 0) {
                                drawRoundRect(
                                    color = activeColor,
                                    topLeft = Offset(0f, centerY - trackHeight / 2),
                                    size = Size(activeWidth, trackHeight),
                                    cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
                                )
                            }
                        }

                        // 黄色滑块圆点
                        val thumbSize = 20.dp
                        val thumbDp = with(density) { (displayProgress * trackWidthPx).toDp() }
                        val thumbScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.15f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "thumbScale"
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = thumbDp - thumbSize / 2)
                                .size(thumbSize)
                                .graphicsLayer {
                                    scaleX = thumbScale
                                    scaleY = thumbScale
                                }
                                .clip(CircleShape)
                                .background(activeColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 总时长
                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 全屏按钮
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "全屏",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onFullscreen() }
                )
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
            inactiveTrackColor = Color.White.copy(alpha = AlphaVeryLow)
        )
    )
}

@Composable
private fun LockedOverlay(
    onUnlock: () -> Unit
) {
    var showUnlockIcon by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        showUnlockIcon = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!showUnlockIcon) {
                        showUnlockIcon = true
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(3000)
                            showUnlockIcon = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showUnlockIcon,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = AlphaLow))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onUnlock() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "已锁定，点击解锁",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
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
            .background(OverlayVeryLight),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlphaHigh)
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
    cachedTitle: String?,
    existingDownloadTask: DownloadTask?,
    downloadProgress: Int,
    videoUrl: String?,
    downloadManager: M3U8DownloadManager,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDownloadTaskUpdated: (DownloadTask?) -> Unit,
    onDownloadingStateChanged: (Boolean) -> Unit,
    onDownloadProgressChanged: (Int) -> Unit,
    videoParts: List<com.android123av.app.models.VideoPart>,
    selectedPartIndex: Int,
    onPartSelected: (Int) -> Unit,
    isFavourite: Boolean,
    isTogglingFavourite: Boolean,
    onFavouriteToggle: () -> Unit,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    var isTitleExpanded by remember { mutableStateOf(false) }
    val effectiveTitle = cachedTitle ?: video.title
    val downloadStatus = existingDownloadTask?.status
    val isDownloadActive = downloadStatus == DownloadStatus.DOWNLOADING

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = effectiveTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (effectiveTitle.length > 50) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { isTitleExpanded = !isTitleExpanded },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (isTitleExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isTitleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DetailItemContent(
                        icon = Icons.Default.Timer,
                        label = "时长",
                        value = videoDetails.duration.ifBlank { "--" }
                    )
                }
                if (videoDetails.performer.isNotBlank()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DetailItemContent(
                            icon = Icons.Default.Person,
                            label = "演员",
                            value = videoDetails.performer,
                            onClick = if (videoDetails.performerHref.isNotBlank()) {
                                {
                                    navigateToCategory(
                                        context = context,
                                        href = videoDetails.performerHref,
                                        title = videoDetails.performer
                                    )
                                }
                            } else null
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DetailItemContent(
                        icon = Icons.Default.Business,
                        label = "制作",
                        value = videoDetails.maker.ifBlank { "未知" },
                        onClick = if (videoDetails.makerHref.isNotBlank()) {
                            {
                                navigateToCategory(
                                    context = context,
                                    href = videoDetails.makerHref,
                                    title = videoDetails.maker
                                )
                            }
                        } else null
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DetailItemContent(
                        icon = Icons.Default.Favorite,
                        label = "热度",
                        value = formatCount(videoDetails.favouriteCount)
                    )
                }
            }
        }

        if (videoDetails.releaseDate.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaLow)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "发布于 ${videoDetails.releaseDate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (videoParts.size > 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "视频部分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        videoParts.forEachIndexed { index, part ->
                            FilterChip(
                                selected = selectedPartIndex == index,
                                onClick = { onPartSelected(index) },
                                label = {
                                    Text(
                                        text = part.name,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = if (selectedPartIndex == index) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (videoDetails.genres.isNotEmpty()) {
                InfoSection(
                    title = "类型",
                    items = videoDetails.getGenresWithHrefs(),
                    icon = Icons.Default.Category
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (videoDetails.tags.isNotEmpty()) {
                InfoSection(
                    title = "标签",
                    items = videoDetails.getTagsWithHrefs(),
                    icon = Icons.Default.LocalOffer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (cachedTitle == null) {
                DownloadStatusCard(
                    existingDownloadTask = existingDownloadTask,
                    downloadProgress = downloadProgress,
                    isDownloadActive = isDownloadActive,
                    downloadManager = downloadManager,
                    context = context,
                    coroutineScope = coroutineScope,
                    onDownloadTaskUpdated = onDownloadTaskUpdated,
                    onDownloadingStateChanged = onDownloadingStateChanged,
                    onDownloadProgressChanged = onDownloadProgressChanged,
                    video = video,
                    videoUrl = videoUrl,
                    onNavigateToDownloads = {
                        val intent = Intent(context, DownloadsActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            HapticUtils.vibrateClick(context)
                            onFavouriteToggle()
                        },
                        enabled = !isTogglingFavourite,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isFavourite) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isFavourite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavourite) "已收藏" else "收藏",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isFavourite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    val buttonText = when (existingDownloadTask?.status) {
                        DownloadStatus.COMPLETED -> "已下载"
                        DownloadStatus.DOWNLOADING -> "下载中 ${existingDownloadTask?.progressDisplay ?: "0.00%"}"
                        DownloadStatus.PAUSED -> "继续下载"
                        DownloadStatus.FAILED -> "重试下载"
                        else -> "下载"
                    }

                    val buttonIcon = when (existingDownloadTask?.status) {
                        DownloadStatus.COMPLETED -> Icons.Default.DownloadDone
                        DownloadStatus.DOWNLOADING -> Icons.Default.Download
                        else -> Icons.Default.Download
                    }

                    val isButtonEnabled = existingDownloadTask?.status != DownloadStatus.DOWNLOADING

                    OutlinedButton(
                        onClick = {
                            HapticUtils.vibrateClick(context)
                            handleDownload(
                                video = video,
                                videoUrl = videoUrl,
                                downloadManager = downloadManager,
                                context = context,
                                existingTask = existingDownloadTask,
                                onTaskCreated = { taskId ->
                                    coroutineScope.launch {
                                        val task = downloadManager.getTaskById(taskId)
                                        onDownloadTaskUpdated(task)
                                    }
                                },
                                onDownloading = { downloading, progress ->
                                    onDownloadingStateChanged(downloading)
                                    onDownloadProgressChanged(progress)
                                },
                                coroutineScope = coroutineScope,
                                isLoggedIn = isLoggedIn
                            )
                        },
                        enabled = isButtonEnabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusCard(
    existingDownloadTask: DownloadTask?,
    downloadProgress: Int,
    isDownloadActive: Boolean,
    downloadManager: M3U8DownloadManager,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDownloadTaskUpdated: (DownloadTask?) -> Unit,
    onDownloadingStateChanged: (Boolean) -> Unit,
    onDownloadProgressChanged: (Int) -> Unit,
    video: Video,
    videoUrl: String?,
    onNavigateToDownloads: () -> Unit
) {
    val downloadStatus = existingDownloadTask?.status
    val preciseProgress = existingDownloadTask?.progressPercent ?: 0f
    val downloadSpeed = existingDownloadTask?.downloadSpeed ?: 0L
    val speedDisplay = existingDownloadTask?.speedDisplay ?: "0 B/s"
    val downloadedBytes = existingDownloadTask?.downloadedBytes ?: 0L
    val totalBytes = existingDownloadTask?.totalBytes ?: 0L
    val bytesDisplay = DownloadTask.formatBytes(downloadedBytes)
    val totalBytesDisplay = DownloadTask.formatBytes(totalBytes)

    val statusInfo = when (downloadStatus) {
        DownloadStatus.COMPLETED -> DownloadStatusInfo(
            icon = Icons.Default.CheckCircle,
            iconColor = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            statusText = "已下载完成",
            statusDescription = "视频已保存在本地",
            showProgress = false,
            showSpeed = false
        )
        DownloadStatus.DOWNLOADING -> DownloadStatusInfo(
            icon = Icons.Default.CloudDownload,
            iconColor = MaterialTheme.colorScheme.tertiary,
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            statusText = "下载中 ${existingDownloadTask?.progressDisplay ?: "0.00%"}",
            statusDescription = "$bytesDisplay / $totalBytesDisplay",
            showProgress = true,
            showSpeed = true,
            speedText = speedDisplay
        )
        DownloadStatus.PAUSED -> DownloadStatusInfo(
            icon = Icons.Default.PauseCircle,
            iconColor = MaterialTheme.colorScheme.secondary,
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            statusText = "下载已暂停",
            statusDescription = "已下载 ${existingDownloadTask?.progressDisplay ?: "0.00%"}",
            showProgress = false,
            showSpeed = false
        )
        DownloadStatus.FAILED -> DownloadStatusInfo(
            icon = Icons.Default.Error,
            iconColor = MaterialTheme.colorScheme.error,
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            statusText = "下载失败",
            statusDescription = existingDownloadTask?.errorMessage ?: "点击重试下载",
            showProgress = false,
            showSpeed = false
        )
        DownloadStatus.PENDING -> DownloadStatusInfo(
            icon = Icons.Default.Schedule,
            iconColor = MaterialTheme.colorScheme.outline,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            statusText = "等待下载",
            statusDescription = "准备中...",
            showProgress = false,
            showSpeed = false
        )
        else -> null
    }

    if (statusInfo != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val intent = Intent(context, DownloadsActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = statusInfo.backgroundColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusInfo.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = statusInfo.iconColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusInfo.statusText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = statusInfo.statusDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMedium)
                        )
                        if (statusInfo.showSpeed && statusInfo.speedText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = statusInfo.iconColor.copy(alpha = AlphaMediumHigh)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusInfo.speedText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusInfo.iconColor.copy(alpha = AlphaMediumHigh),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (statusInfo.showProgress) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { (preciseProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 4.dp,
                                color = statusInfo.iconColor,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = AlphaVeryLow)
                            )
                            Text(
                                text = String.format("%.1f", preciseProgress),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusInfo.iconColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (downloadStatus == DownloadStatus.COMPLETED) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (statusInfo.showProgress) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (preciseProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusInfo.iconColor,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = AlphaVeryLow)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${existingDownloadTask?.progressDisplay ?: "0.00%"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMedium),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = speedDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusInfo.iconColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (downloadStatus == DownloadStatus.PAUSED || downloadStatus == DownloadStatus.FAILED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            handleDownload(
                                video = video,
                                videoUrl = videoUrl,
                                downloadManager = downloadManager,
                                context = context,
                                existingTask = existingDownloadTask,
                                onTaskCreated = { taskId ->
                                    coroutineScope.launch {
                                        val task = downloadManager.getTaskById(taskId)
                                        onDownloadTaskUpdated(task)
                                    }
                                },
                                onDownloading = { downloading, progress ->
                                    onDownloadingStateChanged(downloading)
                                    onDownloadProgressChanged(progress)
                                },
                                coroutineScope = coroutineScope,
                                isLoggedIn = isLoggedIn
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (downloadStatus == DownloadStatus.FAILED) "重试" else "继续",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class DownloadStatusInfo(
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundColor: Color,
    val statusText: String,
    val statusDescription: String,
    val showProgress: Boolean = false,
    val showSpeed: Boolean = false,
    val speedText: String? = null
)

@Composable
private fun DetailItemContent(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoSection(
    title: String,
    items: List<Pair<String, String>>,
    icon: ImageVector,
    context: android.content.Context = LocalContext.current
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { (itemText, itemHref) ->
                SuggestionChip(
                    onClick = {
                        if (itemHref.isNotBlank()) {
                            navigateToCategory(
                                context = context,
                                href = itemHref,
                                title = itemText
                            )
                        }
                    },
                    label = {
                        Text(
                            text = itemText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}

private fun handleDownload(
    video: Video,
    videoUrl: String?,
    downloadManager: M3U8DownloadManager,
    context: android.content.Context,
    existingTask: DownloadTask?,
    onTaskCreated: (Long) -> Unit,
    onDownloading: (Boolean, Int) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isLoggedIn: Boolean
) {
    if (!isLoggedIn) {
        Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
        return
    }
    
    val url = videoUrl ?: video.videoUrl
    
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "无法获取视频地址", Toast.LENGTH_SHORT).show()
        return
    }
    
    when (existingTask?.status) {
        DownloadStatus.COMPLETED -> {
            Toast.makeText(context, "视频已下载", Toast.LENGTH_SHORT).show()
        }
        DownloadStatus.DOWNLOADING -> {
            Toast.makeText(context, "下载进行中...", Toast.LENGTH_SHORT).show()
        }
        DownloadStatus.PAUSED -> {
            coroutineScope.launch {
                downloadManager.resumeDownload(existingTask.id)
                onDownloading(true, existingTask.progress.toInt())
                Toast.makeText(context, "继续下载...", Toast.LENGTH_SHORT).show()
            }
        }
        DownloadStatus.FAILED -> {
            coroutineScope.launch {
                downloadManager.resumeDownload(existingTask.id)
                onDownloading(true, existingTask.progress.toInt())
                Toast.makeText(context, "重试下载...", Toast.LENGTH_SHORT).show()
            }
        }
        else -> {
            coroutineScope.launch {
                try {
                    val downloadDir = File(DownloadPathManager.getCurrentPath(context))
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    
                    val sanitizedTitle = video.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
                        .take(50)
                    val taskDir = File(downloadDir, "${video.id}_$sanitizedTitle")
                    if (!taskDir.exists()) {
                        taskDir.mkdirs()
                    }
                    
                    val task = DownloadTask(
                        videoId = video.id,
                        title = video.title,
                        videoUrl = video.videoUrl ?: "",
                        thumbnailUrl = video.thumbnailUrl,
                        downloadUrl = url,
                        savePath = taskDir.absolutePath,
                        status = DownloadStatus.PENDING
                    )
                    
                    val taskId = downloadManager.insertTask(task)
                    onTaskCreated(taskId)
                    onDownloading(true, 0)
                    
                    VideoDetailsCacheManager.cacheVideoDetails(context, video.id, video.title)
                    
                    downloadManager.startDownload(taskId)
                    Toast.makeText(context, "开始下载...", Toast.LENGTH_SHORT).show()
                    
                } catch (e: Exception) {
                    Toast.makeText(context, "创建下载任务失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMediumLow)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaMediumLow)
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

private fun navigateToCategory(context: android.content.Context, href: String, title: String) {
    if (href.isBlank()) return
    val intent = android.content.Intent(context, CategoryActivity::class.java).apply {
        putExtra("categoryTitle", title)
        putExtra("categoryHref", href)
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
