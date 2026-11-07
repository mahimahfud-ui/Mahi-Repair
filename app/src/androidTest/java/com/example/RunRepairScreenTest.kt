package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
import com.example.ui.MainUiState
import com.example.ui.AppUpdateUiState
import com.example.security.VerifiedAppUpdateRelease
import com.example.security.VerifiedNativeApk
import com.example.ui.screens.MainScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithTag("sign_out_button")
            .performScrollTo()
            .assertExists()
            .assertIsDisplayed()
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
    fun signOutButtonInvokesTheSignOutAction() {
        var signOutCalled = false
        composeRule.setContent {
            MainScreen(
                state = MainUiState(isToolsLoading = false),
                onRunRepair = {},
                onRetryAvailability = {},
                onSelectTool = {},
                onDismissToolPicker = {},
                onSubmitUserAction = {},
                onDismissExecution = {},
                onSignOut = { signOutCalled = true }
            )
        }

        composeRule.onNodeWithTag("sign_out_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(signOutCalled) }
    }

    @Test
    fun verifiedNewerApkOffersTheAndroidInstallerHandoff() {
        val release = VerifiedAppUpdateRelease(
            releaseId = "123e4567-e89b-12d3-a456-426614174000",
            channel = "stable",
            releaseNumber = 3,
            appVersionName = "1.2.0",
            appVersionCode = 12L,
            minSupportedAppVersionCode = 1L,
            mandatory = false,
            releaseNotes = "Security and stability improvements.",
            contentPackageCount = 0,
            nativeApk = VerifiedNativeApk(
                assetId = "android-apk",
                sha256 = "a".repeat(64),
                sizeBytes = 1024L,
                contentType = "application/vnd.android.package-archive",
                packageName = "com.aistudio.mahirepair.qrzntk",
                versionCode = 12L,
                signingCertificateSha256 = "b".repeat(64)
            )
        )
        var installRequested = false

        composeRule.setContent {
            MainScreen(
                state = MainUiState(
                    isToolsLoading = false,
                    appUpdate = AppUpdateUiState(release = release)
                ),
                onRunRepair = {},
                onRetryAvailability = {},
                onSelectTool = {},
                onDismissToolPicker = {},
                onSubmitUserAction = {},
                onDismissExecution = {},
                onCheckForAppUpdates = {},
                onInstallAppUpdate = { installRequested = true }
            )
        }

        composeRule.onNodeWithTag("install_app_update_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertTrue(installRequested) }
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
