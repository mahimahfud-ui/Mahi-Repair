package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.MahiTheme
import com.example.ui.theme.rememberReducedMotion

/**
 * Obsidian backdrop with two slow, drifting light sources (champagne and cyan).
 * Drawn entirely in the draw phase, so the drift never triggers recomposition.
 * Keep [intensity] at or below 1 so text contrast stays strong.
 */
@Composable
fun MahiAmbientBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = MahiTheme.colors
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "mahiAmbient")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ambient1",
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ambient2",
    )

    Box(
        modifier = modifier
            .background(c.background)
            .drawBehind {
                val a = if (reduced) 0.5f else p1
                val b = if (reduced) 0.5f else p2
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.champagne.copy(alpha = 0.10f * intensity), Color.Transparent),
                        center = Offset(w * (0.15f + 0.5f * a), h * (0.08f + 0.12f * b)),
                        radius = w * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.cyan.copy(alpha = 0.08f * intensity), Color.Transparent),
                        center = Offset(w * (0.9f - 0.5f * b), h * (0.85f - 0.2f * a)),
                        radius = w * 0.95f,
                    ),
                )
            },
        content = content,
    )
}
