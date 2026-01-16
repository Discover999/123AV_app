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
import com.android123av.app.constants.AppConstants

object UserStateManager {
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
        println("DEBUG: Initializing UserStateManager...")
        sharedPreferences = context.getSharedPreferences(AppConstants.USER_PREFS_NAME, Context.MODE_PRIVATE)
        println("DEBUG: SharedPreferences initialized: ${sharedPreferences != null}")
        
        val savedUserId = sharedPreferences?.getString(AppConstants.KEY_USER_ID, "") ?: ""
        val savedUserName = sharedPreferences?.getString(AppConstants.KEY_USER_NAME, "") ?: ""
        val savedIsLoggedIn = sharedPreferences?.getBoolean(AppConstants.KEY_IS_LOGGED_IN, false) ?: false
        println("DEBUG: Before loadUserInfo - savedIsLoggedIn: $savedIsLoggedIn, savedUserId: $savedUserId, savedUserName: $savedUserName")
        
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
            
            println("DEBUG: Loaded from SharedPreferences - isLoggedIn: $savedIsLoggedIn, userId: $savedUserId, userName: $savedUserName, userEmail: $savedUserEmail")
            
            rememberMe = prefsRememberMe
            savedUsername = prefsSavedUsername
            savedPassword = prefsSavedPassword
            
            if (savedIsLoggedIn) {
                println("DEBUG: User appears logged in, validating login status...")
                isLoggedIn = savedIsLoggedIn
                userId = savedUserId
                userName = savedUserName
                userEmail = savedUserEmail
                
                CoroutineScope(Dispatchers.IO).launch {
                    val isValid = validateLoginStatus()
                    if (!isValid) {
                        println("DEBUG: Login status is invalid, clearing saved data")
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
    
    private fun saveUserInfo() {
        println("DEBUG: Saving to SharedPreferences - isLoggedIn: $isLoggedIn, userId: $userId, userName: $userName, userEmail: $userEmail")
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
    
    suspend fun validateLoginStatus(): Boolean {
        return try {
            val response = com.android123av.app.network.fetchUserInfo()
            println("DEBUG: validateLoginStatus - status: ${response.status}, result: ${response.result}")
            if (response.isSuccess && response.result != null && response.result.user_id > 0) {
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
            println("DEBUG: fetchUserInfo exception: ${e.message}")
            e.printStackTrace()
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
        println("DEBUG: getCurrentUserInfo called - isLoggedIn: $isLoggedIn, userId: $userId, userName: $userName, userEmail: $userEmail")
        return if (isLoggedIn && userId.isNotEmpty() && userName.isNotEmpty()) {
            Triple(userId, userName, userEmail)
        } else {
            null
        }
    }
}
