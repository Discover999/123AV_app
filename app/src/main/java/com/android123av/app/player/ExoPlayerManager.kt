package com.android123av.app.player

import android.content.Context
import android.widget.Toast
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.android123av.app.models.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
class ExoPlayerManager(private val context: Context) {
    
    private var _player: ExoPlayer? = null
    val player: ExoPlayer?
        get() = _player
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState
    
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playerState.value = _playerState.value.copy(
                playbackState = state,
                isPlaying = state == Player.STATE_READY && _player?.isPlaying == true
            )
        }
        
        override fun onIsPlayingChanged(playing: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = playing)
        }
        
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _playerState.value = _playerState.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height
            )
        }
        
        override fun onPlayerError(error: PlaybackException) {
            val errorMsg = getErrorMessage(error)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }
    
    fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context).build().apply {
            _player = this
            addListener(listener)
        }
    }
    
    fun loadVideo(url: String) {
        val player = _player ?: return
        val source = createMediaSource(url)
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }
    
    fun release() {
        _player?.apply {
            removeListener(listener)
            release()
        }
        _player = null
    }
    
    fun togglePlayPause() {
        _player?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }
    
    fun setPlaybackSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
    }
    
    fun seekTo(positionMs: Long) {
        _player?.seekTo(positionMs)
    }
    
    fun getCurrentPosition(): Long {
        return _player?.currentPosition ?: 0L
    }
    
    fun getDuration(): Long {
        return _player?.duration ?: 0L
    }
    
    fun setResizeMode(mode: Int) {
        _playerState.value = _playerState.value.copy(resizeMode = mode)
    }
    
    private fun createMediaSource(url: String): MediaSource {
        val factory = DefaultHttpDataSource.Factory()
        return if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        }
    }
    
    private fun getErrorMessage(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "直播已结束，请刷新重试"
            PlaybackException.ERROR_CODE_TIMEOUT -> "网络连接超时，请检查网络"
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "网络错误，请检查网络连接"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "视频格式不支持"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "视频解码失败"
            else -> "播放错误: ${error.message ?: "未知错误"}"
        }
    }
    
    companion object {
        val RESIZE_MODES = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
        
        const val DEFAULT_RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
}
