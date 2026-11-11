package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MahiRadius
import com.example.ui.theme.MahiSpacing
import com.example.ui.theme.MahiTheme
import com.example.ui.theme.mahiCardGlow
import com.example.ui.theme.mahiPressable

/**
 * File 12: MahiCards.kt
 * Surface containers: MahiCard, MahiStatCard, and MahiSectionHeader.
 */
@Composable
fun MahiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = MahiRadius.lg,
    interactive: Boolean = onClick != null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isPressed -> MahiTheme.colors.crimson
            isHovered -> MahiTheme.colors.borderFocus
            else -> MahiTheme.colors.border
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardBorder"
    )

    val surfaceColor by animateColorAsState(
        targetValue = when {
            isPressed -> MahiTheme.colors.surfaceHighlight
            isHovered -> MahiTheme.colors.surfaceElevated
            else -> MahiTheme.colors.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardSurface"
    )

    val shape = RoundedCornerShape(radius)

    val baseModifier = modifier
        .mahiCardGlow(
            color = MahiTheme.colors.crimsonGlow,
            radius = radius,
            active = isHovered || isPressed
        )
        .then(if (interactive) Modifier.mahiPressable(interactionSource) else Modifier)
        .clip(shape)
        .background(surfaceColor, shape)
        .border(1.dp, borderColor, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )

    Box(
        modifier = baseModifier,
        content = content
    )
}

@Composable
fun MahiStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    delta: String? = null,
    deltaPositive: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    MahiCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MahiSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MahiTheme.colors.textTertiary
                    )
                )
                if (icon != null) {
                    Box(modifier = Modifier.size(16.dp)) { icon() }
                }
            }

            Spacer(modifier = Modifier.height(MahiSpacing.sm))

            Text(
                text = value,
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp,
                    color = MahiTheme.colors.textPrimary
                )
            )

            if (subtext != null || delta != null) {
                Spacer(modifier = Modifier.height(MahiSpacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MahiSpacing.xs)
                ) {
                    if (delta != null) {
                        Text(
                            text = delta,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (deltaPositive) MahiTheme.colors.emerald else MahiTheme.colors.crimson
                            )
                        )
                    }
                    if (subtext != null) {
                        Text(
                            text = subtext,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = MahiTheme.colors.textSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MahiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MahiSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = MahiTheme.colors.textPrimary
                )
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = MahiTheme.colors.textSecondary
                    )
                )
            }
        }

        if (action != null) {
            action()
        }
    }
}
