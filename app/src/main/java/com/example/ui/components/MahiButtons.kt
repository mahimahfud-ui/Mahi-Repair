package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MahiMotion
import com.example.ui.theme.MahiRadius
import com.example.ui.theme.MahiSize
import com.example.ui.theme.MahiSpacing
import com.example.ui.theme.MahiTheme
import com.example.ui.theme.mahiPressScale
import com.example.ui.theme.rememberReducedMotion

private val ChampagneLight = Color(0xFFEBD7A8)

/**
 * Hero action. Champagne gradient, periodic light sheen, spring press, haptic tick, and a
 * morphing loading state. Pass a testTag through [modifier]; it lands on the clickable node.
 */
@Composable
fun MahiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val c = MahiTheme.colors
    val shape = RoundedCornerShape(MahiRadius.medium)
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val canClick = enabled && !loading

    val sheenTransition = rememberInfiniteTransition(label = "buttonSheen")
    val sheen by sheenTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3800
                0f at 0 using FastOutSlowInEasing
                1f at 1300
                1f at 3800
            },
        ),
        label = "sheen",
    )

    val background = if (enabled) {
        Brush.horizontalGradient(listOf(ChampagneLight, c.champagne))
    } else {
        Brush.horizontalGradient(listOf(c.surfaceElevated, c.surfaceElevated))
    }
    val contentColor = if (enabled) c.background else c.textMuted

    Box(
        modifier = modifier
            .mahiPressScale(interaction)
            .heightIn(min = MahiSize.buttonHeight)
            .clip(shape)
            .background(background)
            .drawWithContent {
                drawContent()
                if (canClick && !reduced) {
                    val x = -size.width * 0.4f + sheen * size.width * 1.8f
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(x, 0f),
                            end = Offset(x + size.width * 0.35f, size.height),
                        ),
                    )
                }
            }
            .semantics { if (loading) stateDescription = "Loading" }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = canClick,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = MahiSpacing.xxl, vertical = MahiSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                (fadeIn(tween(MahiMotion.FAST_MS)) + scaleIn(initialScale = 0.8f)) togetherWith
                    (fadeOut(tween(MahiMotion.FAST_MS)) + scaleOut(targetScale = 0.8f))
            },
            contentAlignment = Alignment.Center,
            label = "primaryButtonContent",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(MahiSize.iconMedium - 4.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MahiSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(MahiSize.iconSmall))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
                }
            }
        }
    }
}

/** Outlined titanium button. The border warms to cyan while pressed. */
@Composable
fun MahiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = MahiTheme.colors
    val shape = RoundedCornerShape(MahiRadius.medium)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val border by animateColorAsState(
        targetValue = if (pressed) c.cyan else c.border,
        animationSpec = tween(MahiMotion.FAST_MS),
        label = "secondaryBorder",
    )
    val contentColor = if (enabled) c.textPrimary else c.textMuted

    Box(
        modifier = modifier
            .mahiPressScale(interaction, pressedScale = 0.97f)
            .heightIn(min = MahiSize.buttonHeight)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = MahiSpacing.xxl, vertical = MahiSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MahiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(MahiSize.iconSmall))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

/** Low-emphasis text action in cyan. Minimum 48dp touch target. */
@Composable
fun MahiGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = MahiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .mahiPressScale(interaction, pressedScale = 0.94f)
            .heightIn(min = MahiSize.minTouchTarget)
            .clip(RoundedCornerShape(MahiRadius.small))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = MahiSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) c.cyan else c.textMuted,
        )
    }
}

/** Square icon button with optional champagne notification dot. */
@Composable
fun MahiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
) {
    val c = MahiTheme.colors
    val shape = RoundedCornerShape(MahiRadius.medium)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .mahiPressScale(interaction, pressedScale = 0.9f)
            .size(MahiSize.minTouchTarget)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(MahiSize.iconMedium - 2.dp))
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.champagne),
            )
        }
    }
}
