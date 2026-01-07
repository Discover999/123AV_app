package com.android123av.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android123av.app.models.PlayerState
import com.android123av.app.models.Video
import com.android123av.app.player.ExoPlayerManager
import com.android123av.app.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "VideoPlayerViewModel"

class VideoPlayerViewModel(
    application: Application,
    private val video: Video
) : AndroidViewModel(application) {
    
    private val repository = VideoRepository()
    private val playerManager = ExoPlayerManager(application.applicationContext)
    
    private val _uiState = MutableStateFlow(PlayerState())
    val uiState: StateFlow<PlayerState> = _uiState.asStateFlow()
    
    private var hideControlsJob: Job? = null
    private var lastInteractionTime = System.currentTimeMillis()
    
    init {
        playerManager.createPlayer()
        loadVideoData()
        observePlayerState()
    }
    
    private fun observePlayerState() {
        viewModelScope.launch {
            playerManager.playerState.collect { playerState ->
                _uiState.value = _uiState.value.copy(
                    isPlaying = playerState.isPlaying,
                    playbackState = playerState.playbackState,
                    videoWidth = playerState.videoWidth,
                    videoHeight = playerState.videoHeight
                )
            }
        }
    }
    
    private fun loadVideoData() {
        loadVideoUrl()
        loadVideoDetails()
    }
    
    fun loadVideoUrl() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            repository.fetchVideoUrl(getApplication(), video)
                .onSuccess { url ->
                    _uiState.value = _uiState.value.copy(
                        videoUrl = url,
                        isLoading = false
                    )
                    playerManager.loadVideo(url)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "获取视频失败",
                        isLoading = false
                    )
                }
        }
    }
    
    private fun loadVideoDetails() {
        if (video.details != null) {
            _uiState.value = _uiState.value.copy(videoDetails = video.details)
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDetails = true)
            
            repository.fetchVideoDetails(video.id)
                .onSuccess { details ->
                    _uiState.value = _uiState.value.copy(
                        videoDetails = details,
                        isLoadingDetails = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingDetails = false)
                }
        }
    }
    
    fun togglePlayPause() {
        playerManager.togglePlayPause()
        updateInteractionTime()
    }
    
    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = !_uiState.value.isFullscreen
        )
        updateInteractionTime()
    }
    
    fun toggleLock() {
        _uiState.value = _uiState.value.copy(
            isLocked = !_uiState.value.isLocked
        )
        updateInteractionTime()
    }
    
    fun toggleSpeedSelector() {
        _uiState.value = _uiState.value.copy(
            showSpeedSelector = !_uiState.value.showSpeedSelector
        )
        updateInteractionTime()
    }
    
    fun setPlaybackSpeed(speed: Float, index: Int) {
        playerManager.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(
            currentSpeedIndex = index,
            showSpeedSelector = false
        )
        updateInteractionTime()
    }
    
    fun cycleResizeMode() {
        val currentMode = _uiState.value.resizeMode
        val modes = ExoPlayerManager.RESIZE_MODES
        val currentIndex = modes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % modes.size
        val nextMode = modes[nextIndex]
        
        Log.d(TAG, "cycleResizeMode: current=$currentMode, next=$nextMode")
        
        playerManager.setResizeMode(nextMode)
        _uiState.value = _uiState.value.copy(resizeMode = nextMode)
        updateInteractionTime()
    }
    
    fun showControls() {
        _uiState.value = _uiState.value.copy(showControls = true)
        updateInteractionTime()
        scheduleHideControls()
    }
    
    fun hideControls() {
        hideControlsJob?.cancel()
        _uiState.value = _uiState.value.copy(showControls = false)
    }
    
    private fun updateInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()
    }
    
    private fun scheduleHideControls() {
        hideControlsJob?.cancel()
        if (playerManager.player?.isPlaying == true && 
            !_uiState.value.isLocked && 
            !_uiState.value.showSpeedSelector) {
            hideControlsJob = viewModelScope.launch {
                val hideDelay = when (_uiState.value.playbackState) {
                    3 -> 8000L
                    else -> 5000L
                }
                delay(hideDelay)
                if (System.currentTimeMillis() - lastInteractionTime >= hideDelay - 500) {
                    _uiState.value = _uiState.value.copy(showControls = false)
                }
            }
        }
    }
    
    fun onPlaybackStateChanged(state: Int) {
        _uiState.value = _uiState.value.copy(playbackState = state)
        when (state) {
            2 -> scheduleHideControls()
            4 -> {
                _uiState.value = _uiState.value.copy(showControls = true)
                hideControlsJob?.cancel()
            }
            1 -> {
                _uiState.value = _uiState.value.copy(showControls = true)
            }
        }
    }
    
    fun onIsPlayingChanged(playing: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = playing)
        if (playing) {
            scheduleHideControls()
        } else {
            hideControlsJob?.cancel()
            _uiState.value = _uiState.value.copy(showControls = true)
        }
    }
    
    fun getExoPlayer() = playerManager.player
    
    fun setPlayerView(view: androidx.media3.ui.PlayerView) = playerManager.setPlayerView(view)
    
    fun getCurrentPosition() = playerManager.getCurrentPosition()
    
    fun getDuration() = playerManager.getDuration()
    
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    
    override fun onCleared() {
        super.onCleared()
        hideControlsJob?.cancel()
        playerManager.release()
    }
}
