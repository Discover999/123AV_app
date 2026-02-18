package com.android123av.app.state

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.android123av.app.network.getPersistentCookieJar
import com.android123av.app.constants.AppConstants

object UserStateManager {
    private const val TAG = "UserStateManager"
    
    var isLoggedIn by mutableStateOf(false)
    var userId by mutableStateOf("")
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
    var loginError by mutableStateOf("")
    var rememberMe by mutableStateOf(false)
    var savedUsername by mutableStateOf("")
    var savedPassword by mutableStateOf("")
    
    private var sharedPreferences: SharedPreferences? = null
    private var loginSuccessListener: (() -> Unit)? = null
    
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(AppConstants.USER_PREFS_NAME, Context.MODE_PRIVATE)
        loadUserInfo()
    }
    
    private fun loadUserInfo() {
        sharedPreferences?.let { prefs ->
            val savedIsLoggedIn = prefs.getBoolean(AppConstants.KEY_IS_LOGGED_IN, false)
            val savedUserId = prefs.getString(AppConstants.KEY_USER_ID, "") ?: ""
            val savedUserName = prefs.getString(AppConstants.KEY_USER_NAME, "") ?: ""
            val savedUserEmail = prefs.getString(AppConstants.KEY_USER_EMAIL, "") ?: ""
            val prefsRememberMe = prefs.getBoolean(AppConstants.KEY_REMEMBER_ME, false)
            val prefsSavedUsername = prefs.getString(AppConstants.KEY_SAVED_USERNAME, "") ?: ""
            val prefsSavedPassword = prefs.getString(AppConstants.KEY_SAVED_PASSWORD, "") ?: ""
            
            rememberMe = prefsRememberMe
            savedUsername = prefsSavedUsername
            savedPassword = prefsSavedPassword
            
            if (savedIsLoggedIn) {
                isLoggedIn = savedIsLoggedIn
                userId = savedUserId
                userName = savedUserName
                userEmail = savedUserEmail
                
                CoroutineScope(Dispatchers.IO).launch {
                    val isValid = validateLoginStatus()
                    if (!isValid) {
                        onLogout()
                    }
                }
            } else {
                isLoggedIn = false
                userId = ""
                userName = ""
                userEmail = ""
            }
        }
    }
    
    private fun saveUserInfo() {
        sharedPreferences?.edit()?.apply {
            putBoolean(AppConstants.KEY_IS_LOGGED_IN, isLoggedIn)
            putString(AppConstants.KEY_USER_ID, userId)
            putString(AppConstants.KEY_USER_NAME, userName)
            putString(AppConstants.KEY_USER_EMAIL, userEmail)
            putBoolean(AppConstants.KEY_REMEMBER_ME, rememberMe)
            putString(AppConstants.KEY_SAVED_USERNAME, if (rememberMe) savedUsername else "")
            putString(AppConstants.KEY_SAVED_PASSWORD, if (rememberMe) savedPassword else "")
            apply()
        }
    }
    
    fun updateRememberMe(enabled: Boolean, username: String = "", password: String = "") {
        rememberMe = enabled
        if (enabled) {
            savedUsername = username
            savedPassword = password
        } else {
            savedUsername = ""
            savedPassword = ""
        }
        saveUserInfo()
    }
    
    fun clearSavedCredentials() {
        rememberMe = false
        savedUsername = ""
        savedPassword = ""
        saveUserInfo()
    }
    
    fun setLoginSuccessListener(listener: () -> Unit) {
        loginSuccessListener = listener
    }
    
    fun clearLoginSuccessListener() {
        loginSuccessListener = null
    }
    
    fun onLoginSuccess(username: String, userId: String = "1", email: String = "") {
        isLoggedIn = true
        this.userId = userId
        this.userName = username
        this.userEmail = email
        isLoggingIn = false
        loginError = ""
        saveUserInfo()
        loginSuccessListener?.invoke()
    }
    
    suspend fun validateLoginStatus(): Boolean {
        return try {
            val response = com.android123av.app.network.fetchUserInfo()
            if (response.isSuccess && response.result != null && response.result.user_id > 0) {
                userId = response.result.user_id.toString()
                userName = response.result.username
                userEmail = response.result.email
                saveUserInfo()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "validateLoginStatus failed: ${e.message}")
            false
        }
    }
    
    suspend fun fetchAndUpdateUserInfo() {
        try {
            val response = com.android123av.app.network.fetchUserInfo()
            if (response.isSuccess && response.result != null) {
                userId = response.result.user_id.toString()
                userName = response.result.username
                userEmail = response.result.email
                saveUserInfo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndUpdateUserInfo failed: ${e.message}")
        }
    }
    
    fun onLogout() {
        isLoggedIn = false
        userId = ""
        userName = ""
        userEmail = ""
        loginError = ""
        if (!rememberMe) {
            clearSavedCredentials()
        }
        saveUserInfo()
        
        getPersistentCookieJar()?.clearAllCookies()
    }
    
    fun updateLoginError(error: String) {
        loginError = error
        isLoggingIn = false
    }
    
    fun updateLoggingIn(loggingIn: Boolean) {
        isLoggingIn = loggingIn
    }
    
    fun getCurrentUserInfo(): Triple<String, String, String>? {
        return if (isLoggedIn && userId.isNotEmpty() && userName.isNotEmpty()) {
            Triple(userId, userName, userEmail)
        } else {
            null
        }
    }
}
