package com.mahi.repair.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mahi.repair.designsystem.components.*
import com.mahi.repair.designsystem.theme.MahiTheme

// Preview-only sample text so every state can be checked in Android Studio.
// Real screens must render synced backend data. Keep this file in debug/ or delete it.

@Preview(widthDp = 360, heightDp = 2300, showBackground = true, backgroundColor = 0xFF090A0D)
@Composable
private fun ToolCardStatesPreview() = MahiTheme {
    Column(Modifier.background(MahiTheme.colors.background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolCardState.values().forEach {
            RepairToolCard("Sample tool", "A short description of what this repair tool checks and fixes.", "Network", it, onAction = {})
        }
        RepairToolCardSkeleton()
    }
}

@Preview(widthDp = 360, heightDp = 640, showBackground = true, backgroundColor = 0xFF090A0D)
@Composable
private fun ExecutionPreview() = MahiTheme {
    Column(Modifier.background(MahiTheme.colors.background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        RepairExecutionHeader("Sample tool", "Testing connectivity", progress = null) {}
        RepairStepList(
            listOf(
                RepairStep("1", "First check", StepStatus.Done),
                RepairStep("2", "Second check", StepStatus.Done),
                RepairStep("3", "Third check", StepStatus.Active),
                RepairStep("4", "Needs your input", StepStatus.Waiting, detail = "Follow the on-screen instruction."),
                RepairStep("5", "Verify repair", StepStatus.Pending),
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncState.values().forEach { SyncStatusChip(it) }
        }
    }
}

@Preview(widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun SuccessPreview() = MahiTheme {
    RepairSuccessScreen(listOf("Check one", "Check two"), listOf("Fix one"), "Passed", "12 s", onDone = {})
}

@Preview(widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun PartialPreview() = MahiTheme {
    RepairPartialScreen("One step needs your attention.", listOf("Check one"), "Open settings", {}, {})
}

@Preview(widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun FailedPreview() = MahiTheme {
    RepairFailedScreen("The final check did not pass.", "The connection may be unstable.", "Move closer to your router and try again.", onDone = {}, onRetry = {})
}

@Preview(widthDp = 360, heightDp = 200, showBackground = true, backgroundColor = 0xFF090A0D)
@Composable
private fun SearchPreview() = MahiTheme {
    Column(Modifier.padding(20.dp).fillMaxWidth()) { MahiSearchField("", {}) }
}
