package com.android123av.app.screens

import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.android123av.app.models.Video
import com.android123av.app.ui.components.LoadingState
import com.android123av.app.ui.components.PlayerControls
import com.android123av.app.ui.components.VideoErrorState
import com.android123av.app.ui.components.VideoInfoPanel
import com.android123av.app.viewmodel.VideoPlayerViewModel
import com.android123av.app.viewmodel.VideoPlayerViewModelFactory

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    video: Video,
    onBack: () -> Unit,
    providedViewModel: VideoPlayerViewModel = viewModel(
        factory = VideoPlayerViewModelFactory(video)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? androidx.activity.ComponentActivity
    val window = activity?.window
    
    val uiState by providedViewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(uiState.isFullscreen) {
        activity?.requestedOrientation = if (uiState.isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    LaunchedEffect(uiState.isFullscreen) {
        setSystemUIVisibility(window, uiState.isFullscreen)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            setSystemUIVisibility(window, false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> providedViewModel.getExoPlayer()?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (uiState.isPlaying) providedViewModel.getExoPlayer()?.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    BackHandler(uiState.isFullscreen) {
        providedViewModel.toggleFullscreen()
    }
    
    when {
        uiState.isLoading -> LoadingState(video.title)
        uiState.errorMessage != null -> VideoErrorState(
            message = uiState.errorMessage!!,
            onBack = onBack,
            onRetry = { providedViewModel.loadVideoUrl() }
        )
        uiState.videoUrl != null -> VideoPlayerContent(
            modifier = modifier,
            viewModel = providedViewModel,
            video = video,
            uiState = uiState,
            onBack = if (uiState.isFullscreen) { { providedViewModel.toggleFullscreen() } } else onBack
        )
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    modifier: Modifier,
    viewModel: VideoPlayerViewModel,
    video: Video,
    uiState: com.android123av.app.models.PlayerState,
    onBack: () -> Unit
) {
    val controlsAlpha by animateFloatAsState(
        targetValue = if (uiState.showControls && !uiState.isLocked) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "controlsAlpha"
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.isFullscreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "fullscreenTransition"
        ) { fullscreen ->
            if (fullscreen) {
                FullscreenPlayerView(
                    viewModel = viewModel,
                    controlsAlpha = controlsAlpha,
                    uiState = uiState,
                    onBack = onBack
                )
            } else {
                PortraitPlayerView(
                    viewModel = viewModel,
                    controlsAlpha = controlsAlpha,
                    uiState = uiState,
                    video = video,
                    onBack = onBack
                )
            }
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullscreenPlayerView(
    viewModel: VideoPlayerViewModel,
    controlsAlpha: Float,
    uiState: com.android123av.app.models.PlayerState,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.getExoPlayer()
                    resizeMode = uiState.resizeMode
                    useController = false
                    viewModel.setPlayerView(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = viewModel.getExoPlayer()
                playerView.resizeMode = uiState.resizeMode
            }
        )
        
        PlayerControls(
            viewModel = viewModel,
            controlsAlpha = controlsAlpha,
            uiState = uiState,
            onBack = onBack
        )
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PortraitPlayerView(
    viewModel: VideoPlayerViewModel,
    controlsAlpha: Float,
    uiState: com.android123av.app.models.PlayerState,
    video: Video,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.getExoPlayer()
                        resizeMode = uiState.resizeMode
                        useController = false
                        viewModel.setPlayerView(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { playerView ->
                    playerView.player = viewModel.getExoPlayer()
                    playerView.resizeMode = uiState.resizeMode
                }
            )
            
            PlayerControls(
                viewModel = viewModel,
                controlsAlpha = controlsAlpha,
                uiState = uiState,
                onBack = onBack
            )
        }
        
        VideoInfoPanel(
            video = video,
            videoDetails = uiState.videoDetails,
            isLoadingDetails = uiState.isLoadingDetails
        )
    }
}

private fun setSystemUIVisibility(window: android.view.Window?, isFullscreen: Boolean) {
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
