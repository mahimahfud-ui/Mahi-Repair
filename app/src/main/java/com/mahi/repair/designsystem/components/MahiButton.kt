package com.mahi.repair.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.theme.MahiMotion
import com.mahi.repair.designsystem.theme.MahiTheme

enum class MahiButtonStyle { Primary, Secondary, Ghost }

/**
 * Spring-press button with a 48dp minimum touch target, focus ring and optional haptic.
 * [onClick] must be your real callback; this component has no behaviour of its own.
 */
@Composable
fun MahiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MahiButtonStyle = MahiButtonStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    accessibilityLabel: String? = null,
    compact: Boolean = false,
    haptic: Boolean = true,
) {
    val c = MahiTheme.colors
    val shape = MahiTheme.shapes.md
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val buzz = rememberMahiHaptic()

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> c.elevated.copy(alpha = 0.5f)
            style == MahiButtonStyle.Primary -> if (pressed) c.accentFillPressed else c.accentFill
            style == MahiButtonStyle.Secondary -> if (pressed) c.outlineStrong.compositeOver(c.elevated) else c.elevated
            else -> if (pressed) c.outline else Color.Transparent
        },
        animationSpec = tween(MahiMotion.Quick),
        label = "buttonContainer",
    )
    val contentColor = when {
        !enabled -> c.textTertiary
        style == MahiButtonStyle.Primary -> c.onAccent
        else -> c.textPrimary
    }

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(source)
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .clip(shape)
            .background(container)
            .then(if (style == MahiButtonStyle.Secondary && enabled) Modifier.border(1.dp, c.outline, shape) else Modifier)
            .mahiFocusRing(source, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = accessibilityLabel,
            ) {
                if (haptic) buzz()
                onClick()
            }
            .padding(horizontal = if (compact) 16.dp else 20.dp)
            .semantics { if (accessibilityLabel != null) contentDescription = accessibilityLabel },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MahiTheme.typography.button, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
