package com.android123av.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.android123av.app.models.User
import com.android123av.app.state.UserStateManager

// 用户信息栏组件
@Composable
fun UserInfoSection(
    isLoggedIn: Boolean,
    userName: String?,
    userEmail: String?,
    userId: String?,
    onClick: () -> Unit
) {
    println("DEBUG: UserInfoSection - isLoggedIn: $isLoggedIn, userName: $userName, userEmail: $userEmail, userId: $userId")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧用户信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (isLoggedIn) {
                    // 用户名
                    Text(
                        text = userName ?: "未知用户",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // 邮箱
                    if (!userEmail.isNullOrEmpty()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    // 用户ID
                    if (!userId.isNullOrEmpty()) {
                        Text(
                            text = "ID: $userId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } else {
                    // 未登录状态
                    Text(
                        text = "请先登录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 右侧头像
            Icon(
                imageVector = Icons.Default.AccountBox,
                contentDescription = "用户头像",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// 个人资料屏幕
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier,
    isLoggedIn: Boolean,
    user: User?,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    println("DEBUG: ProfileScreen composable - isLoggedIn: $isLoggedIn, user: $user")
    println("DEBUG: ProfileScreen - UserStateManager.isLoggedIn: ${UserStateManager.isLoggedIn}")
    println("DEBUG: ProfileScreen - UserStateManager.userName: ${UserStateManager.userName}")
    println("DEBUG: ProfileScreen - UserStateManager.userEmail: ${UserStateManager.userEmail}")
    println("DEBUG: ProfileScreen - UserStateManager.userId: ${UserStateManager.userId}")
    
    // 关于对话框状态
    var showAboutDialog by remember { mutableStateOf(false) }
    // 注销确认对话框状态
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
        // 用户信息栏
        println("DEBUG: ProfileScreen - isLoggedIn: $isLoggedIn, userName: ${UserStateManager.userName}, userEmail: ${UserStateManager.userEmail}, userId: ${UserStateManager.userId}")
        UserInfoSection(
            isLoggedIn = isLoggedIn,
            userName = UserStateManager.userName,
            userEmail = UserStateManager.userEmail,
            userId = UserStateManager.userId,
            onClick = {
                if (isLoggedIn) {
                    // 已登录状态下点击显示注销确认对话框
                    showLogoutDialog = true
                } else {
                    // 未登录状态下点击跳转到登录页面
                    onNavigateToLogin()
                }
            }
        )
        
        // 主要内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 移除显眼的退出登录按钮，只保留点击用户信息card的注销功能
            
            // 关于部分 - 点击打开对话框
            Spacer(modifier = Modifier.height(32.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "关于",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }
        }
    }
    
    // 关于对话框
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
    
    // 注销确认对话框
    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
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
                    text = "版本 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "这是一个视频播放应用",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("确定")
            }
        },
        dismissButton = null
    )
}

@Composable
fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确认注销",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "您确定要退出登录吗？",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}


