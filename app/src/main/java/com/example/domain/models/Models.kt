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

enum class StepRisk(val id: String) {
    READ_ONLY("read_only"),
    GUIDED("guided"),
    PERMISSION("permission");

    companion object {
        fun fromId(id: String): StepRisk? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

data class StepCondition(
    val previousStep: String,
    val equals: String
)

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

enum class ToolCategory(val title: String, val slug: String) {
    ALL("All", "all"),
    NETWORK("Network", "network"),
    APPS("Apps", "apps"),
    STORAGE("Storage", "storage"),
    CONNECTIVITY("Connectivity", "connectivity"),
    SYSTEM("System & Storage", "system"),
    GUIDES("Guides", "guides"),
    MEDIA("Media & Streaming", "apps"),
    SOCIAL("Social Apps", "apps"),
    GENERAL("General", "apps");

    companion object {
        fun fromCategory(value: String): ToolCategory {
            return entries.firstOrNull {
                it.slug.equals(value, ignoreCase = true) || it.title.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: GENERAL
        }
    }
}

data class RecipeStep(
    val id: String,
    val action: ActionType,
    val params: Map<String, Any?> = emptyMap(),
    val risk: StepRisk = StepRisk.READ_ONLY,
    val requiresConfirmation: Boolean = false,
    val condition: StepCondition? = null
) {
    val name: String
        get() = when (action) {
            ActionType.CHECK_INTERNET -> "Check Internet Connection"
            ActionType.DNS_LOOKUP -> "Resolve ${params["host"] ?: params["hostname"] ?: "DNS"}"
            ActionType.HTTPS_PROBE -> "Probe ${params["host"] ?: params["url"] ?: "Server"}"
            ActionType.CHECK_APP_INSTALLED -> "Check App Installed (${params["packageName"] ?: params["package_name"] ?: ""})"
            ActionType.CHECK_STORAGE -> "Check Storage (${params["minimumAvailableMb"] ?: params["min_free_mb"] ?: 500} MB)"
            ActionType.OPEN_APP -> "Launch ${params["packageName"] ?: params["package_name"] ?: "App"}"
            ActionType.OPEN_APP_DETAILS -> "App Settings (${params["packageName"] ?: params["package_name"] ?: "App"})"
            ActionType.OPEN_ANDROID_SETTINGS -> "Open ${params["screen"] ?: params["setting_type"] ?: "Android"} Settings"
            ActionType.SHOW_GUIDE -> (params["title"] as? String) ?: (params["guide_title"] as? String) ?: "Troubleshooting Guide"
            ActionType.ASK_USER_CONFIRMATION -> (params["title"] as? String) ?: "User Confirmation"
            ActionType.REQUEST_OWN_APP_PERMISSION -> "Permission: ${params["permission"] ?: ""}"
            ActionType.VERIFY_INTERNET -> "Verify Internet Connectivity"
            ActionType.VERIFY_DNS -> "Verify DNS Resolution (${params["host"] ?: params["domain"] ?: "DNS"})"
        }

    val description: String
        get() = when (action) {
            ActionType.SHOW_GUIDE -> (params["description"] as? String) ?: (params["guide_description"] as? String) ?: "Follow on-screen instructions"
            ActionType.ASK_USER_CONFIRMATION -> (params["message"] as? String) ?: "Confirm to proceed"
            ActionType.HTTPS_PROBE -> "Test connection latency to ${params["host"] ?: ""}"
            ActionType.DNS_LOOKUP -> "Verify domain resolution for ${params["host"] ?: ""}"
            else -> "Execute native allowlisted diagnostic action"
        }

    val parameters: Map<String, Any?> get() = params
    val isRequired: Boolean get() = risk != StepRisk.GUIDED || action == ActionType.SHOW_GUIDE
    val stopOnFailure: Boolean get() = action == ActionType.CHECK_INTERNET || action == ActionType.VERIFY_INTERNET
}

data class RepairRecipe(
    val schemaVersion: Int = 1,
    val toolId: String,
    val title: String,
    val version: Int,
    val category: String,
    val minAndroidApi: Int = 23,
    val minAppVersion: String = "1.0.0",
    val estimatedSeconds: Int = 60,
    val changelog: String? = null,
    val steps: List<RecipeStep> = emptyList()
) {
    val recipeId: String get() = toolId
    val requiredAndroidSdk: Int get() = minAndroidApi
}

data class RepairTool(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val version: String,
    val problemDescription: String,
    val minAppVersion: Int = 1,
    val minAndroidSdk: Int = 23,
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
