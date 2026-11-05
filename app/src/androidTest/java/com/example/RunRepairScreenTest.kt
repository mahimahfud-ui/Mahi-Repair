package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test
    fun homeShowsOnlyRunRepairActionUntilTheUserOpensTheCatalogue() {
        composeRule.setContent {
            MainScreen(
                state = MainUiState(
                    tools = listOf(networkTool, dnsTool),
                    isToolsLoading = false
                ),
                onRunRepair = {},
                onRetryAvailability = {},
                onSelectTool = {},
                onDismissToolPicker = {},
                onSubmitUserAction = {},
                onDismissExecution = {}
            )
        }

        composeRule.onNodeWithText("MAHI REPAIR").assertExists()
        composeRule.onNodeWithTag("run_repair_button").assertExists().assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("search_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("tools_list").assertDoesNotExist()
        composeRule.onNodeWithTag("sync_button").assertDoesNotExist()
        composeRule.onNodeWithTag("history_button").assertDoesNotExist()
        composeRule.onNodeWithTag("sign_out_button").assertDoesNotExist()
        composeRule.onNodeWithText("Admin Panel", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Network Diagnostics").assertDoesNotExist()
        composeRule.onNodeWithText("DNS Troubleshooting").assertDoesNotExist()
    }

    @Test
    fun runRepairButtonInvokesCatalogueOpeningAction() {
        var openCount = 0
        composeRule.setContent {
            MainScreen(
                state = MainUiState(
                    tools = listOf(networkTool),
                    isToolsLoading = false
                ),
                onRunRepair = { openCount++ },
                onRetryAvailability = {},
                onSelectTool = {},
                onDismissToolPicker = {},
                onSubmitUserAction = {},
                onDismissExecution = {}
            )
        }

        composeRule.onNodeWithTag("run_repair_button").performClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun catalogueDisplaysOnlyTheSuppliedPublishedToolEntries() {
        var selectedToolId = ""
        composeRule.setContent {
            MainScreen(
                state = MainUiState(
                    tools = listOf(networkTool, dnsTool),
                    isToolsLoading = false,
                    isToolPickerOpen = true
                ),
                onRunRepair = {},
                onRetryAvailability = {},
                onSelectTool = { selectedToolId = it },
                onDismissToolPicker = {},
                onSubmitUserAction = {},
                onDismissExecution = {}
            )
        }

        composeRule.onNodeWithText("Network Diagnostics").assertExists().assertIsDisplayed()
        composeRule.onNodeWithText("DNS Troubleshooting").assertExists().assertIsDisplayed()
        composeRule.onNodeWithText("v2", substring = true).assertExists()
        composeRule.onNodeWithText("v1", substring = true).assertExists()
        composeRule.onNodeWithText("DNS Troubleshooting").performClick()
        composeRule.runOnIdle { assertEquals("dns-tool-id", selectedToolId) }
    }

    @Test
    fun unavailableCatalogueKeepsRunRepairDisabled() {
        composeRule.setContent {
            MainScreen(
                state = MainUiState(
                    tools = emptyList(),
                    isToolsLoading = false,
                    canRetryAvailability = false,
                    repairStatusMessage = "No repair tools are currently available."
                ),
                onRunRepair = {},
                onRetryAvailability = {},
                onSelectTool = {},
                onDismissToolPicker = {},
                onSubmitUserAction = {},
                onDismissExecution = {}
            )
        }

        composeRule.onNodeWithTag("run_repair_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("repair_status").assertExists()
        composeRule.onNodeWithText("No repair tools are currently available.").assertExists()
    }
}
