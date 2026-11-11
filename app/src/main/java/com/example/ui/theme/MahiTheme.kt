package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MahiColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val borderFocus: Color,
    val crimson: Color,
    val crimsonLight: Color,
    val crimsonGlow: Color,
    val champagne: Color,
    val cyan: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val emerald: Color,
    val amber: Color,
    val error: Color,
    val success: Color,
    val warning: Color
)

val DefaultMahiColors = MahiColors(
    background = Color(0xFF090A0D),
    surface = Color(0xFF101216),
    surfaceElevated = Color(0xFF171A20),
    surfaceHighlight = Color(0xFF20242C),
    border = Color(0xFF252830),
    borderFocus = Color(0xFF383C48),
    crimson = Color(0xFFEB102A),
    crimsonLight = Color(0xFFFF334B),
    crimsonGlow = Color(0x33EB102A),
    champagne = Color(0xFFD5B77A),
    cyan = Color(0xFF4FD6D0),
    accent = Color(0xFFEB102A),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFF9298A5),
    textTertiary = Color(0xFF5A6070),
    textMuted = Color(0xFF5A6070),
    emerald = Color(0xFF10B981),
    amber = Color(0xFFF59E0B),
    error = Color(0xFFEB102A),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B)
)

object MahiRadius {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val full: Dp = 999.dp

    // Compatibility aliases
    val small: Dp = sm
    val medium: Dp = md
    val large: Dp = lg
    val pill: Dp = full
}

object MahiSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

object MahiSize {
    val buttonHeight: Dp = 48.dp
    val inputHeight: Dp = 52.dp
    val minTouchTarget: Dp = 48.dp
    val iconSmall: Dp = 16.dp
    val iconMedium: Dp = 24.dp
    val iconLarge: Dp = 32.dp
}

object MahiMotion {
    const val FAST_MS = 180
    const val STANDARD_MS = 300
    const val EMPHASIZED_MS = 480
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
}

val LocalMahiColors = staticCompositionLocalOf { DefaultMahiColors }

object MahiTheme {
    val colors: MahiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMahiColors.current

    val radius = MahiRadius
    val spacing = MahiSpacing
    val size = MahiSize
    val motion = MahiMotion
}

/**
 * Modifier for interactive press feedback with smooth spring scaling.
 */
fun Modifier.mahiPressable(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mahiPressableScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Modifier for subtle ambient pulsing glow.
 */
fun Modifier.mahiPulseGlow(
    color: Color,
    radius: Dp = 16.dp
): Modifier = composed {
    val reduced = rememberReducedMotion()
    if (reduced) return@composed this

    val infinite = rememberInfiniteTransition(label = "pulseGlow")
    val alpha by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    drawBehind {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = size.maxDimension / 2f + radius.toPx()
        )
    }
}

/**
 * Modifier for card hover / active border glow.
 */
fun Modifier.mahiCardGlow(
    color: Color,
    radius: Dp = MahiRadius.lg,
    active: Boolean = false
): Modifier = composed {
    val targetAlpha = if (active) 0.35f else 0.08f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 250),
        label = "cardGlowAlpha"
    )

    drawBehind {
        val strokeWidth = 2.dp.toPx()
        val corner = radius.toPx()
        drawRoundRect(
            color = color.copy(alpha = alpha),
            size = Size(size.width + strokeWidth * 2, size.height + strokeWidth * 2),
            topLeft = Offset(-strokeWidth, -strokeWidth),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Modifier for focused elements outline ring.
 */
fun Modifier.mahiFocusRing(
    focused: Boolean,
    color: Color,
    shape: Shape = RoundedCornerShape(MahiRadius.md)
): Modifier = composed {
    val targetAlpha = if (focused) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 180),
        label = "focusRingAlpha"
    )

    if (alpha <= 0.01f) return@composed this

    drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            color = color.copy(alpha = alpha),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
