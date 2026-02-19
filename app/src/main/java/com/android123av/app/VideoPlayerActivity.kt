package com.android123av.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.android123av.app.models.Video
import com.android123av.app.screens.VideoPlayerScreen
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.state.PipSettingsManager
import com.android123av.app.ui.theme.MyApplicationTheme
import com.android123av.app.utils.ActivityUtils

class VideoPlayerActivity : ComponentActivity() {
    private val isPipSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    
    private val _isPipMode = mutableStateOf(false)
    private var currentPlayer: Player? = null
    private var shouldFinishOnExitPip = false
    
    companion object {
        const val ACTION_PLAY_PAUSE = "com.android123av.app.PIP_ACTION_PLAY_PAUSE"
        const val ACTION_FORWARD = "com.android123av.app.PIP_ACTION_FORWARD"
        const val ACTION_BACKWARD = "com.android123av.app.PIP_ACTION_BACKWARD"
        const val ACTION_CLOSE = "com.android123av.app.PIP_ACTION_CLOSE"
        
        private var _pipControlCallback: PipControlCallback? = null
        
        fun setPipControlCallback(callback: PipControlCallback?) {
            _pipControlCallback = callback
        }
    }
    
    interface PipControlCallback {
        fun onPlayPause()
        fun onSeekForward()
        fun onSeekBackward()
    }
    
    fun setPlayer(player: Player?) {
        currentPlayer = player
    }
    
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> _pipControlCallback?.onPlayPause()
                ACTION_FORWARD -> _pipControlCallback?.onSeekForward()
                ACTION_BACKWARD -> _pipControlCallback?.onSeekBackward()
                ACTION_CLOSE -> stopPlaybackAndFinish()
            }
        }
    }
    
    private fun stopPlaybackAndFinish() {
        shouldFinishOnExitPip = true
        
        try {
            currentPlayer?.let { player ->
                player.pause()
                player.stop()
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "停止播放器失败: ${e.message}")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            return
        }
        
        runOnUiThread {
            forceStopPlayer()
            if (!isFinishing) {
                finish()
            }
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ThemeStateManager.initialize(this)
        PipSettingsManager.initialize(this)
        ActivityUtils.updateStatusBarColor(this)
        
        registerPipActionReceiver()
        
        val video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("video", Video::class.java)
        } else {
            intent.getParcelableExtra("video")
        }
        val localVideoPath = intent.getStringExtra("localVideoPath")
        val videoId = intent.getStringExtra("videoId")
        
        Log.d("VideoPlayerActivity", "onCreate: videoId=$videoId")
        
        setContent {
            val currentTheme by ThemeStateManager.currentTheme.collectAsState()
            val isPipMode by _isPipMode
            
            LaunchedEffect(currentTheme) {
                ActivityUtils.updateStatusBarColor(this@VideoPlayerActivity)
            }
            
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerScreen(
                        modifier = Modifier.padding(it),
                        video = video,
                        localVideoPath = localVideoPath,
                        localVideoId = videoId,
                        isPipMode = isPipMode,
                        onBack = { canEnterPip ->
                            Log.d("VideoPlayerActivity", "返回按钮被点击了, canEnterPip=$canEnterPip")
                            if (canEnterPip && isPipSupported && PipSettingsManager.isAutoPopOnBackEnabled()) {
                                enterPip()
                            } else {
                                finish()
                            }
                        },
                        onEnterPip = { isPlaying ->
                            updatePipActions(isPlaying)
                        }
                    )
                }
            }
        }
    }
    
    override fun onStop() {
        if (!_isPipMode.value && !isFinishing) {
            try {
                currentPlayer?.pause()
            } catch (e: Exception) {
                Log.e("VideoPlayerActivity", "暂停播放器失败: ${e.message}")
            }
        }
        super.onStop()
    }
    
    override fun onDestroy() {
        forceStopPlayer()
        super.onDestroy()
        unregisterPipActionReceiver()
    }
    
    private fun forceStopPlayer() {
        try {
            currentPlayer?.stop()
            currentPlayer?.release()
            currentPlayer = null
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "强制停止播放器失败: ${e.message}")
        }
    }
    
    private fun registerPipActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_FORWARD)
            addAction(ACTION_BACKWARD)
            addAction(ACTION_CLOSE)
        }
        ContextCompat.registerReceiver(this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    
    private fun unregisterPipActionReceiver() {
        try {
            unregisterReceiver(pipActionReceiver)
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "取消注册广播接收器失败: ${e.message}")
        }
    }
    
    /**
     * 创建PiP自定义按钮
     */
    private fun createPipAction(
        actionId: String,
        iconResId: Int,
        title: String,
        description: String,
        requestCode: Int
    ): RemoteAction {
        val intent = Intent(actionId).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            Icon.createWithResource(this, iconResId),
            title,
            description,
            pendingIntent
        )
    }
    
    /**
     * 进入画中画(PiP)模式
     */
    private fun enterPip() {
        if (!isPipSupported) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (pipParams == null) {
                    updatePipActions(false)
                }
                pipParams?.let { this.enterPictureInPictureMode(it) }
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "进入PiP失败: ${e.message}")
        }
    }
    
    private var pipParams: PictureInPictureParams? = null
    
    private fun updatePipActions(isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playPauseIcon = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            val playPauseTitle = if (isPlaying) "暂停" else "播放"
            
            pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(
                    listOf(
                        createPipAction(
                            ACTION_BACKWARD,
                            android.R.drawable.ic_media_previous,
                            "快退",
                            "快退10秒",
                            1
                        ),
                        createPipAction(
                            ACTION_PLAY_PAUSE,
                            playPauseIcon,
                            playPauseTitle,
                            "切换播放状态",
                            0
                        ),
                        createPipAction(
                            ACTION_FORWARD,
                            android.R.drawable.ic_media_next,
                            "快进",
                            "快进10秒",
                            2
                        ),
                        createPipAction(
                            ACTION_CLOSE,
                            android.R.drawable.ic_menu_close_clear_cancel,
                            "关闭",
                            "关闭小窗",
                            3
                        )
                    )
                )
                .build()
            
            setPictureInPictureParams(pipParams!!)
        }
    }
    
    @Deprecated("Deprecated in android.app.Activity")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        _isPipMode.value = isInPictureInPictureMode
        
        if (!isInPictureInPictureMode) {
            _pipControlCallback = null
            
            if (shouldFinishOnExitPip) {
                runOnUiThread {
                    forceStopPlayer()
                    if (!isFinishing) {
                        finish()
                    }
                }
            } else {
                window.decorView.postDelayed({
                    if (!isFinishing) {
                        val isResumed = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                        if (!isResumed) {
                            runOnUiThread {
                                forceStopPlayer()
                                finish()
                            }
                        }
                    }
                }, 100)
            }
        }
    }
    
    fun notifyPlayingState(isPlaying: Boolean) {
        updatePipActions(isPlaying)
    }
}
