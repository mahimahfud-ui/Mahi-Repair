package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MahiRadius
import com.example.ui.theme.MahiSpacing
import com.example.ui.theme.MahiTheme
import com.example.ui.theme.rememberReducedMotion

/**
 * File 11: MahiPremiumComponents.kt
 * Status indicators, skeletons, and empty states.
 */

enum class MahiTone {
    Neutral, Accent, Success, Warning, Error
}

@Composable
fun MahiBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: MahiTone = MahiTone.Neutral,
    icon: (@Composable () -> Unit)? = null
) {
    val (bg, border, fg) = when (tone) {
        MahiTone.Neutral -> Triple(
            MahiTheme.colors.surfaceElevated,
            MahiTheme.colors.border,
            MahiTheme.colors.textSecondary
        )
        MahiTone.Accent -> Triple(
            MahiTheme.colors.crimsonGlow,
            MahiTheme.colors.crimson.copy(alpha = 0.5f),
            MahiTheme.colors.crimsonLight
        )
        MahiTone.Success -> Triple(
            MahiTheme.colors.emerald.copy(alpha = 0.12f),
            MahiTheme.colors.emerald.copy(alpha = 0.4f),
            MahiTheme.colors.emerald
        )
        MahiTone.Warning -> Triple(
            MahiTheme.colors.amber.copy(alpha = 0.12f),
            MahiTheme.colors.amber.copy(alpha = 0.4f),
            MahiTheme.colors.amber
        )
        MahiTone.Error -> Triple(
            MahiTheme.colors.crimson.copy(alpha = 0.15f),
            MahiTheme.colors.crimson.copy(alpha = 0.5f),
            MahiTheme.colors.crimsonLight
        )
    }

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(MahiRadius.full))
            .border(1.dp, border, RoundedCornerShape(MahiRadius.full))
            .padding(horizontal = MahiSpacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(10.dp)) { icon() }
        }
        Text(
            text = text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                letterSpacing = 0.3.sp
            )
        )
    }
}

@Composable
fun MahiStatusDot(
    modifier: Modifier = Modifier,
    tone: MahiTone = MahiTone.Success,
    pulse: Boolean = true,
    size: Dp = 8.dp
) {
    val baseColor = when (tone) {
        MahiTone.Neutral -> MahiTheme.colors.textTertiary
        MahiTone.Accent -> MahiTheme.colors.crimson
        MahiTone.Success -> MahiTheme.colors.emerald
        MahiTone.Warning -> MahiTheme.colors.amber
        MahiTone.Error -> MahiTheme.colors.crimson
    }

    val reduced = rememberReducedMotion()

    if (pulse && !reduced) {
        val infinite = rememberInfiniteTransition(label = "pulse")
        val ringScale by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ringScale"
        )
        val ringAlpha by infinite.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ringAlpha"
        )

        Box(
            modifier = modifier.size(size * 2.5f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(ringScale)
                    .alpha(ringAlpha)
                    .background(baseColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .background(baseColor, CircleShape)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(baseColor, CircleShape)
        )
    }
}

@Composable
fun MahiSkeleton(
    modifier: Modifier = Modifier,
    radius: Dp = MahiRadius.md
) {
    val reduced = rememberReducedMotion()
    val base = MahiTheme.colors.surfaceElevated
    val highlight = MahiTheme.colors.surfaceHighlight

    val brush = if (!reduced) {
        val infinite = rememberInfiniteTransition(label = "shimmer")
        val phase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerPhase"
        )
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = androidx.compose.ui.geometry.Offset(phase, 0f),
            end = androidx.compose.ui.geometry.Offset(phase + 300f, 300f)
        )
    } else {
        SolidColor(base)
    }

    Box(
        modifier = modifier
            .background(brush, RoundedCornerShape(radius))
    )
}

@Composable
fun MahiEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    action: (@Composable () -> Unit)? = null
) {
    val reduced = rememberReducedMotion()
    val bobOffset by if (!reduced) {
        val infinite = rememberInfiniteTransition(label = "bob")
        infinite.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emptyBob"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MahiSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = bobOffset.dp)
                .size(64.dp)
                .background(MahiTheme.colors.surfaceElevated, CircleShape)
                .border(1.dp, MahiTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MahiTheme.colors.crimsonLight,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(MahiSpacing.lg))

        Text(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MahiTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(MahiSpacing.xs))

        Text(
            text = description,
            style = TextStyle(
                fontSize = 13.sp,
                color = MahiTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        if (action != null) {
            Spacer(modifier = Modifier.height(MahiSpacing.lg))
            action()
        }
    }
}

@Composable
fun MahiErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MahiEmptyState(
        title = title,
        description = message,
        icon = Icons.Filled.Warning,
        modifier = modifier,
        action = onRetry?.let { retryAction ->
            {
                MahiSecondaryButton(
                    text = "Retry",
                    onClick = retryAction,
                    leadingIcon = Icons.Filled.Refresh,
                    modifier = Modifier.testTag("error_state_retry")
                )
            }
        }
    )
}
