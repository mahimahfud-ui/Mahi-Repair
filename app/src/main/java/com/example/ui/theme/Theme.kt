package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MahiDarkColorScheme = darkColorScheme(
    primary = MahiRedPrimary,
    onPrimary = Color.White,
    primaryContainer = MahiRedDark,
    onPrimaryContainer = Color.White,
    secondary = MahiRedPrimary,
    onSecondary = Color.White,
    background = MahiBlack,
    onBackground = MahiTextPrimary,
    surface = MahiSurfaceDark,
    onSurface = MahiTextPrimary,
    surfaceVariant = MahiCardSurface,
    onSurfaceVariant = MahiTextSecondary,
    outline = MahiCardStroke,
    outlineVariant = MahiCardStroke
)

@Composable
fun MahiRepairTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MahiDarkColorScheme,
        typography = Typography,
        content = content
    )
}
