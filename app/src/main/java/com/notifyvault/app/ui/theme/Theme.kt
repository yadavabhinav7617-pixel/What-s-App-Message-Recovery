package com.notifyvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF14532D),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF082F49),
    background = Color(0xFF081120),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF1F2937)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6D5EF7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF10B981),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF0EA5E9),
    onTertiary = Color(0xFF082F49),
    background = Color(0xFFF5F7FF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun NotifyVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
