package com.emisferia.proactive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EmisferiaDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkBackground,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = TextPrimary,

    secondary = NeonPurple,
    onSecondary = DarkBackground,
    secondaryContainer = NeonPurpleDark,
    onSecondaryContainer = TextPrimary,

    tertiary = NeonPink,
    onTertiary = DarkBackground,
    tertiaryContainer = NeonPinkDark,
    onTertiaryContainer = TextPrimary,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFF3D0000),
    onErrorContainer = Color(0xFFFFB4AB),

    outline = TextMuted,
    outlineVariant = Color(0xFF2A2A34)
)

@Composable
fun EmisferiaProactiveTheme(
    darkTheme: Boolean = true, // Always dark for neon effect
    content: @Composable () -> Unit
) {
    val colorScheme = EmisferiaDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EmisferiaTypography,
        content = content
    )
}
