package com.example

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
import com.example.ui.MainUiState
import com.example.ui.screens.MainScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunRepairScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val networkTool = RepairTool(
        id = "server-tool-id",
        name = "Network Diagnostics",
        description = "Check network and DNS connectivity.",
        category = ToolCategory.GENERAL,
        version = "2",
        problemDescription = "Network diagnostics"
    )

    private val dnsTool = RepairTool(
        id = "dns-tool-id",
        name = "DNS Troubleshooting",
        description = "Resolve and verify DNS records.",
        category = ToolCategory.GENERAL,
        version = "1",
        problemDescription = "DNS troubleshooting"
    )

    private fun setMainScreen(
        state: MainUiState,
        onRunRepair: () -> Unit = {},
        onRetryAvailability: () -> Unit = {},
        onSelectTool: (String) -> Unit = {},
        onDismissToolPicker: () -> Unit = {}
    ) {
        composeRule.setContent {
            MainScreen(
                state = state,
                onRunRepair = onRunRepair,
                onRetryAvailability = onRetryAvailability,
                onSelectTool = onSelectTool,
                onDismissToolPicker = onDismissToolPicker,
                onSubmitUserAction = {},
                onDismissExecution = {},
                onRequestPermission = {},
                onCancelPermission = {},
                onSignOut = {}
            )
        }
    }

    @Test
    fun homeDashboardShowsLiveSectionsAndAuthorizedTools() {
        setMainScreen(
            MainUiState(
                tools = listOf(networkTool, dnsTool),
                isToolsLoading = false
            )
        )

        composeRule.onNodeWithText("YOUR REPAIR HUB").assertExists().assertIsDisplayed()
        composeRule.onNodeWithText("Everything running smoothly?").assertExists()
        composeRule.onNodeWithTag("run_repair_button").assertExists().assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Network Diagnostics").assertExists()
        composeRule.onNodeWithText("DNS Troubleshooting").assertExists()
        composeRule.onNodeWithTag("search_bar").assertDoesNotExist()
        composeRule.onNodeWithText("Admin Panel", substring = true).assertDoesNotExist()
    }

    @Test
    fun runRepairButtonOpensExistingCatalogueFlow() {
        var openCount = 0
        setMainScreen(
            MainUiState(tools = listOf(networkTool), isToolsLoading = false),
            onRunRepair = { openCount++ }
        )

        composeRule.onNodeWithTag("run_repair_button").performClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun catalogueDisplaysOnlyTheSuppliedPublishedToolEntries() {
        var selectedToolId = ""
        setMainScreen(
            MainUiState(
                tools = listOf(networkTool, dnsTool),
                isToolsLoading = false,
                isToolPickerOpen = true
            ),
            onSelectTool = { selectedToolId = it }
        )

        composeRule.onNodeWithText("Network Diagnostics").assertExists().assertIsDisplayed()
        composeRule.onAllNodesWithText("DNS Troubleshooting").onLast().performClick()
        composeRule.runOnIdle { assertEquals("dns-tool-id", selectedToolId) }
    }

    @Test
    fun toolsNavigationOpensSearchableVerifiedLibrary() {
        setMainScreen(
            MainUiState(tools = listOf(networkTool, dnsTool), isToolsLoading = false)
        )

        composeRule.onNodeWithContentDescription("Tools").performClick()
        composeRule.onNodeWithText("VERIFIED LIBRARY").assertExists()
        composeRule.onNodeWithTag("search_bar").assertExists().assertIsDisplayed()
    }

    @Test
    fun unavailableCatalogueKeepsRunRepairDisabled() {
        setMainScreen(
            MainUiState(
                tools = emptyList(),
                isToolsLoading = false,
                canRetryAvailability = false,
                repairStatusMessage = "No repair tools are currently available."
            )
        )

        composeRule.onNodeWithTag("run_repair_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("repair_status").assertExists()
        composeRule.onNodeWithText("No repair tools are currently available.").assertExists()
    }
}
