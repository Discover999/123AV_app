package com.android123av.app.utils

import androidx.compose.animation.core.*
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 画中画(PiP)动画工具类
 * 提供PiP模式下的进入/退出动画效果
 */
object PipAnimationUtils {
    
    /**
     * PiP进入动画规范
     */
    @Composable
    fun rememberPipEnterTransition(durationMillis: Int = 300): EnterTransition = remember(durationMillis) {
        EnterTransition.None
    }
    
    /**
     * PiP退出动画规范
     */
    @Composable
    fun rememberPipExitTransition(durationMillis: Int = 300): ExitTransition = remember(durationMillis) {
        ExitTransition.None
    }
    
    /**
     * 创建PiP大小变化的动画
     */
    @Composable
    fun AnimatePipSize(
        targetWidth: Dp,
        targetHeight: Dp,
        durationMillis: Int = 300,
        content: @Composable (width: Dp, height: Dp) -> Unit
    ) {
        val animatedWidth = animateDpAsState(
            targetValue = targetWidth,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            label = "pipWidthAnimation"
        )
        
        val animatedHeight = animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            label = "pipHeightAnimation"
        )
        
        content(animatedWidth.value, animatedHeight.value)
    }
    
    /**
     * 创建PiP位置变化的动画
     */
    @Composable
    fun AnimatePipPosition(
        targetX: Dp,
        targetY: Dp,
        durationMillis: Int = 300,
        content: @Composable (x: Dp, y: Dp) -> Unit
    ) {
        val animatedX = animateDpAsState(
            targetValue = targetX,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            label = "pipXAnimation"
        )
        
        val animatedY = animateDpAsState(
            targetValue = targetY,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            label = "pipYAnimation"
        )
        
        content(animatedX.value, animatedY.value)
    }
    
    /**
     * 预定义的PiP动画参数
     */
    data class PipAnimationConfig(
        val durationMillis: Int = 300,
        val easing: Easing = FastOutSlowInEasing,
        val delayMillis: Int = 0
    ) {
        fun toAnimationSpec(): FiniteAnimationSpec<Float> = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing
        )
    }
}
