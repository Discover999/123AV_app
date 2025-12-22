package com.android123av.app.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.android123av.app.AppDestinations

// 导航栏组件
@Composable
fun AppNavigationBar(
    currentDestination: AppDestinations,
    onNavigateTo: (AppDestinations) -> Unit
) {
    NavigationBar {
        // 底部导航栏的主要目的地
        listOf(AppDestinations.HOME, AppDestinations.SEARCH, AppDestinations.FAVORITES, AppDestinations.PROFILE).forEach {
            NavigationBarItem(
                icon = { Icon(it.icon, contentDescription = it.label) },
                label = { Text(it.label) },
                selected = it == currentDestination,
                onClick = { onNavigateTo(it) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}



