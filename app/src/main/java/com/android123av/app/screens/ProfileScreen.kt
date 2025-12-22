package com.android123av.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.android123av.app.models.User

// 个人资料屏幕
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean,
    user: User?,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoggedIn) {
            // 登录后的界面
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBox,
                    contentDescription = "用户头像",
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = user?.name ?: "未知用户",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onLogout) {
                    Text("退出登录")
                }
            }
            
            // 关于部分
            Spacer(modifier = Modifier.height(64.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "关于",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "版本 1.0.0",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "这是一个视频播放应用",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            // 未登录界面（符合设计图要求）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNavigateToLogin()
                    }
            ) {
                Text(
                    text = "请先登录",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Icon(
                    imageVector = Icons.Default.AccountBox,
                    contentDescription = "用户头像",
                    modifier = Modifier.size(80.dp)
                )
                Divider(modifier = Modifier.padding(top = 16.dp).fillMaxWidth())
            }
        }
    }
}


