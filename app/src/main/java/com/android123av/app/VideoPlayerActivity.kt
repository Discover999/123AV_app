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
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.IconCompat
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
    private var wasPlayingBeforePip = false
    private var currentVideoPosition = 0L
    
    companion object {
        const val ACTION_PLAY_PAUSE = "com.android123av.app.PIP_ACTION_PLAY_PAUSE"
        const val ACTION_FORWARD = "com.android123av.app.PIP_ACTION_FORWARD"
        const val ACTION_BACKWARD = "com.android123av.app.PIP_ACTION_BACKWARD"
        const val ACTION_CLOSE = "com.android123av.app.PIP_ACTION_CLOSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.android123av.app.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_SEEK_FORWARD = "com.android123av.app.ACTION_SEEK_FORWARD"
        const val ACTION_SEEK_BACKWARD = "com.android123av.app.ACTION_SEEK_BACKWARD"
        
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
    
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("VideoPlayerActivity", "PiP广播接收: ${intent?.action}")
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    Log.d("VideoPlayerActivity", "PiP按钮: 播放/暂停")
                    _pipControlCallback?.onPlayPause()
                }
                ACTION_FORWARD -> {
                    Log.d("VideoPlayerActivity", "PiP按钮: 快进10秒")
                    _pipControlCallback?.onSeekForward()
                }
                ACTION_BACKWARD -> {
                    Log.d("VideoPlayerActivity", "PiP按钮: 快退10秒")
                    _pipControlCallback?.onSeekBackward()
                }
                ACTION_CLOSE -> {
                    Log.d("VideoPlayerActivity", "PiP按钮: 关闭")
                    finish()
                }
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
        
        Log.d("VideoPlayerActivity", "onCreate: videoId=$videoId, localVideoPath=$localVideoPath")
        
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
                        onBack = {
                            Log.d("VideoPlayerActivity", "返回按钮被点击了")
                            // 检查是否启用了自动PiP
                            if (isPipSupported && PipSettingsManager.isAutoPopOnBackEnabled()) {
                                Log.d("VideoPlayerActivity", "条件满足，准备进入PiP")
                                enterPip()
                            } else {
                                Log.d("VideoPlayerActivity", "PiP条件不满足：isPipSupported=$isPipSupported, isAutoPopOnBackEnabled=${PipSettingsManager.isAutoPopOnBackEnabled()}")
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterPipActionReceiver()
    }
    
    private fun registerPipActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_FORWARD)
            addAction(ACTION_BACKWARD)
            addAction(ACTION_CLOSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipActionReceiver, filter)
        }
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
        val intent = Intent(actionId)
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
        Log.d("VideoPlayerActivity", "enterPip: isPipSupported=$isPipSupported")
        if (!isPipSupported) {
            Log.w("VideoPlayerActivity", "设备不支持PiP，需要Android 8.0+")
            return
        }
        
        val isEnabled = PipSettingsManager.isAutoPopOnBackEnabled()
        Log.d("VideoPlayerActivity", "enterPip: 自动PiP已启用=$isEnabled")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val aspectRatio = Rational(16, 9)
                
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
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
                                android.R.drawable.ic_media_play,
                                "播放/暂停",
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
                
                Log.d("VideoPlayerActivity", "正在进入PiP模式...")
                val result = this.enterPictureInPictureMode(pipParams)
                Log.d("VideoPlayerActivity", "PiP进入结果: $result")
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "进入PiP失败: ${e.message}", e)
        }
    }
    
    /**
     * PiP模式变化回调 - 隐藏控件并保持播放
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        Log.d("VideoPlayerActivity", "PiP模式变化: isInPictureInPictureMode=$isInPictureInPictureMode")
        _isPipMode.value = isInPictureInPictureMode
        
        if (isInPictureInPictureMode) {
            Log.d("VideoPlayerActivity", "进入PiP模式，已设置控制回调")
        } else {
            Log.d("VideoPlayerActivity", "退出PiP模式，已清除控制回调")
            _pipControlCallback = null
        }
    }
}
