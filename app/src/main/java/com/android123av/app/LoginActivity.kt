package com.android123av.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.android123av.app.ui.theme.MyApplicationTheme
import com.android123av.app.state.UserStateManager
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LoginScreen(
                    onLoginSuccess = {
                        // 登录成功，返回上一个activity
                        setResult(RESULT_OK)
                        finish()
                    },
                    onBack = {
                        // 用户点击返回或取消
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    // 使用共享的用户状态
    val isLoggingIn = UserStateManager.isLoggingIn
    val loginError = UserStateManager.loginError
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 页面标题
            Text(
                text = "用户登录",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // 用户名输入框
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true
            )
            
            // 密码输入框
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
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
                onClick = {
                    coroutineScope.launch {
                        UserStateManager.updateLoggingIn(true)
                        UserStateManager.updateLoginError("")
                        try {
                            val response = com.android123av.app.network.login(username, password)
                            if (response.isSuccess) {
                                // 登录成功后获取用户信息
                                try {
                                    println("DEBUG: Login success, waiting for cookies to be fully set...")
                                    // 等待一小段时间确保cookies完全设置
                                    kotlinx.coroutines.delay(500)
                                    
                                    println("DEBUG: Fetching user info after delay...")
                                    val userInfoResponse = com.android123av.app.network.fetchUserInfo()
                                    println("DEBUG: User info response - status: ${userInfoResponse.status}, result: ${userInfoResponse.result}")
                                    
                                    if (userInfoResponse.isSuccess && userInfoResponse.result != null && 
                                        userInfoResponse.result.user_id > 0 && userInfoResponse.result.username.isNotBlank()) {
                                        // 处理有效的用户信息
                                        println("DEBUG: User info success - username: ${userInfoResponse.result.username}, user_id: ${userInfoResponse.result.user_id}, email: ${userInfoResponse.result.email}")
                                        UserStateManager.onLoginSuccess(
                                            username = userInfoResponse.result.username,
                                            userId = userInfoResponse.result.user_id.toString(),
                                            email = userInfoResponse.result.email
                                        )
                                    } else {
                                        // 如果获取用户信息失败，重试一次
                                        println("DEBUG: User info empty or invalid, retrying after another delay...")
                                        kotlinx.coroutines.delay(1000)
                                        val retryResponse = com.android123av.app.network.fetchUserInfo()
                                        
                                        if (retryResponse.isSuccess && retryResponse.result != null && 
                                            retryResponse.result.user_id > 0 && retryResponse.result.username.isNotBlank()) {
                                            // 处理重试后有效的用户信息
                                            println("DEBUG: Retry user info success - username: ${retryResponse.result.username}, user_id: ${retryResponse.result.user_id}, email: ${retryResponse.result.email}")
                                            UserStateManager.onLoginSuccess(
                                                username = retryResponse.result.username,
                                                userId = retryResponse.result.user_id.toString(),
                                                email = retryResponse.result.email
                                            )
                                        } else {
                                            // 如果重试也失败，使用基本登录信息
                                            println("DEBUG: Retry user info also failed, using basic login info")
                                            UserStateManager.onLoginSuccess(username)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 获取用户信息失败，但仍然使用基本登录信息
                                    println("DEBUG: User info exception: ${e.message}")
                                    UserStateManager.onLoginSuccess(username)
                                }
                                onLoginSuccess()
                            } else {
                                UserStateManager.updateLoginError(response.message ?: "登录失败")
                            }
                        } catch (e: Exception) {
                            UserStateManager.updateLoginError("登录失败: ${e.message}")
                        } finally {
                            UserStateManager.updateLoggingIn(false)
                        }
                    }
                },
                enabled = !UserStateManager.isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                if (UserStateManager.isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登录")
                }
            }
            
            // 返回按钮
            TextButton(onClick = onBack) {
                Text("取消")
            }
        }
    }
}