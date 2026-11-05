package com.mahi.repair.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.theme.LocalReduceMotion
import com.mahi.repair.designsystem.theme.MahiMotion
import com.mahi.repair.designsystem.theme.MahiTheme

/** Spring-based press feedback. Disabled automatically when system animations are off. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = MahiMotion.pressSpring(),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Visible keyboard / switch-access focus indicator. */
fun Modifier.mahiFocusRing(source: InteractionSource, shape: Shape): Modifier = composed {
    val focused by source.collectIsFocusedAsState()
    if (focused) border(2.dp, MahiTheme.colors.accent, shape) else this
}

/** Skeleton shimmer. Static fill when reduced motion is on. Clip the shape before calling. */
fun Modifier.shimmer(): Modifier = composed {
    val c = MahiTheme.colors
    if (LocalReduceMotion.current) {
        background(c.elevated)
    } else {
        val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = -0.5f, targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "shimmerX",
        )
        drawBehind {
            val w = size.width
            drawRect(
                Brush.linearGradient(
                    colors = listOf(c.elevated, c.outlineStrong.compositeOver(c.elevated), c.elevated),
                    start = Offset(w * (x - 0.4f), 0f),
                    end = Offset(w * (x + 0.4f), 0f),
                )
            )
        }
    }
}

/** Returns a lambda that fires haptic feedback. Use for important actions only (Run, Retry, success). */
@Composable
fun rememberMahiHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } }
}
