package com.app.myapplication.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import com.app.myapplication.AppDestinations

class AppState(
    initialDestination: AppDestinations = AppDestinations.HOME
) {
    // 当前导航目的地
    val currentDestination: MutableState<AppDestinations> = mutableStateOf(initialDestination)
    
    // 导航到指定目的地
    fun navigateTo(destination: AppDestinations) {
        currentDestination.value = destination
    }
    
    // 导航到登录页面
    fun navigateToLogin() {
        currentDestination.value = AppDestinations.LOGIN
    }
    
    // 从登录页面返回
    fun navigateBackFromLogin() {
        currentDestination.value = AppDestinations.PROFILE
    }
    
    companion object {
        // Saver用于保存和恢复AppState的状态
        val Saver: Saver<AppState, *> = Saver(
            save = { appState ->
                mapOf(
                    "currentDestination" to appState.currentDestination.value.name
                )
            },
            restore = { data ->
                AppState(
                    initialDestination = AppDestinations.valueOf(data["currentDestination"] as String)
                )
            }
        )
    }
}

// 用于在Composable中记住AppState的函数
@Composable
fun rememberAppState(
    initialDestination: AppDestinations = AppDestinations.HOME
): AppState {
    return rememberSaveable(saver = AppState.Saver) {
        AppState(initialDestination = initialDestination)
    }
}
