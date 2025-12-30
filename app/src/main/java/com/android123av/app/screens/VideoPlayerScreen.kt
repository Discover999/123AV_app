package com.android123av.app.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls && !isLocked) 1f else 0f,
        animationSpec = tween(300),
        label = "controlsAlpha"
    )

    val hideControlsJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun scheduleHideControls() {
        hideControlsJob.value?.cancel()
        if (exoPlayer?.isPlaying == true) {
            hideControlsJob.value = coroutineScope.launch {
                delay(3000)
                showControls = false
            }
        }
    }

    fun showControlsTemporarily() {
        showControls = true
        scheduleHideControls()
    }

    fun createMediaSource(url: String): MediaSource {
        val factory = DefaultHttpDataSource.Factory()
        return if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        }
    }

    LaunchedEffect(video) {
        isLoading = true
        errorMessage = null
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

    LaunchedEffect(video) {
        if (video.details == null) {
            isLoadingDetails = true
            try {
                videoDetails = fetchVideoDetails(video.id)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingDetails = false
            }
        } else {
            videoDetails = video.details
        }
    }

    BackHandler(isFullscreen) {
        isFullscreen = false
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            hideControlsJob.value?.cancel()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isLocked) {
                    showControlsTemporarily()
                }
            }
    ) {
        when {
            isLoading -> LoadingState(video.title)
            errorMessage != null -> VideoErrorState(errorMessage!!) { onBack() }
            videoUrl != null -> {
                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .align(Alignment.TopCenter)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = ExoPlayer.Builder(ctx).build().apply {
                                        val source = createMediaSource(videoUrl!!)
                                        setMediaSource(source)
                                        prepare()
                                        playWhenReady = true

                                        addListener(object : Player.Listener {
                                            override fun onPlaybackStateChanged(state: Int) {
                                                when (state) {
                                                    Player.STATE_READY -> scheduleHideControls()
                                                    Player.STATE_ENDED -> {
                                                        showControls = true
                                                        hideControlsJob.value?.cancel()
                                                    }
                                                }
                                            }

                                            override fun onIsPlayingChanged(playing: Boolean) {
                                                if (playing) scheduleHideControls()
                                            }

                                            override fun onPlayerError(error: PlaybackException) {
                                                Toast.makeText(ctx, "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                    }
                                    exoPlayer = player
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        PlayerControls(
                            exoPlayer = exoPlayer,
                            isFullscreen = true,
                            isLocked = isLocked,
                            showSpeedSelector = showSpeedSelector,
                            currentSpeedIndex = currentSpeedIndex,
                            controlsAlpha = controlsAlpha,
                            onBack = { isFullscreen = false },
                            onFullscreen = { isFullscreen = false },
                            onLock = { isLocked = !isLocked },
                            onSpeedChange = { index ->
                                currentSpeedIndex = index
                                exoPlayer?.setPlaybackSpeed(playbackSpeeds[index].speed)
                                showSpeedSelector = false
                            },
                            onSpeedSelectorToggle = { showSpeedSelector = !showSpeedSelector },
                            onPlayPause = {
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = ExoPlayer.Builder(ctx).build().apply {
                                            val source = createMediaSource(videoUrl!!)
                                            setMediaSource(source)
                                            prepare()
                                            playWhenReady = true

                                            addListener(object : Player.Listener {
                                                override fun onPlaybackStateChanged(state: Int) {
                                                    when (state) {
                                                        Player.STATE_READY -> scheduleHideControls()
                                                        Player.STATE_ENDED -> {
                                                            showControls = true
                                                            hideControlsJob.value?.cancel()
                                                        }
                                                    }
                                                }

                                                override fun onIsPlayingChanged(playing: Boolean) {
                                                    if (playing) scheduleHideControls()
                                                }

                                                override fun onPlayerError(error: PlaybackException) {
                                                    Toast.makeText(ctx, "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            })
                                        }
                                        exoPlayer = player
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        useController = false
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            PlayerControls(
                                exoPlayer = exoPlayer,
                                isFullscreen = false,
                                isLocked = isLocked,
                                showSpeedSelector = showSpeedSelector,
                                currentSpeedIndex = currentSpeedIndex,
                                controlsAlpha = controlsAlpha,
                                onBack = onBack,
                                onFullscreen = { isFullscreen = true },
                                onLock = { isLocked = !isLocked },
                                onSpeedChange = { index ->
                                    currentSpeedIndex = index
                                    exoPlayer?.setPlaybackSpeed(playbackSpeeds[index].speed)
                                    showSpeedSelector = false
                                },
                                onSpeedSelectorToggle = { showSpeedSelector = !showSpeedSelector },
                                onPlayPause = {
                                    exoPlayer?.let { player ->
                                        if (player.isPlaying) player.pause() else player.play()
                                    }
                                }
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
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VideoErrorState(
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun PlayerControls(
    exoPlayer: Player?,
    isFullscreen: Boolean,
    isLocked: Boolean,
    showSpeedSelector: Boolean,
    currentSpeedIndex: Int,
    controlsAlpha: Float,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onSpeedSelectorToggle: () -> Unit,
    onPlayPause: () -> Unit
) {
    val playbackState = exoPlayer?.playbackState
    val isPlaying = exoPlayer?.isPlaying == true
    val currentPosition = exoPlayer?.currentPosition ?: 0L
    val duration = exoPlayer?.duration ?: 0L
    val bufferedPosition = exoPlayer?.bufferedPosition ?: 0L
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(controlsAlpha)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isFullscreen) {
                            onFullscreen()
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }

                if (!isLocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onLock,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "锁定",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onFullscreen,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "全屏",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLocked) {
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    val newPosition = (currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPosition)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "快退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    val newPosition = (currentPosition + 10000).coerceAtMost(duration)
                                    player.seekTo(newPosition)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "快进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (showSpeedSelector && !isLocked) {
                    PlaybackSpeedSelector(
                        currentIndex = currentSpeedIndex,
                        onSpeedSelect = onSpeedChange,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )

                    Slider(
                        value = progress,
                        onValueChange = { value ->
                            exoPlayer?.seekTo((value * duration).toLong())
                        },
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
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLocked) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onSpeedSelectorToggle,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = playbackSpeeds[currentSpeedIndex].label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
private fun PlaybackSpeedSelector(
    currentIndex: Int,
    onSpeedSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        playbackSpeeds.forEachIndexed { index, speed ->
            Text(
                text = speed.label,
                color = if (index == currentIndex) MaterialTheme.colorScheme.primary else Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSpeedSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun VideoInfoSection(
    video: Video,
    videoDetails: VideoDetails,
    modifier: Modifier = Modifier
) {
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

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
        }

        Spacer(modifier = Modifier.height(12.dp))

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
