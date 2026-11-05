package com.mahi.repair.designsystem.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal val LocalMahiColors = staticCompositionLocalOf { MahiDarkColors }
internal val LocalMahiSpacing = staticCompositionLocalOf { MahiSpacing() }
internal val LocalMahiShapes = staticCompositionLocalOf { MahiShapes() }
internal val LocalMahiTypography = staticCompositionLocalOf { MahiTypography() }

/** Access tokens anywhere inside [MahiTheme]: `MahiTheme.colors.accent`. */
object MahiTheme {
    val colors: MahiColors @Composable @ReadOnlyComposable get() = LocalMahiColors.current
    val spacing: MahiSpacing @Composable @ReadOnlyComposable get() = LocalMahiSpacing.current
    val shapes: MahiShapes @Composable @ReadOnlyComposable get() = LocalMahiShapes.current
    val typography: MahiTypography @Composable @ReadOnlyComposable get() = LocalMahiTypography.current
}

@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun MahiTheme(content: @Composable () -> Unit) {
    val c = MahiDarkColors
    val type = MahiTypography()
    val scheme = darkColorScheme(
        primary = c.accentFill, onPrimary = c.onAccent,
        secondary = c.accent, onSecondary = c.onAccent,
        background = c.background, onBackground = c.textPrimary,
        surface = c.card, onSurface = c.textPrimary,
        surfaceVariant = c.elevated, onSurfaceVariant = c.textSecondary,
        outline = c.outlineStrong, outlineVariant = c.outline,
        error = c.error, onError = Color.Black, scrim = c.scrim,
    )
    val material = Typography(
        headlineLarge = type.screenTitle, headlineMedium = type.headline, titleLarge = type.headline,
        titleMedium = type.title, bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.bodySmall,
        labelLarge = type.button, labelMedium = type.caption, labelSmall = type.sectionLabel,
    )
    CompositionLocalProvider(
        LocalMahiColors provides c,
        LocalMahiSpacing provides MahiSpacing(),
        LocalMahiShapes provides MahiShapes(),
        LocalMahiTypography provides type,
        LocalReduceMotion provides rememberReduceMotion(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = material,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
