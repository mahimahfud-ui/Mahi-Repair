package com.example

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
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

    private val primaryTool = RepairTool(
        id = "server-tool-id",
        name = "Run Repair",
        description = "Owner-published repair workflow",
        category = ToolCategory.GENERAL,
        version = "1",
        problemDescription = "Primary repair workflow"
    )

    @Test
    fun homeShowsOnlyThePrimaryRunRepairAction() {
        composeRule.setContent {
            MainScreen(
                state = MainUiState(primaryTool = primaryTool, isPrimaryToolLoading = false),
                onRunRepair = {},
                onRetryAvailability = {},
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
        composeRule.onNodeWithText("Search repair tools...", substring = true).assertDoesNotExist()
    }

    @Test
    fun runRepairButtonInvokesTheSinglePrimaryAction() {
        var runCount = 0
        composeRule.setContent {
            MainScreen(
                state = MainUiState(primaryTool = primaryTool, isPrimaryToolLoading = false),
                onRunRepair = { runCount++ },
                onRetryAvailability = {},
                onSubmitUserAction = {},
                onDismissExecution = {}
            )
        }

        composeRule.onNodeWithTag("run_repair_button").performClick()
        composeRule.runOnIdle { assertEquals(1, runCount) }
    }

    @Test
    fun unavailableRecipeKeepsRunRepairDisabled() {
        composeRule.setContent {
            MainScreen(
                state = MainUiState(
                    primaryTool = null,
                    isPrimaryToolLoading = false,
                    canRetryAvailability = false,
                    repairStatusMessage = "Run Repair is not available yet."
                ),
                onRunRepair = {},
                onRetryAvailability = {},
                onSubmitUserAction = {},
                onDismissExecution = {}
            )
        }

        composeRule.onNodeWithTag("run_repair_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("repair_status").assertExists()
        composeRule.onNodeWithText("Run Repair is not available yet.").assertExists()
    }
}
