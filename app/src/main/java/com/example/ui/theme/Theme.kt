package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MahiDarkColorScheme = darkColorScheme(
    primary = MahiAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF243D70),
    onPrimaryContainer = Color(0xFFE6EEFF),
    secondary = MahiAccentLight,
    onSecondary = Color(0xFF07101F),
    tertiary = MahiSuccessGreen,
    onTertiary = Color(0xFF062117),
    error = MahiRedPrimary,
    onError = Color.White,
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
fun MahiRepairTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMahiColors provides DefaultMahiColors
    ) {
        MaterialTheme(
            colorScheme = MahiDarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
