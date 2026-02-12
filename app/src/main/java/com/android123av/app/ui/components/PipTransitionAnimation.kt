package com.android123av.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PiP过渡效果组件
 * 提供平滑的画中画进入和退出动画
 */
@Composable
fun PipTransitionBox(
    isVisible: Boolean,
    animationDurationMillis: Int = 300,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = tween(
            durationMillis = animationDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "pipScaleAnimation",
        finishedListener = { if (!isVisible) onFinished() }
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = animationDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "pipAlphaAnimation"
    )
    
    val cornerRadius by animateDpAsState(
        targetValue = if (isVisible) 8.dp else 20.dp,
        animationSpec = tween(
            durationMillis = animationDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "pipCornerRadiusAnimation"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

/**
 * 可调整大小的PiP窗口容器
 * 支持拖拽和调整大小的动画
 */
@Composable
fun ResizablePipContainer(
    modifier: Modifier = Modifier,
    initialWidth: Dp = 300.dp,
    initialHeight: Dp = 200.dp,
    minWidth: Dp = 150.dp,
    minHeight: Dp = 100.dp,
    maxWidth: Dp = 600.dp,
    maxHeight: Dp = 400.dp,
    isAnimated: Boolean = true,
    animationDurationMillis: Int = 300,
    content: @Composable BoxScope.() -> Unit
) {
    var currentWidth by remember { mutableStateOf(initialWidth) }
    var currentHeight by remember { mutableStateOf(initialHeight) }
    
    val animatedWidth by animateDpAsState(
        targetValue = currentWidth,
        animationSpec = tween(
            durationMillis = if (isAnimated) animationDurationMillis else 0,
            easing = FastOutSlowInEasing
        ),
        label = "pipWidthAnimation"
    )
    
    val animatedHeight by animateDpAsState(
        targetValue = currentHeight,
        animationSpec = tween(
            durationMillis = if (isAnimated) animationDurationMillis else 0,
            easing = FastOutSlowInEasing
        ),
        label = "pipHeightAnimation"
    )
    
    Box(
        modifier = modifier
            .width(animatedWidth)
            .height(animatedHeight)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
    ) {
        content()
    }
}

/**
 * PiP过渡指示器动画
 * 显示进入/退出PiP模式的视觉反馈
 */
@Composable
fun PipTransitionIndicator(
    isEntering: Boolean,
    animationDurationMillis: Int = 300,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isEntering) 0.8f else 1.2f,
        animationSpec = tween(
            durationMillis = animationDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "indicatorScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isEntering) 0f else 1f,
        animationSpec = tween(
            durationMillis = animationDurationMillis,
            easing = LinearEasing
        ),
        label = "indicatorAlpha"
    )
    
    Box(
        modifier = modifier
            .size(100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
        )
    }
}

/**
 * PiP滑动进入动画
 * 从屏幕边缘滑入
 */
@Composable
fun PipSlideInAnimation(
    isVisible: Boolean,
    animationDurationMillis: Int = 300,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        )
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * PiP缩放+淡入/淡出组合动画
 */
@Composable
fun PipScaleFadeAnimation(
    isVisible: Boolean,
    animationDurationMillis: Int = 300,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            initialScale = 0.3f,
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        ),
        exit = scaleOut(
            targetScale = 0.3f,
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = FastOutSlowInEasing
            )
        )
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}
