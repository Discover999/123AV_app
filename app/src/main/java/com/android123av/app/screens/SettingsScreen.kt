package com.android123av.app.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android123av.app.state.DownloadPathManager
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.constants.AppConstants
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var currentTheme by remember { mutableIntStateOf(ThemeStateManager.getTheme()) }
    var dynamicColorEnabled by remember { mutableStateOf(ThemeStateManager.isDynamicColorEnabled()) }
    var currentPathDisplay by remember { mutableStateOf(DownloadPathManager.getDisplayPath(context)) }
    var currentPath by remember { mutableStateOf(DownloadPathManager.getCurrentPath(context)) }
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(ThemeStateManager.currentTheme) {
        ThemeStateManager.currentTheme.collectLatest { theme ->
            currentTheme = theme
        }
    }

    LaunchedEffect(ThemeStateManager.dynamicColor) {
        ThemeStateManager.dynamicColor.collectLatest { enabled ->
            dynamicColorEnabled = enabled
        }
    }

    LaunchedEffect(currentPath) {
        currentPathDisplay = if (currentPath == DownloadPathManager.getDefaultPath(context)) {
            "默认位置"
        } else {
            currentPath
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
                    AppConstants.THEME_LIGHT -> "亮色"
                    AppConstants.THEME_DARK -> "暗色"
                    else -> "跟随系统"
                },
                onClick = { showThemeDialog = true },
                icon = Icons.Default.Palette
            )

            DynamicColorSettingItem(
                enabled = dynamicColorEnabled,
                onToggle = { ThemeStateManager.setDynamicColor(it) },
                onCustomColorClick = { showColorPickerDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "权限",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                title = "通知权限",
                subtitle = when {
                    hasNotificationPermission -> "已授权"
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "系统不支持"
                    else -> "未授权"
                },
                onClick = {
                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                showWarning = !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                icon = Icons.Default.Notifications
            )

            SettingsItem(
                title = "存储权限",
                subtitle = if (hasStoragePermission) "已授权" else "未授权",
                onClick = {
                    if (!hasStoragePermission) {
                        openAppSettings(context)
                    }
                },
                showWarning = !hasStoragePermission,
                icon = Icons.Default.Security
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "下载",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                title = "下载缓存位置",
                subtitle = currentPathDisplay,
                onClick = { showPathDialog = true },
                enabled = hasStoragePermission,
                icon = Icons.Default.Folder
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

    if (showPathDialog) {
        DownloadPathDialog(
            onPathSelected = { path ->
                DownloadPathManager.setCustomPath(context, path)
                currentPath = path
                showPathDialog = false
            },
            onResetToDefault = {
                DownloadPathManager.resetToDefault(context)
                currentPath = DownloadPathManager.getCurrentPath(context)
                showPathDialog = false
            },
            onDismiss = { showPathDialog = false }
        )
    }

    if (showColorPickerDialog) {
        ColorPickerDialog(
            onColorSelected = { colorValue ->
                ThemeStateManager.setCustomColorSeed(colorValue)
                showColorPickerDialog = false
            },
            onDismiss = { showColorPickerDialog = false }
        )
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWarning: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (enabled) Modifier.clickable { onClick() }
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (showWarning) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    tint = MaterialTheme.colorScheme.error
                )
            }
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
        Triple(AppConstants.THEME_LIGHT, "亮色", "使用亮色主题"),
        Triple(AppConstants.THEME_DARK, "暗色", "使用暗色主题"),
        Triple(AppConstants.THEME_SYSTEM, "跟随系统", "跟随系统设置")
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

@Composable
fun DownloadPathDialog(
    onPathSelected: (String) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedPathIndex by remember { mutableIntStateOf(-1) }
    var customSelectedPath by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(selectedUri, flags)
                customSelectedPath = selectedUri.toString()
                selectedPathIndex = -1
            } catch (e: SecurityException) {
                customSelectedPath = selectedUri.toString()
                selectedPathIndex = -1
            }
        }
    }

    val availablePaths = remember {
        listOf(
            Triple(0, "内部存储/电影", File(context.filesDir, Environment.DIRECTORY_MOVIES).absolutePath),
            Triple(1, "内部存储/下载", File(context.filesDir, Environment.DIRECTORY_DOWNLOADS).absolutePath),
            Triple(2, "外部存储/电影", context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.parentFile?.absolutePath ?: ""),
            Triple(3, "外部存储/下载", context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.parentFile?.absolutePath ?: "")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "下载缓存位置",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "选择存储位置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                availablePaths.forEach { (index, pathName, pathValue) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPathIndex = index
                                customSelectedPath = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = pathName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedPathIndex == index && customSelectedPath == null) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            folderPickerLauncher.launch(null)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (customSelectedPath != null) "已选择自定义位置" else "选择自定义位置",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (customSelectedPath != null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (customSelectedPath != null) {
                    Text(
                        text = customSelectedPath!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onResetToDefault,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("还原默认位置")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val path = when {
                        customSelectedPath != null -> {
                            val docFile = File(customSelectedPath!!)
                            docFile.absolutePath
                        }
                        selectedPathIndex in 0..3 -> availablePaths[selectedPathIndex].third
                        else -> ""
                    }
                    if (path.isNotEmpty()) {
                        onPathSelected(path)
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun DynamicColorSettingItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onCustomColorClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主题色",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (enabled) "跟随系统主题色" else "使用自定义主题色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            if (!enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自定义主题色",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "点击选择主题色",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Card(
                        modifier = Modifier
                            .clickable { onCustomColorClick() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorPreviewSquare()
                            Spacer(modifier = Modifier.width(8.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPreviewSquare() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small
        ) {}
    }
}

@Composable
fun ColorPickerDialog(
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colorOptions = listOf(
        0xFF6750A4 to "紫色",
        0xFF625B71 to "紫灰色",
        0xFF7D5260 to "粉红色",
        0xFFB00020 to "红色",
        0xFF006B3C to "绿色",
        0xFF006874 to "青色",
        0xFF0D7377 to "蓝绿色",
        0xFF1976D2 to "蓝色",
        0xFF00796B to "蓝绿色",
        0xFF5D4037 to "棕色"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择主题色",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "选择应用的主题色",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                colorOptions.forEach { (colorValue, colorName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onColorSelected(colorValue) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = androidx.compose.ui.graphics.Color(colorValue),
                            shape = MaterialTheme.shapes.small
                        ) {}
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = colorName,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
