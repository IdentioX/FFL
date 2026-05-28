package ru.ffl24.zakaz.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DriverColorScheme = lightColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    secondary = AppGray,
    onSecondary = AppTextDark,
    tertiary = AppReady,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceSoft,
    onSurfaceVariant = AppTextMuted,
    error = AppNotReady,
    onError = AppOnPrimary,
    primaryContainer = AppPrimaryDark,
    onPrimaryContainer = AppOnPrimary,
    errorContainer = AppNotReady,
    onErrorContainer = AppOnPrimary
)

private val DarkDriverColorScheme = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    secondary = AppGray,
    onSecondary = AppTextDark,
    tertiary = AppReady,
    background = AppDarkBackground,
    onBackground = AppDarkText,
    surface = AppDarkSurface,
    onSurface = AppDarkText,
    surfaceVariant = AppDarkSurfaceSoft,
    onSurfaceVariant = AppDarkTextMuted,
    error = AppNotReady,
    onError = AppOnPrimary,
    primaryContainer = AppPrimaryDark,
    onPrimaryContainer = AppOnPrimary,
    errorContainer = AppNotReady,
    onErrorContainer = AppOnPrimary
)

@Composable
fun FFl24Theme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkDriverColorScheme else DriverColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (LocalContext.current as? Activity)?.window
        if (window != null) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
