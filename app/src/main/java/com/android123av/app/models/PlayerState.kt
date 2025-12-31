package com.android123av.app.models

data class PlayerState(
    val isLoading: Boolean = true,
    val videoUrl: String? = null,
    val videoDetails: VideoDetails? = null,
    val isLoadingDetails: Boolean = false,
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val playbackState: Int = 0,
    val showControls: Boolean = true,
    val isFullscreen: Boolean = false,
    val isLocked: Boolean = false,
    val showSpeedSelector: Boolean = false,
    val currentSpeedIndex: Int = 2,
    val resizeMode: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
)

data class PlayerControlsState(
    val controlsAlpha: Float = 1f,
    val showControls: Boolean = true,
    val isLocked: Boolean = false,
    val showSpeedSelector: Boolean = false,
    val currentSpeedIndex: Int = 2,
    val resizeMode: Int = 0,
    val isFullscreen: Boolean = false
)
