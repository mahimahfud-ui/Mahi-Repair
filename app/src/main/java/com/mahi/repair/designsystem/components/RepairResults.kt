package com.mahi.repair.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.theme.MahiTheme

/**
 * Show only after your execution engine has reported final verification.
 * These screens render the facts you pass in; they never decide the outcome.
 */
@Composable
fun RepairSuccessScreen(
    checked: List<String>,
    repaired: List<String>,
    verification: String,
    duration: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "The final check passed.",
) {
    val buzz = rememberMahiHaptic()
    LaunchedEffect(Unit) { buzz() }
    ResultScaffold(
        modifier = modifier,
        icon = { AnimatedCheckmark() },
        title = "Repair completed",
        subtitle = subtitle,
        primaryLabel = "Done",
        onPrimary = onDone,
    ) {
        ResultList("What was checked", checked, Icons.Rounded.Check, MahiTheme.colors.success)
        ResultList("What was repaired", repaired, Icons.Rounded.Check, MahiTheme.colors.success)
        ResultFacts(listOf("Verification" to verification, "Completed in" to duration))
    }
}

@Composable
fun RepairPartialScreen(
    attention: String,
    completed: List<String>,
    nextStepLabel: String,
    onNextStep: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MahiTheme.colors
    ResultScaffold(
        modifier = modifier,
        icon = { ResultGlyph(Icons.Rounded.Info, c.warning, c.warningSubtle) },
        title = "Almost there",
        subtitle = attention,
        primaryLabel = nextStepLabel,
        onPrimary = onNextStep,
        secondaryLabel = "Back to tools",
        onSecondary = onDone,
    ) {
        ResultList("Done so far", completed, Icons.Rounded.Check, c.success)
    }
}

@Composable
fun RepairFailedScreen(
    reason: String,
    possibleCause: String?,
    safeNextStep: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val c = MahiTheme.colors
    ResultScaffold(
        modifier = modifier,
        icon = { ResultGlyph(Icons.Rounded.Close, c.error, c.errorSubtle) },
        title = "Repair couldn’t finish",
        subtitle = reason,
        primaryLabel = if (onRetry != null) "Try again" else "Back to tools",
        onPrimary = onRetry ?: onDone,
        secondaryLabel = if (onRetry != null) "Back to tools" else null,
        onSecondary = if (onRetry != null) onDone else null,
    ) {
        if (possibleCause != null) ResultNote("Possible reason", possibleCause)
        ResultNote("What you can do", safeNextStep)
    }
}

@Composable
private fun ResultScaffold(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = MahiTheme.colors
    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            icon()
            Spacer(Modifier.height(12.dp))
            Text(title, style = MahiTheme.typography.headline, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MahiTheme.typography.body, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            body()
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MahiButton(primaryLabel, onPrimary, Modifier.fillMaxWidth())
            if (secondaryLabel != null && onSecondary != null) {
                MahiButton(secondaryLabel, onSecondary, Modifier.fillMaxWidth(), style = MahiButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun ResultGlyph(glyph: ImageVector, tone: Color, subtle: Color) {
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(subtle).border(1.dp, tone.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, contentDescription = null, tint = tone, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    val c = MahiTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MahiTheme.shapes.md)
            .background(c.card)
            .border(1.dp, c.outline, MahiTheme.shapes.md)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ResultList(title: String, items: List<String>, glyph: ImageVector, tone: Color) {
    if (items.isEmpty()) return
    val c = MahiTheme.colors
    ResultCard {
        Text(title, style = MahiTheme.typography.sectionLabel, color = c.textSecondary, modifier = Modifier.semantics { heading() })
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(glyph, contentDescription = null, tint = tone, modifier = Modifier.padding(top = 3.dp).size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(item, style = MahiTheme.typography.body, color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun ResultFacts(facts: List<Pair<String, String>>) {
    val c = MahiTheme.colors
    ResultCard {
        facts.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MahiTheme.typography.body, color = c.textSecondary)
                Spacer(Modifier.width(16.dp))
                Text(value, style = MahiTheme.typography.bodyStrong, color = c.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun ResultNote(title: String, text: String) {
    val c = MahiTheme.colors
    ResultCard {
        Text(title, style = MahiTheme.typography.sectionLabel, color = c.textSecondary, modifier = Modifier.semantics { heading() })
        Text(text, style = MahiTheme.typography.body, color = c.textPrimary)
    }
}
