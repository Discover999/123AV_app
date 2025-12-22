package com.app.myapplication.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.app.myapplication.AppDestinations

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
