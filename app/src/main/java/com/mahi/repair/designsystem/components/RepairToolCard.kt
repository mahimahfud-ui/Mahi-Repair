package com.mahi.repair.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.theme.LocalReduceMotion
import com.mahi.repair.designsystem.theme.MahiMotion
import com.mahi.repair.designsystem.theme.MahiTheme

/** Map your existing tool/execution state onto one of these. Never fabricate a state. */
enum class ToolCardState {
    Available, New, Updating, Running, RequiresPermission,
    WaitingForUser, Completed, Failed, Disabled, Offline,
}

private data class CardUi(
    val label: String?,
    val tone: Color,
    val glyph: ImageVector?,
    val action: String,
    val enabled: Boolean,
    val style: MahiButtonStyle,
    val busy: Boolean = false,
    val dim: Boolean = false,
)

@Composable
private fun ToolCardState.ui(): CardUi {
    val c = MahiTheme.colors
    return when (this) {
        ToolCardState.Available -> CardUi(null, c.textSecondary, null, "Run", true, MahiButtonStyle.Primary)
        ToolCardState.New -> CardUi("New", c.accent, Icons.Rounded.Star, "Run", true, MahiButtonStyle.Primary)
        ToolCardState.Updating -> CardUi("Updating", c.textSecondary, Icons.Rounded.Refresh, "Updating", false, MahiButtonStyle.Secondary, busy = true)
        ToolCardState.Running -> CardUi("Running", c.accent, Icons.Rounded.PlayArrow, "View", true, MahiButtonStyle.Secondary, busy = true)
        ToolCardState.RequiresPermission -> CardUi("Permission needed", c.warning, Icons.Rounded.Lock, "Allow", true, MahiButtonStyle.Primary)
        ToolCardState.WaitingForUser -> CardUi("Waiting for you", c.warning, Icons.Rounded.Person, "Continue", true, MahiButtonStyle.Primary)
        ToolCardState.Completed -> CardUi("Completed", c.success, Icons.Rounded.Check, "Run again", true, MahiButtonStyle.Secondary)
        ToolCardState.Failed -> CardUi("Didn’t finish", c.error, Icons.Rounded.Close, "Retry", true, MahiButtonStyle.Secondary)
        ToolCardState.Disabled -> CardUi("Unavailable", c.textSecondary, Icons.Rounded.Info, "Run", false, MahiButtonStyle.Primary, dim = true)
        ToolCardState.Offline -> CardUi("Offline", c.textSecondary, Icons.Rounded.Warning, "Run", false, MahiButtonStyle.Primary, dim = true)
    }
}

/**
 * One repair tool. Data comes from your synced backend model; [onAction] is your existing
 * execution / permission / retry callback. Status is always conveyed by text + glyph, never colour alone.
 *
 * [icon] is a slot: pass the tool's real icon (vector, or an image-loader composable for remote icons).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepairToolCard(
    name: String,
    description: String,
    category: String,
    state: ToolCardState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Icon(Icons.Rounded.Build, contentDescription = null) },
) {
    val c = MahiTheme.colors
    val shape = MahiTheme.shapes.md
    val ui = state.ui()
    val border by animateColorAsState(
        targetValue = when (state) {
            ToolCardState.Running -> c.accent.copy(alpha = 0.6f)
            ToolCardState.Failed -> c.error.copy(alpha = 0.35f)
            ToolCardState.WaitingForUser, ToolCardState.RequiresPermission -> c.warning.copy(alpha = 0.35f)
            else -> c.outline
        },
        animationSpec = tween(MahiMotion.Standard),
        label = "cardBorder",
    )

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, border, shape)
            .padding(16.dp)
            .animateContentSize(MahiMotion.layoutSpring()),
    ) {
        Row(
            Modifier
                .alpha(if (ui.dim) 0.6f else 1f)
                .semantics(mergeDescendants = true) {
                    stateDescription = ui.label ?: "Available"
                    contentDescription = "$name. $description. Category: $category"
                },
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(MahiTheme.shapes.sm)
                    .background(c.elevated)
                    .border(1.dp, c.outline, MahiTheme.shapes.sm),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides c.accent) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MahiTheme.typography.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MahiTheme.typography.bodySmall,
                    color = c.textSecondary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                Modifier.weight(1f).alpha(if (ui.dim) 0.6f else 1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    category,
                    style = MahiTheme.typography.caption,
                    color = c.textSecondary,
                    maxLines = 1,
                    modifier = Modifier
                        .border(1.dp, c.outlineStrong, MahiTheme.shapes.pill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "statusPill",
                ) { s ->
                    val u = s.ui()
                    if (u.label != null) StatusPill(u)
                }
            }
            Spacer(Modifier.width(12.dp))
            MahiButton(
                text = ui.action,
                onClick = onAction,
                enabled = ui.enabled,
                style = ui.style,
                compact = true,
                accessibilityLabel = "${ui.action} $name",
                modifier = Modifier.widthIn(min = 88.dp),
            )
        }

        AnimatedVisibility(visible = ui.busy) {
            Box(Modifier.padding(top = 14.dp)) {
                if (LocalReduceMotion.current) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(MahiTheme.shapes.pill).background(c.accent.copy(alpha = 0.5f)))
                } else {
                    LinearProgressIndicator(
                        color = c.accent,
                        trackColor = c.outline,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(MahiTheme.shapes.pill),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(ui: CardUi) {
    Row(
        Modifier
            .clip(MahiTheme.shapes.pill)
            .background(ui.tone.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ui.glyph?.let {
            Icon(it, contentDescription = null, tint = ui.tone, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(ui.label.orEmpty(), style = MahiTheme.typography.caption, color = ui.tone, maxLines = 1)
    }
}

/** Same footprint as [RepairToolCard]. Show while the first sync is in flight. */
@Composable
fun RepairToolCardSkeleton(modifier: Modifier = Modifier) {
    val c = MahiTheme.colors
    val shape = MahiTheme.shapes.md
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.outline, shape)
            .padding(16.dp)
            .semantics { contentDescription = "Loading repair tool" },
    ) {
        Row {
            Box(Modifier.size(44.dp).clip(MahiTheme.shapes.sm).shimmer())
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(MahiTheme.shapes.xs).shimmer())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(12.dp).clip(MahiTheme.shapes.xs).shimmer())
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(MahiTheme.shapes.xs).shimmer())
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(64.dp).height(24.dp).clip(MahiTheme.shapes.pill).shimmer())
            Box(Modifier.width(88.dp).height(40.dp).clip(MahiTheme.shapes.md).shimmer())
        }
    }
}
