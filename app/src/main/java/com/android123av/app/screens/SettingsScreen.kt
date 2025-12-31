package com.android123av.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android123av.app.state.ThemeStateManager
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var currentTheme by remember { mutableIntStateOf(ThemeStateManager.getTheme()) }

    LaunchedEffect(ThemeStateManager.currentTheme) {
        ThemeStateManager.currentTheme.collectLatest { theme ->
            currentTheme = theme
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "外观",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                title = "主题",
                subtitle = when (currentTheme) {
                    ThemeStateManager.THEME_LIGHT -> "亮色"
                    ThemeStateManager.THEME_DARK -> "暗色"
                    else -> "跟随系统"
                },
                onClick = { showThemeDialog = true }
            )
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                ThemeStateManager.setTheme(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: Int,
    onThemeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        Triple(ThemeStateManager.THEME_LIGHT, "亮色", "使用亮色主题"),
        Triple(ThemeStateManager.THEME_DARK, "暗色", "使用暗色主题"),
        Triple(ThemeStateManager.THEME_SYSTEM, "跟随系统", "跟随系统设置")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择主题",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                themes.forEach { (themeId, themeName, themeDesc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(themeId) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = themeName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = themeDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentTheme == themeId) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
