package com.android123av.app.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android123av.app.network.editUserProfile
import com.android123av.app.state.UserStateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileHeader(
    isLoggedIn: Boolean,
    userName: String?,
    userEmail: String?,
    onHeaderClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onHeaderClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLoggedIn) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) userName ?: "未知用户" else "点击登录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (isLoggedIn && !userEmail.isNullOrEmpty()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (!isLoggedIn) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "登录后享受更多功能",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            if (isLoggedIn) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(12.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 顶部组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    context: Context,
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToNetworkTest: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {}
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
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
        ) {
            ProfileHeader(
                isLoggedIn = isLoggedIn,
                userName = UserStateManager.userName,
                userEmail = UserStateManager.userEmail,
                onHeaderClick = {
                    if (isLoggedIn) {
                        showOptionsDialog = true
                    } else {
                        onNavigateToLogin()
                    }
                }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = isLoggedIn,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "功能",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        MenuItem(
                            icon = Icons.Default.Download,
                            title = "下载管理",
                            subtitle = "已下载视频",
                            onClick = { onNavigateToDownloads() }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isLoggedIn) "账户" else "常用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                MenuItem(
                    icon = Icons.Default.Settings,
                    title = "设置",
                    subtitle = "应用设置",
                    onClick = { onNavigateToSettings() }
                )

                MenuItem(
                    icon = Icons.Default.Wifi,
                    title = "可用性检测",
                    subtitle = "检测服务器连接状态",
                    onClick = { onNavigateToNetworkTest() }
                )
                
                MenuItem(
                    icon = Icons.Default.Help,
                    title = "帮助与反馈",
                    subtitle = "常见问题",
                    onClick = { onNavigateToHelp() }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                MenuItem(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "版本信息",
                    onClick = { showAboutDialog = true }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    
    if (showAboutDialog) {
        AboutDialog(context = context, onDismiss = { showAboutDialog = false })
    }
    
    if (showOptionsDialog) {
        ProfileOptionsDialog(
            onDismiss = { showOptionsDialog = false },
            onEditProfile = {
                showOptionsDialog = false
                showEditProfileDialog = true
            },
            onLogout = {
                showOptionsDialog = false
                showLogoutConfirmDialog = true
            }
        )
    }
    
    if (showLogoutConfirmDialog) {
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutConfirmDialog = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmDialog = false }
        )
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            onDismiss = { showEditProfileDialog = false },
            onSuccess = { }
        )
    }
}

// ==================== 对话框组件 ====================

@Composable
fun AboutDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val versionName = try {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "版本 ${packageInfo.versionName}"
    } catch (e: Exception) {
        "版本 Unknow"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "关于",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "123AV是一个专注于提供优质视频内容的视频网站。本应用程序与 www.123av.com 及其关联方无任何隶属、合作或授权关系，特此声明！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "本应用提供的所有内容仅用于：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "📚 技术研究学习",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "✨ 移动端用户体验优化",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "🚫 非商业用途展示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "官方网站：www.123av.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.123av.com"))
                        context.startActivity(intent)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        dismissButton = null
    )
}

@Composable
fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = "确定要退出登录吗？",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
fun ProfileOptionsDialog(
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit
) {
    var action by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(action) {
        if (action != null) {
            onDismiss()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { action = "edit" },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "编辑资料",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { action = "logout" },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "退出登录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
    
    LaunchedEffect(action) {
        when (action) {
            "edit" -> onEditProfile()
            "logout" -> onLogout()
        }
    }
}

@Composable
fun EditProfileDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var username by remember { mutableStateOf(UserStateManager.userName) }
    var email by remember { mutableStateOf(UserStateManager.userEmail) }
    var password by remember { mutableStateOf("") }
    var showPasswordField by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isLoading, dismissOnClickOutside = !isLoading)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "编辑资料",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("电子邮箱") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { showPasswordField = !showPasswordField },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = if (showPasswordField) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("修改密码")
                }

                if (showPasswordField) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isLoading
                    )
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                successMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || email.isBlank()) {
                            errorMessage = "用户名和邮箱不能为空"
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        successMessage = null
                        coroutineScope.launch {
                            val response = editUserProfile(username, email)
                            isLoading = false
                            if (response.status == 200 && response.result == true) {
                                UserStateManager.userName = username
                                UserStateManager.userEmail = email
                                successMessage = "修改成功"
                                kotlinx.coroutines.delay(1000)
                                onSuccess()
                                onDismiss()
                            } else {
                                errorMessage = response.messages?.all?.firstOrNull() ?: "修改失败"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && username.isNotBlank() && email.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("确认修改")
                }

                if (!isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}


