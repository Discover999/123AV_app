package com.android123av.app.state

import androidx.compose.runtime.*
import com.android123av.app.models.User
import com.android123av.app.network.login
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UserState {
    val isLoggedIn: Boolean get() = UserStateManager.isLoggedIn
    val userId: String get() = UserStateManager.userId
    val userName: String get() = UserStateManager.userName
    val isLoggingIn: Boolean get() = UserStateManager.isLoggingIn
    val loginError: String get() = UserStateManager.loginError
    
    init {
        UserStateManager.setLoginSuccessListener {
        }
    }
    
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
    
    fun performLogout() {
        UserStateManager.onLogout()
    }
    
    fun getCurrentUser(): User? {
        val userInfo = UserStateManager.getCurrentUserInfo()
        return userInfo?.let { (id, name, email) ->
            User(id = id, name = name, email = email)
        }
    }
}

@Composable
fun rememberUserState(): UserState {
    return remember { UserState() }
}
