package com.android123av.app.constants

import androidx.media3.ui.AspectRatioFrameLayout

object PlayerConstants {
    
    data class PlaybackSpeed(
        val speed: Float,
        val label: String
    )
    
    val PLAYBACK_SPEEDS = listOf(
        PlaybackSpeed(0.5f, "0.5x"),
        PlaybackSpeed(0.75f, "0.75x"),
        PlaybackSpeed(1.0f, "1.0x"),
        PlaybackSpeed(1.25f, "1.25x"),
        PlaybackSpeed(1.5f, "1.5x"),
        PlaybackSpeed(1.75f, "1.75x"),
        PlaybackSpeed(2.0f, "2.0x")
    )
    
    const val DEFAULT_SPEED_INDEX = 2
    
    val RESIZE_MODES = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    
    const val DEFAULT_RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FIT
    
    const val CONTROLS_HIDE_DELAY_NORMAL = 5000L
    const val CONTROLS_HIDE_DELAY_BUFFERING = 8000L
    
    const val VIDEO_FETCH_TIMEOUT = 6000L
    
    object ErrorMessages {
        const val FETCH_VIDEO_FAILED = "无法获取视频播放地址"
        const val FETCH_DETAILS_FAILED = "无法获取视频详情"
        const val NETWORK_ERROR = "网络错误，请检查网络连接"
        const val TIMEOUT_ERROR = "网络连接超时，请检查网络"
        const val CONNECTION_FAILED = "网络连接失败"
        const val FORMAT_NOT_SUPPORTED = "视频格式不支持"
        const val DECODER_INIT_FAILED = "解码器初始化失败"
        const val DECODING_FAILED = "视频解码失败"
        const val LIVE_ENDED = "直播已结束，请刷新重试"
    }
}
