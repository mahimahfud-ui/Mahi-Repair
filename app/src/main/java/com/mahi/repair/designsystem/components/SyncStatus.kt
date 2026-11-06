package com.mahi.repair.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.theme.MahiMotion
import com.mahi.repair.designsystem.theme.MahiTheme

/** Map your existing sync/connectivity/auth state onto these. User-facing wording only; no backend detail. */
enum class SyncState { Connecting, Syncing, UpToDate, NewToolsAvailable, Offline, AuthRequired, ServerUnavailable }

private data class SyncUi(val label: String, val tone: Color, val glyph: ImageVector?)

@Composable
private fun SyncState.ui(): SyncUi {
    val c = MahiTheme.colors
    return when (this) {
        SyncState.Connecting -> SyncUi("Connecting", c.textSecondary, null)
        SyncState.Syncing -> SyncUi("Syncing tools", c.textSecondary, null)
        SyncState.UpToDate -> SyncUi("Up to date", c.success, Icons.Rounded.Check)
        SyncState.NewToolsAvailable -> SyncUi("New tools available", c.accent, Icons.Rounded.Star)
        SyncState.Offline -> SyncUi("Offline", c.warning, Icons.Rounded.Info)
        SyncState.AuthRequired -> SyncUi("Sign in to sync", c.warning, Icons.Rounded.Lock)
        SyncState.ServerUnavailable -> SyncUi("Can’t reach server", c.error, Icons.Rounded.Warning)
    }
}

/** Compact header chip. Pass [onClick] for actionable states (retry sync, sign in, show new tools). */
@Composable
fun SyncStatusChip(state: SyncState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val ui = state.ui()
    val tone by animateColorAsState(ui.tone, tween(MahiMotion.Standard), label = "syncTone")
    val shape = MahiTheme.shapes.pill
    val base = modifier
        .let { if (onClick != null) it.minimumInteractiveComponentSize() else it }
        .clip(shape)
        .background(tone.copy(alpha = 0.12f))
        .border(1.dp, tone.copy(alpha = 0.25f), shape)
    val tappable = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base

    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        label = "syncChip",
    ) { s ->
        val u = s.ui()
        Row(
            tappable
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Sync status: ${u.label}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (u.glyph != null) Icon(u.glyph, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
            else MahiSpinner(diameter = 14.dp, color = tone)
            Spacer(Modifier.width(6.dp))
            Text(u.label, style = MahiTheme.typography.caption, color = tone, maxLines = 1)
        }
    }
}
