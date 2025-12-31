package com.android123av.app.ui.components

import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.android123av.app.constants.PlayerConstants
import com.android123av.app.models.PlayerState
import com.android123av.app.viewmodel.VideoPlayerViewModel

@Composable
fun PlayerControls(
    viewModel: VideoPlayerViewModel,
    controlsAlpha: Float,
    uiState: PlayerState,
    onBack: () -> Unit
) {
    val exoPlayer = viewModel.getExoPlayer()
    
    var currentPosition by remember { mutableStateOf(viewModel.getCurrentPosition()) }
    var duration by remember { mutableStateOf(viewModel.getDuration()) }
    var isPlaying by remember { mutableStateOf(uiState.isPlaying) }
    var playbackState by remember { mutableStateOf(uiState.playbackState) }
    
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
                isFullscreen = uiState.isFullscreen,
                onReplay = { viewModel.seekTo(0); viewModel.togglePlayPause() },
                onFullscreen = { viewModel.toggleFullscreen() }
            )
            isIdle -> CenterLoadingIndicator()
            else -> {
                if (!uiState.isLocked) {
                    VideoPlayerOverlay(
                        showControls = uiState.showControls,
                        showSpeedSelector = uiState.showSpeedSelector,
                        currentSpeedIndex = uiState.currentSpeedIndex,
                        currentPosition = currentPosition,
                        duration = duration,
                        progress = progress,
                        isPlaying = isPlaying,
                        controlsAlpha = controlsAlpha,
                        isFullscreen = uiState.isFullscreen,
                        resizeMode = uiState.resizeMode,
                        onBack = onBack,
                        onFullscreen = { viewModel.toggleFullscreen() },
                        onLock = { viewModel.toggleLock() },
                        onSpeedChange = { viewModel.setPlaybackSpeed(PlayerConstants.PLAYBACK_SPEEDS[it].speed, it) },
                        onSpeedSelectorToggle = { viewModel.toggleSpeedSelector() },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo((it * duration).toLong()) },
                        onSeekBackward = { viewModel.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                        onSeekForward = { viewModel.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                        onTap = { viewModel.showControls() },
                        onHideControls = { viewModel.hideControls() },
                        onResizeModeChange = { viewModel.cycleResizeMode() }
                    )
                } else {
                    LockedOverlay(onUnlock = { viewModel.toggleLock() })
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
    resizeMode: Int,
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
    onResizeModeChange: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TapGestureLayer(
            showControls = showControls,
            onTap = onTap,
            onDoubleTapLeft = onSeekBackward,
            onDoubleTapRight = onSeekForward
        )
        
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(controlsAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
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
                
                SpeedSelector(
                    currentIndex = currentSpeedIndex,
                    onSpeedSelect = { index ->
                        onSpeedChange(index)
                    },
                    visible = showSpeedSelector
                )
                
                BottomControls(
                    currentPosition = currentPosition,
                    duration = duration,
                    progress = progress,
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSpeedSelectorToggle = onSpeedSelectorToggle,
                    currentSpeed = PlayerConstants.PLAYBACK_SPEEDS[currentSpeedIndex].label
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
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
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回"
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlIconButton(
                icon = when (resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.CropFree
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.CropSquare
                    else -> Icons.Default.ZoomIn
                },
                onClick = onResizeModeChange
            )
            ControlIconButton(
                icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                onClick = onFullscreen
            )
            ControlIconButton(
                icon = Icons.Default.Lock,
                onClick = onLock
            )
        }
    }
}

@Composable
private fun BottomControls(
    currentPosition: Long,
    duration: Long,
    progress: Float,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedSelectorToggle: () -> Unit,
    currentSpeed: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                thumbColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSpeedSelectorToggle,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = currentSpeed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                PlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPause
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
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
                        val centerX = size.width / 2
                        if (offset.x < centerX) {
                            onDoubleTapLeft()
                        } else {
                            onDoubleTapRight()
                        }
                    }
                )
            }
    )
}

@Composable
private fun SpeedSelector(
    currentIndex: Int,
    onSpeedSelect: (Int) -> Unit,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                PlayerConstants.PLAYBACK_SPEEDS.forEachIndexed { index, speed ->
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
}

@Composable
fun LockedOverlay(onUnlock: () -> Unit) {
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
fun CenterLoadingIndicator() {
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
fun PlaybackCompleteOverlay(
    isFullscreen: Boolean,
    onReplay: () -> Unit,
    onFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onReplay,
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

private fun formatTime(timeMs: Long): String {
    val seconds = (timeMs / 1000) % 60
    val minutes = (timeMs / (1000 * 60)) % 60
    val hours = timeMs / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
