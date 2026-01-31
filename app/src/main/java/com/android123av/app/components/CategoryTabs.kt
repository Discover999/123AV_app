package com.android123av.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private object CategoryTabsDefaults {
    val ChipCornerRadius = 20.dp
    val AnimationDuration = 300
    val ChipSpacing = 10.dp
    val IconSize = 18.dp
    val ChipPaddingHorizontal = 16.dp
    val ChipPaddingVertical = 10.dp
    val ContentSpacing = 8.dp
    val RowPaddingHorizontal = 16.dp
    val RowPaddingVertical = 8.dp
}

@Composable
fun CategoryTabs(
    modifier: Modifier = Modifier,
    onCategoryChange: (String) -> Unit,
    onDoubleTapToTop: () -> Unit = {}
) {
    val categories = remember {
        listOf(
            CategoryInfo("新发布", Icons.Default.Star),
            CategoryInfo("最近更新", Icons.Default.Refresh),
            CategoryInfo("正在观看", Icons.Default.PlayArrow),
            CategoryInfo("未审查", Icons.Default.Warning)
        )
    }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = CategoryTabsDefaults.RowPaddingHorizontal,
                vertical = CategoryTabsDefaults.RowPaddingVertical
            ),
        horizontalArrangement = Arrangement.spacedBy(CategoryTabsDefaults.ChipSpacing)
    ) {
        categories.forEachIndexed { index, category ->
            CategoryChip(
                category = category,
                isSelected = selectedTabIndex == index,
                onClick = {
                    selectedTabIndex = index
                    onCategoryChange(category.name)
                },
                onDoubleTapToTop = onDoubleTapToTop
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: CategoryInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleTapToTop: () -> Unit = {}
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(CategoryTabsDefaults.AnimationDuration)
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(CategoryTabsDefaults.AnimationDuration)
    )

    val chipShape = RoundedCornerShape(CategoryTabsDefaults.ChipCornerRadius)

    var lastTapTime by remember { mutableLongStateOf(0L) }
    val doubleTapThreshold = 300L

    Surface(
        modifier = Modifier
            .clip(chipShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < doubleTapThreshold) {
                            onDoubleTapToTop()
                        }
                        lastTapTime = currentTime
                        onClick()
                    }
                )
            },
        shape = chipShape,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CategoryTabsDefaults.ChipPaddingHorizontal,
                vertical = CategoryTabsDefaults.ChipPaddingVertical
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CategoryTabsDefaults.ContentSpacing)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(CategoryTabsDefaults.IconSize),
                tint = contentColor
            )
            Text(
                text = category.name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

data class CategoryInfo(
    val name: String,
    val icon: ImageVector
)
