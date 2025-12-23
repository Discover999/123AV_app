package com.android123av.app.state

import androidx.compose.runtime.*
import com.android123av.app.models.User
import com.android123av.app.network.login
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// 用户状态管理类 - 使用共享的UserStateManager
class UserState {
    // 使用共享状态管理器的属性
    val isLoggedIn: Boolean get() = UserStateManager.isLoggedIn
    val userId: String get() = UserStateManager.userId
    val userName: String get() = UserStateManager.userName
    val isLoggingIn: Boolean get() = UserStateManager.isLoggingIn
    val loginError: String get() = UserStateManager.loginError
    
    init {
        println("DEBUG: UserState initialized - isLoggedIn: $isLoggedIn, userId: $userId, userName: $userName")
        // 同步状态
        UserStateManager.setLoginSuccessListener {
            // 状态变化监听器
            println("DEBUG: Login success listener triggered")
        }
    }
    
    // 登录方法
    fun performLogin(
        username: String, 
        password: String, 
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch {
            UserStateManager.updateLoggingIn(true)
            UserStateManager.updateLoginError("")
            try {
                val response = login(username, password)
                if (response.isSuccess) {
                    UserStateManager.onLoginSuccess(username)
                } else {
                    UserStateManager.updateLoginError(response.message ?: "登录失败")
                }
            } catch (e: Exception) {
                UserStateManager.updateLoginError("登录失败: ${e.message}")
            } finally {
                UserStateManager.updateLoggingIn(false)
            }
        }
    }
    
    // 登出方法
    fun performLogout() {
        UserStateManager.onLogout()
    }
    
    // 获取当前用户信息
    fun getCurrentUser(): User? {
        val userInfo = UserStateManager.getCurrentUserInfo()
        println("DEBUG: UserState.getCurrentUser() - userInfo: $userInfo")
        return userInfo?.let { (id, name, email) ->
            User(id = id, name = name, email = email)
        }
    }
}

// 为UserState提供Compose的remember函数
@Composable
fun rememberUserState(): UserState {
    println("DEBUG: rememberUserState called")
    return remember {
        println("DEBUG: Creating new UserState instance")
        UserState()
    }
}


