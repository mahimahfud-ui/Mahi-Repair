package com.mahi.repair.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colour tokens. `accent` is for text, icons and focus rings on dark surfaces.
 * `accentFill` is a slightly deeper red used behind white button text (4.6:1 contrast).
 */
@Immutable
data class MahiColors(
    val background: Color,
    val backgroundSecondary: Color,
    val card: Color,
    val elevated: Color,
    val outline: Color,
    val outlineStrong: Color,
    val accent: Color,
    val accentFill: Color,
    val accentFillPressed: Color,
    val accentSubtle: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val error: Color,
    val errorSubtle: Color,
    val scrim: Color,
)

val MahiDarkColors = MahiColors(
    background = Color(0xFF090A0D),
    backgroundSecondary = Color(0xFF101216),
    card = Color(0xFF15181E),
    elevated = Color(0xFF1B1F26),
    outline = Color(0x14FFFFFF),
    outlineStrong = Color(0x29FFFFFF),
    accent = Color(0xFFE5484D),
    accentFill = Color(0xFFD7383F),
    accentFillPressed = Color(0xFFBF2F36),
    accentSubtle = Color(0x26E5484D),
    onAccent = Color.White,
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9399A4),
    textTertiary = Color(0xFF6E7480),
    success = Color(0xFF35C98B),
    successSubtle = Color(0x2635C98B),
    warning = Color(0xFFE9AB4B),
    warningSubtle = Color(0x26E9AB4B),
    error = Color(0xFFEF5B63),
    errorSubtle = Color(0x26EF5B63),
    scrim = Color(0xB3000000),
)

/** 4dp grid. */
@Immutable
data class MahiSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
)

/** Radius scale: small controls 8, inputs/icon tiles 12, cards/buttons 16, sheets 28. */
@Immutable
data class MahiShapes(
    val xs: Shape = RoundedCornerShape(8.dp),
    val sm: Shape = RoundedCornerShape(12.dp),
    val md: Shape = RoundedCornerShape(16.dp),
    val lg: Shape = RoundedCornerShape(20.dp),
    val xl: Shape = RoundedCornerShape(28.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
)

object MahiMotion {
    const val Quick = 150
    const val Standard = 250
    const val Emphasized = 400
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> standard() = tween<T>(Standard, easing = Ease)
    fun <T> layoutSpring() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    fun <T> pressSpring() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
}

/** True when the user has turned system animations off. Components fall back to static visuals. */
val LocalReduceMotion = staticCompositionLocalOf { false }
