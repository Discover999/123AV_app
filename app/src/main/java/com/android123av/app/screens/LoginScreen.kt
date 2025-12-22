package com.android123av.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

// 登录二级页面
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    isLoggingIn: Boolean,
    loginError: String,
    onLogin: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 页面标题
        Text(
            text = "登录",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // 用户名输入框
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
        
        // 密码输入框
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            visualTransformation = PasswordVisualTransformation()
        )
        
        // 错误信息
        if (loginError.isNotBlank()) {
            Text(
                text = loginError,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // 登录按钮
        Button(
            onClick = { onLogin(username, password) },
            enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("登录")
            }
        }
        
        // 返回按钮
        TextButton(onClick = onBack) {
            Text("返回")
        }
    }
}


