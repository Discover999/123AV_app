package com.android123av.app.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.android123av.app.state.ThemeStateManager
import com.android123av.app.constants.AppConstants
import androidx.compose.ui.platform.LocalConfiguration

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private fun generateTonalPalette(seedColor: Color): Map<String, Color> {
    val color = seedColor.copy(alpha = 1f)
    val isLightSeed = color.red + color.green + color.blue > 1.5f
    val onColor = if (isLightSeed) Color(0xFF381E72) else Color.White
    val onColorContainer = if (isLightSeed) color else Color(0xFFEADDFF)
    val containerColor = if (isLightSeed) color.copy(alpha = 0.12f) else color.copy(alpha = 0.24f)
    return mapOf(
        "primary" to color,
        "onPrimary" to onColor,
        "primaryContainer" to containerColor,
        "onPrimaryContainer" to onColorContainer,
        "secondary" to PurpleGrey40,
        "onSecondary" to Color.White,
        "secondaryContainer" to PurpleGrey40.copy(alpha = 0.12f),
        "onSecondaryContainer" to PurpleGrey40,
        "tertiary" to Pink40,
        "onTertiary" to Color.White,
        "tertiaryContainer" to Pink40.copy(alpha = 0.12f),
        "onTertiaryContainer" to Pink40,
        "background" to Color(0xFFFFFBFE),
        "onBackground" to Color(0xFF1C1B1F),
        "surface" to Color(0xFFFFFBFE),
        "onSurface" to Color(0xFF1C1B1F),
        "surfaceVariant" to Color(0xFFE7E0EC),
        "onSurfaceVariant" to Color(0xFF49454F),
        "outline" to Color(0xFF79747E),
        "outlineVariant" to Color(0xFFCAC4D0),
        "error" to Color(0xFFB3261E),
        "onError" to Color.White,
        "errorContainer" to Color(0xFFF9DEDC),
        "onErrorContainer" to Color(0xFF410E0B)
    )
}

private fun generateDarkTonalPalette(seedColor: Color): Map<String, Color> {
    val color = seedColor.copy(alpha = 1f)
    val onColor = Color.White
    val onPrimaryContainer = Color(0xFFEADDFF)
    return mapOf(
        "primary" to color,
        "onPrimary" to onColor,
        "primaryContainer" to color.copy(alpha = 0.24f),
        "onPrimaryContainer" to onPrimaryContainer,
        "secondary" to PurpleGrey80,
        "onSecondary" to Color(0xFF332D41),
        "secondaryContainer" to PurpleGrey80.copy(alpha = 0.12f),
        "onSecondaryContainer" to PurpleGrey80,
        "tertiary" to Pink80,
        "onTertiary" to Color(0xFF492532),
        "tertiaryContainer" to Pink80.copy(alpha = 0.12f),
        "onTertiaryContainer" to Pink80,
        "background" to Color(0xFF1C1B1F),
        "onBackground" to Color(0xFFE6E1E5),
        "surface" to Color(0xFF1C1B1F),
        "onSurface" to Color(0xFFE6E1E5),
        "surfaceVariant" to Color(0xFF49454F),
        "onSurfaceVariant" to Color(0xFFCAC4D0),
        "outline" to Color(0xFF938F99),
        "outlineVariant" to Color(0xFF49454F),
        "error" to Color(0xFFF2B8B5),
        "onError" to Color(0xFF601410),
        "errorContainer" to Color(0xFF8C1D18),
        "onErrorContainer" to Color(0xFFF9DEDC)
    )
}

@Composable
fun MyApplicationTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeMode by ThemeStateManager.currentTheme.collectAsState()
    val useDynamicColor by ThemeStateManager.dynamicColor.collectAsState()
    val customColorSeed by ThemeStateManager.customColorSeed.collectAsState()
    val isDynamicColorEnabled = dynamicColor && useDynamicColor
    val isDarkTheme = when (themeMode) {
        AppConstants.THEME_LIGHT -> false
        AppConstants.THEME_DARK -> true
        else -> {
            val configuration = LocalConfiguration.current
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    val colorScheme = when {
        isDynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        !useDynamicColor -> {
            val seedColor = Color(customColorSeed.toInt())
            if (isDarkTheme) {
                val palette = generateDarkTonalPalette(seedColor)
                darkColorScheme(
                    primary = palette["primary"]!!,
                    onPrimary = palette["onPrimary"]!!,
                    primaryContainer = palette["primaryContainer"]!!,
                    onPrimaryContainer = palette["onPrimaryContainer"]!!,
                    secondary = palette["secondary"]!!,
                    onSecondary = palette["onSecondary"]!!,
                    secondaryContainer = palette["secondaryContainer"]!!,
                    onSecondaryContainer = palette["onSecondaryContainer"]!!,
                    tertiary = palette["tertiary"]!!,
                    onTertiary = palette["onTertiary"]!!,
                    tertiaryContainer = palette["tertiaryContainer"]!!,
                    onTertiaryContainer = palette["onTertiaryContainer"]!!,
                    background = palette["background"]!!,
                    onBackground = palette["onBackground"]!!,
                    surface = palette["surface"]!!,
                    onSurface = palette["onSurface"]!!,
                    surfaceVariant = palette["surfaceVariant"]!!,
                    onSurfaceVariant = palette["onSurfaceVariant"]!!,
                    outline = palette["outline"]!!,
                    outlineVariant = palette["outlineVariant"]!!,
                    error = palette["error"]!!,
                    onError = palette["onError"]!!,
                    errorContainer = palette["errorContainer"]!!,
                    onErrorContainer = palette["onErrorContainer"]!!
                )
            } else {
                val palette = generateTonalPalette(seedColor)
                lightColorScheme(
                    primary = palette["primary"]!!,
                    onPrimary = palette["onPrimary"]!!,
                    primaryContainer = palette["primaryContainer"]!!,
                    onPrimaryContainer = palette["onPrimaryContainer"]!!,
                    secondary = palette["secondary"]!!,
                    onSecondary = palette["onSecondary"]!!,
                    secondaryContainer = palette["secondaryContainer"]!!,
                    onSecondaryContainer = palette["onSecondaryContainer"]!!,
                    tertiary = palette["tertiary"]!!,
                    onTertiary = palette["onTertiary"]!!,
                    tertiaryContainer = palette["tertiaryContainer"]!!,
                    onTertiaryContainer = palette["onTertiaryContainer"]!!,
                    background = palette["background"]!!,
                    onBackground = palette["onBackground"]!!,
                    surface = palette["surface"]!!,
                    onSurface = palette["onSurface"]!!,
                    surfaceVariant = palette["surfaceVariant"]!!,
                    onSurfaceVariant = palette["onSurfaceVariant"]!!,
                    outline = palette["outline"]!!,
                    outlineVariant = palette["outlineVariant"]!!,
                    error = palette["error"]!!,
                    onError = palette["onError"]!!,
                    errorContainer = palette["errorContainer"]!!,
                    onErrorContainer = palette["onErrorContainer"]!!
                )
            }
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
