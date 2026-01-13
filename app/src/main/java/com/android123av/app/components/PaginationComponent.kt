package com.android123av.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private object PaginationDefaults {
    val ButtonCornerRadius = 12.dp
    val ButtonElevation = 2.dp
    val ButtonPaddingHorizontal = 16.dp
    val ButtonPaddingVertical = 12.dp
    val IconSize = 20.dp
    val LoadingIndicatorSize = 16.dp
    val LoadingIndicatorStrokeWidth = 2.dp
    val RowPaddingHorizontal = 16.dp
    val RowPaddingVertical = 12.dp
    val SpacerWidth = 16.dp
    val PageDisplayPaddingHorizontal = 16.dp
    val PageDisplayPaddingVertical = 10.dp
    val ContentSpacing = 4.dp
    val DisabledAlpha = 0.5f
    val MaxPageInputLength = 6
    val ErrorTextPaddingTop = 4.dp
}

@Composable
fun PaginationComponent(
    currentPage: Int,
    totalPages: Int,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    isLoading: Boolean = false,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit,
    onPageSelected: ((Int) -> Unit)? = null
) {
    var showPageDialog by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var isInputError by remember { mutableStateOf(false) }

    if (showPageDialog) {
        AlertDialog(
            onDismissRequest = {
                showPageDialog = false
                pageInput = ""
                isInputError = false
            },
            title = { Text("跳转页面") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = {
                            pageInput = it.filter { char -> char.isDigit() }.take(PaginationDefaults.MaxPageInputLength)
                            isInputError = false
                        },
                        label = { Text("输入页码") },
                        supportingText = { Text("共 $totalPages 页") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isInputError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isInputError) {
                        Text(
                            text = "请输入 1 到 $totalPages 之间的有效页码",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = PaginationDefaults.ErrorTextPaddingTop)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pageNum = pageInput.toIntOrNull()
                        if (pageNum != null && pageNum in 1..totalPages) {
                            onPageSelected?.invoke(pageNum)
                            showPageDialog = false
                            pageInput = ""
                            isInputError = false
                        } else {
                            isInputError = true
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPageDialog = false
                        pageInput = ""
                        isInputError = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = PaginationDefaults.RowPaddingHorizontal,
                vertical = PaginationDefaults.RowPaddingVertical
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationButton(
            onClick = onLoadPrevious,
            enabled = hasPrevPage && !isLoading,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "上一页"
        )

        Spacer(modifier = Modifier.width(PaginationDefaults.SpacerWidth))

        PageDisplay(
            currentPage = currentPage,
            totalPages = totalPages,
            isClickable = onPageSelected != null && totalPages > 1,
            onClick = { showPageDialog = true }
        )

        Spacer(modifier = Modifier.width(PaginationDefaults.SpacerWidth))

        NavigationButton(
            onClick = onLoadNext,
            enabled = hasNextPage && !isLoading,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "下一页",
            isLoading = isLoading
        )
    }
}

@Composable
private fun NavigationButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(PaginationDefaults.ButtonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PaginationDefaults.DisabledAlpha),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PaginationDefaults.DisabledAlpha)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (enabled) PaginationDefaults.ButtonElevation else 0.dp
        ),
        contentPadding = PaddingValues(
            horizontal = PaginationDefaults.ButtonPaddingHorizontal,
            vertical = PaginationDefaults.ButtonPaddingVertical
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(PaginationDefaults.LoadingIndicatorSize),
                strokeWidth = PaginationDefaults.LoadingIndicatorStrokeWidth,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(PaginationDefaults.IconSize)
            )
        }
    }
}

@Composable
private fun PageDisplay(
    currentPage: Int,
    totalPages: Int,
    isClickable: Boolean,
    onClick: () -> Unit
) {
    val content = @Composable {
        Surface(
            shape = RoundedCornerShape(PaginationDefaults.ButtonCornerRadius),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = PaginationDefaults.PageDisplayPaddingHorizontal,
                    vertical = PaginationDefaults.PageDisplayPaddingVertical
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaginationDefaults.ContentSpacing)
            ) {
                Text(
                    text = "$currentPage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "/ $totalPages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (isClickable) {
        Box(modifier = Modifier.clickable(onClick = onClick)) {
            content()
        }
    } else {
        content()
    }
}
