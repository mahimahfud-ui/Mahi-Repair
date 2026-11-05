package com.example.domain.models

/**
 * All allowlisted native repair actions supported by Mahi Repair.
 * Arbitrary scripts or unallowlisted actions are strictly rejected.
 */
enum class ActionType(val identifier: String) {
    CHECK_INTERNET("CHECK_INTERNET"),
    DNS_LOOKUP("DNS_LOOKUP"),
    HTTPS_PROBE("HTTPS_PROBE"),
    CHECK_APP_INSTALLED("CHECK_APP_INSTALLED"),
    CHECK_STORAGE("CHECK_STORAGE"),
    OPEN_APP("OPEN_APP"),
    OPEN_APP_DETAILS("OPEN_APP_DETAILS"),
    OPEN_ANDROID_SETTINGS("OPEN_ANDROID_SETTINGS"),
    SHOW_GUIDE("SHOW_GUIDE"),
    ASK_USER_CONFIRMATION("ASK_USER_CONFIRMATION"),
    REQUEST_OWN_APP_PERMISSION("REQUEST_OWN_APP_PERMISSION"),
    VERIFY_INTERNET("VERIFY_INTERNET"),
    VERIFY_DNS("VERIFY_DNS");

    companion object {
        fun fromIdentifier(id: String): ActionType? {
            return entries.firstOrNull { it.identifier.equals(id, ignoreCase = true) }
        }
    }
}

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    WAITING_USER,
    CANCELLED,
    SKIPPED
}

enum class RepairRunResult {
    SUCCESS,
    PARTIAL,
    FAILED,
    CANCELLED
}

enum class ToolCategory(val title: String) {
    ALL("All"),
    NETWORK("Network"),
    SOCIAL("Social Apps"),
    SYSTEM("System & Storage"),
    MEDIA("Media & Streaming"),
    GENERAL("General")
}

data class RecipeStep(
    val id: String,
    val action: ActionType,
    val name: String,
    val description: String = "",
    val parameters: Map<String, Any?> = emptyMap(),
    val isRequired: Boolean = true,
    val stopOnFailure: Boolean = true
)

data class RepairRecipe(
    val recipeId: String,
    val toolId: String,
    val version: String,
    val targetPackage: String? = null,
    val requiredAndroidSdk: Int = 26,
    val steps: List<RecipeStep> = emptyList()
)

data class RepairTool(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val version: String,
    val problemDescription: String,
    val minAppVersion: Int = 1,
    val minAndroidSdk: Int = 26,
    val isNew: Boolean = false,
    val enabled: Boolean = true,
    val targetPackage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class StepExecutionRecord(
    val stepId: String,
    val actionName: String,
    val title: String,
    val status: StepStatus,
    val message: String,
    val durationMs: Long,
    val details: Map<String, String> = emptyMap()
)

data class RepairRunReport(
    val runId: String,
    val toolId: String,
    val toolName: String,
    val toolVersion: String,
    val userEmail: String,
    val deviceModel: String,
    val androidSdk: Int,
    val startTime: Long,
    val endTime: Long,
    val result: RepairRunResult,
    val stepRecords: List<StepExecutionRecord>,
    val finalDiagnosis: String
)
