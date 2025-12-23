package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.android123av.app.network.getPersistentCookieJar

// 全局用户状态管理器 - 单例模式
object UserStateManager {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    
    private var sharedPreferences: SharedPreferences? = null
    
    var isLoggedIn by mutableStateOf(false)
    var userId by mutableStateOf("")
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginError by mutableStateOf("")
    
    // 登录成功时的回调监听器
    private var loginSuccessListener: (() -> Unit)? = null
    
    // 初始化SharedPreferences
    fun initialize(context: Context) {
        println("DEBUG: Initializing UserStateManager...")
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        println("DEBUG: SharedPreferences initialized: ${sharedPreferences != null}")
        
        // 检查是否有保存的cookies（用于调试）
        val savedUserId = sharedPreferences?.getString(KEY_USER_ID, "") ?: ""
        val savedUserName = sharedPreferences?.getString(KEY_USER_NAME, "") ?: ""
        val savedIsLoggedIn = sharedPreferences?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
        println("DEBUG: Before loadUserInfo - savedIsLoggedIn: $savedIsLoggedIn, savedUserId: $savedUserId, savedUserName: $savedUserName")
        
        loadUserInfo()
    }
    
    // 从SharedPreferences加载用户信息
    private fun loadUserInfo() {
        sharedPreferences?.let { prefs ->
            val savedIsLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
            val savedUserId = prefs.getString(KEY_USER_ID, "") ?: ""
            val savedUserName = prefs.getString(KEY_USER_NAME, "") ?: ""
            val savedUserEmail = prefs.getString(KEY_USER_EMAIL, "") ?: ""
            
            println("DEBUG: Loaded from SharedPreferences - isLoggedIn: $savedIsLoggedIn, userId: $savedUserId, userName: $savedUserName, userEmail: $savedUserEmail")
            
            // 如果已登录，验证登录状态是否仍然有效
            if (savedIsLoggedIn) {
                println("DEBUG: User appears logged in, validating login status...")
                // 先恢复保存的状态
                isLoggedIn = savedIsLoggedIn
                userId = savedUserId
                userName = savedUserName
                userEmail = savedUserEmail
                
                CoroutineScope(Dispatchers.IO).launch {
                    // 验证登录状态是否仍然有效
                    val isValid = validateLoginStatus()
                    if (!isValid) {
                        println("DEBUG: Login status is invalid, clearing saved data")
                        // 如果登录状态无效，清除保存的数据
                        onLogout()
                    } else {
                        println("DEBUG: Login status is valid, user data restored")
                    }
                }
            } else {
                println("DEBUG: User is not logged in")
                isLoggedIn = false
                userId = ""
                userName = ""
                userEmail = ""
            }
        } ?: run {
            println("DEBUG: SharedPreferences is null, cannot load user info")
        }
    }
    
    // 保存用户信息到SharedPreferences
    private fun saveUserInfo() {
        println("DEBUG: Saving to SharedPreferences - isLoggedIn: $isLoggedIn, userId: $userId, userName: $userName, userEmail: $userEmail")
        sharedPreferences?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_EMAIL, userEmail)
            apply()
        }
    }
    
    // 设置登录成功监听器
    fun setLoginSuccessListener(listener: () -> Unit) {
        loginSuccessListener = listener
    }
    
    // 清除登录成功监听器
    fun clearLoginSuccessListener() {
        loginSuccessListener = null
    }
    
    // 执行登录成功操作
    fun onLoginSuccess(username: String, userId: String = "1", email: String = "") {
        println("DEBUG: onLoginSuccess called - username: $username, userId: $userId, email: $email")
        isLoggedIn = true
        this.userId = userId
        this.userName = username
        this.userEmail = email
        isLoggingIn = false
        loginError = ""
        saveUserInfo()
        loginSuccessListener?.invoke()
    }
    
    // 检查是否真正有有效的cookies（用于验证登录状态）
    suspend fun validateLoginStatus(): Boolean {
        return try {
            val response = com.android123av.app.network.fetchUserInfo()
            println("DEBUG: validateLoginStatus - status: ${response.status}, result: ${response.result}")
            if (response.isSuccess && response.result != null && response.result.user_id > 0) {
                // 有有效的用户信息，更新本地数据
                userId = response.result.user_id.toString()
                userName = response.result.username
                userEmail = response.result.email
                println("DEBUG: Login status validated - ID: $userId, Name: $userName, Email: $userEmail")
                saveUserInfo()
                true
            } else {
                println("DEBUG: Login status invalid - no valid user info")
                false
            }
        } catch (e: Exception) {
            println("DEBUG: validateLoginStatus exception: ${e.message}")
            false
        }
    }
    
    // 获取用户信息
    suspend fun fetchAndUpdateUserInfo() {
        try {
            val response = com.android123av.app.network.fetchUserInfo()
            println("DEBUG: fetchUserInfo response - status: ${response.status}, result: ${response.result}")
            if (response.isSuccess && response.result != null) {
                userId = response.result.user_id.toString()
                userName = response.result.username
                userEmail = response.result.email
                println("DEBUG: Updated user info - ID: $userId, Name: $userName, Email: $userEmail")
                saveUserInfo()
            } else {
                println("DEBUG: fetchUserInfo failed or result is null")
            }
        } catch (e: Exception) {
            // 获取用户信息失败，但不影响登录状态
            println("DEBUG: fetchUserInfo exception: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 执行登出操作
    fun onLogout() {
        isLoggedIn = false
        userId = ""
        userName = ""
        userEmail = ""
        loginError = ""
        saveUserInfo()
        
        // 清除cookies，确保下次打开app不会自动登录
        getPersistentCookieJar()?.clearAllCookies()
    }
    
    // 设置登录错误
    fun updateLoginError(error: String) {
        loginError = error
        isLoggingIn = false
    }
    
    // 设置登录中状态
    fun updateLoggingIn(loggingIn: Boolean) {
        isLoggingIn = loggingIn
    }
    
    // 获取当前用户信息
    fun getCurrentUserInfo(): Triple<String, String, String>? {
        println("DEBUG: getCurrentUserInfo called - isLoggedIn: $isLoggedIn, userId: $userId, userName: $userName, userEmail: $userEmail")
        return if (isLoggedIn && userId.isNotEmpty() && userName.isNotEmpty()) {
            Triple(userId, userName, userEmail)
        } else {
            null
        }
    }
}