package com.app.myapplication.state

import androidx.compose.runtime.*
import com.app.myapplication.models.User
import com.app.myapplication.network.login
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// 用户状态管理类
class UserState {
    var isLoggedIn by mutableStateOf(false)
    var userId by mutableStateOf("")
    var userName by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginError by mutableStateOf("")
    
    // 登录方法
    fun performLogin(
        username: String, 
        password: String, 
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch {
            isLoggingIn = true
            loginError = ""
            try {
                val response = login(username, password)
                if (response.isSuccess) {
                    isLoggedIn = true
                    userId = "1" // 暂时使用固定ID，API未返回用户ID
                    userName = username // 暂时使用用户名作为显示名称
                } else {
                    loginError = response.message ?: "登录失败"
                }
            } catch (e: Exception) {
                loginError = "登录失败: ${e.message}"
            } finally {
                isLoggingIn = false
            }
        }
    }
    
    // 登出方法
    fun performLogout() {
        isLoggedIn = false
        userId = ""
        userName = ""
    }
    
    // 获取当前用户信息
    fun getCurrentUser(): User? {
        return if (isLoggedIn) {
            User(id = userId, name = userName)
        } else {
            null
        }
    }
}

// 为UserState提供Compose的remember函数
@Composable
fun rememberUserState(): UserState {
    return remember {
        UserState()
    }
}