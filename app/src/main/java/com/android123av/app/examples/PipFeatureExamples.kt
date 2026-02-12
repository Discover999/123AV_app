package com.android123av.app.examples

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android123av.app.ui.components.*
import com.android123av.app.state.PipSettingsManager

/**
 * PiP功能使用示例
 */
object PipFeatureExamples {
    
    /**
     * 示例1: 基础PiP过渡动画
     */
    @Composable
    fun BasicPipTransitionExample() {
        var isInPip by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { isInPip = !isInPip }) {
                Text("切换PiP模式")
            }
            
            // 使用PipTransitionBox动画
            PipTransitionBox(
                isVisible = isInPip,
                animationDurationMillis = PipSettingsManager.getPipAnimationDuration(),
                modifier = Modifier
                    .size(300.dp, 200.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .align(Alignment.CenterHorizontally)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
    
    /**
     * 示例2: 使用PipScaleFadeAnimation
     */
    @Composable
    fun ScaleFadeAnimationExample() {
        var showPip by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { showPip = !showPip }) {
                Text("缩放+淡入淡出动画")
            }
            
            PipScaleFadeAnimation(
                isVisible = showPip,
                animationDurationMillis = PipSettingsManager.getPipAnimationDuration(),
                modifier = Modifier
                    .size(200.dp)
                    .background(MaterialTheme.colorScheme.secondary, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .align(Alignment.CenterHorizontally)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("PiP", color = Color.White)
                }
            }
        }
    }
    
    /**
     * 示例3: 使用PipSlideInAnimation
     */
    @Composable
    fun SlideInAnimationExample() {
        var showSlide by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Button(
                onClick = { showSlide = !showSlide },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("滑入动画")
            }
            
            PipSlideInAnimation(
                isVisible = showSlide,
                animationDurationMillis = PipSettingsManager.getPipAnimationDuration(),
                modifier = Modifier
                    .width(200.dp)
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.tertiary, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("从右侧滑入", color = Color.White)
                }
            }
        }
    }
    
    /**
     * 示例4: 使用ResizablePipContainer
     */
    @Composable
    fun ResizablePipContainerExample() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("可调整大小的PiP容器")
            
            ResizablePipContainer(
                initialWidth = 250.dp,
                initialHeight = 150.dp,
                isAnimated = PipSettingsManager.isPipAnimationEnabled(),
                animationDurationMillis = PipSettingsManager.getPipAnimationDuration()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("可调整大小")
                }
            }
        }
    }
    
    /**
     * 示例5: 完整的PiP集成示例
     */
    @Composable
    fun FullPipIntegrationExample() {
        var isInPipMode by remember { mutableStateOf(false) }
        var showIndicator by remember { mutableStateOf(false) }
        
        // 监听设置变化
        val animationDuration = PipSettingsManager.getPipAnimationDuration()
        val animationEnabled = PipSettingsManager.isPipAnimationEnabled()
        
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 主要内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = {
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(onClick = {
                        showIndicator = true
                        isInPipMode = !isInPipMode
                    }) {
                        Text("进入/退出 PiP 模式")
                    }
                    
                    // PiP指示器
                    if (showIndicator) {
                        PipTransitionIndicator(
                            isEntering = isInPipMode,
                            animationDurationMillis = if (animationEnabled) animationDuration else 0,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            )
            
            // PiP内容 - 使用组合动画
            if (animationEnabled) {
                PipScaleFadeAnimation(
                    isVisible = isInPipMode,
                    animationDurationMillis = animationDuration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(280.dp, 180.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PiP视频播放器", color = Color.White)
                    }
                }
            } else {
                // 无动画版本
                AnimatedVisibility(
                    visible = isInPipMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(280.dp, 180.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PiP视频播放器", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 在VideoPlayerActivity中的使用示例
 */
object VideoPlayerPipIntegration {
    
    /**
     * VideoPlayerActivity的返回处理
     */
    fun getOnBackHandler(
        activity: android.app.Activity,
        isPipSupported: Boolean
    ): () -> Unit = {
        // 检查是否启用了自动PiP
        if (isPipSupported && PipSettingsManager.isAutoPopOnBackEnabled()) {
            // 获取动画配置
            val animationDuration = PipSettingsManager.getPipAnimationDuration()
            val animationEnabled = PipSettingsManager.isPipAnimationEnabled()
            
            // 进入PiP模式
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val aspectRatio = android.util.Rational(16, 9)
                val pipParams = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                activity.enterPictureInPictureMode(pipParams)
            }
        } else {
            activity.finish()
        }
    }
}
