package com.android123av.app.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import com.android123av.app.AppDestinations

data class CategoryNavigation(
    val title: String,
    val href: String
)

class AppState(
    initialDestination: AppDestinations = AppDestinations.HOME
) {
    val currentDestination: MutableState<AppDestinations> = mutableStateOf(initialDestination)
    
    val categoryNavigation: MutableState<CategoryNavigation?> = mutableStateOf(null)
    
    fun navigateTo(destination: AppDestinations) {
        currentDestination.value = destination
    }
    
    fun navigateToLogin() {
        currentDestination.value = AppDestinations.LOGIN
    }
    
    fun navigateBackFromLogin() {
        currentDestination.value = AppDestinations.PROFILE
    }
    
    fun navigateToCategory(title: String, href: String) {
        categoryNavigation.value = CategoryNavigation(title, href)
    }
    
    fun navigateBackFromCategory() {
        categoryNavigation.value = null
    }
    
    companion object {
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

@Composable
fun rememberAppState(
    initialDestination: AppDestinations = AppDestinations.HOME
): AppState {
    return rememberSaveable(saver = AppState.Saver) {
        AppState(initialDestination = initialDestination)
    }
}



