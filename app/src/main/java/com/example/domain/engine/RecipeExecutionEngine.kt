package com.example.domain.engine

import android.os.Build
import com.example.domain.actions.ActionRegistry
import com.example.domain.actions.ActionResult
import com.example.domain.actions.UserActionType
import com.example.domain.models.ActionType
import com.example.domain.models.RecipeStep
import com.example.domain.models.RepairRecipe
import com.example.domain.models.RepairRunReport
import com.example.domain.models.RepairRunResult
import com.example.domain.models.RepairTool
import com.example.domain.models.StepCondition
import com.example.domain.models.StepExecutionRecord
import com.example.domain.models.StepRisk
import com.example.domain.models.StepStatus
import com.example.security.RecipeContractVerifier
import com.example.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.UUID

sealed interface ExecutionEvent {
    object Idle : ExecutionEvent
    data class VerifyingSignature(val toolName: String) : ExecutionEvent
    data class SignatureFailed(val reason: String) : ExecutionEvent
    data class IncompatibleDevice(val requiredSdk: Int, val currentSdk: Int) : ExecutionEvent
    data class Running(
        val tool: RepairTool,
        val totalSteps: Int,
        val currentStepIndex: Int,
        val currentStep: RecipeStep,
        val stepRecords: List<StepExecutionRecord>
    ) : ExecutionEvent
    data class PromptUserAction(
        val type: UserActionType,
        val title: String,
        val message: String,
        val steps: List<String> = emptyList(),
        val payload: Map<String, String> = emptyMap()
    ) : ExecutionEvent
    data class Finished(
        val report: RepairRunReport
    ) : ExecutionEvent
}

class RecipeExecutionEngine(
    private val actionRegistry: ActionRegistry
) {

    private val _engineState = MutableStateFlow<ExecutionEvent>(ExecutionEvent.Idle)
    val engineState: StateFlow<ExecutionEvent> = _engineState.asStateFlow()

    // Suspends execution waiting for user confirmation or guide acknowledgement
    private var pendingUserResponse: CompletableDeferred<Boolean>? = null

    private fun isAppVersionSupported(currentVersion: String, requiredVersion: String): Boolean {
        val current = currentVersion.split('.')
        val required = requiredVersion.split('.')
        if (current.isEmpty() || required.isEmpty()) return false
        val count = maxOf(current.size, required.size)
        for (index in 0 until count) {
            val currentPart = if (index < current.size) {
                current[index].toBigIntegerOrNull() ?: return false
            } else BigInteger.ZERO
            val requiredPart = if (index < required.size) {
                required[index].toBigIntegerOrNull() ?: return false
            } else BigInteger.ZERO
            val comparison = currentPart.compareTo(requiredPart)
            if (comparison != 0) return comparison > 0
        }
        return true
    }

    /**
     * Resolves pending user confirmation or interactive guide.
     */
    fun submitUserActionResponse(approved: Boolean) {
        pendingUserResponse?.complete(approved)
    }

    /**
     * Parses and strictly validates a canonical repair-recipe-v1 JSON string.
     */
    fun parseRecipeJson(jsonString: String): Result<RepairRecipe> {
        return try {
            val root = RecipeContractVerifier.validateSchema(jsonString)
            val schemaVersion = root.getInt("schemaVersion")
            val toolId = root.getString("toolId")
            val title = root.getString("title")
            val version = root.getInt("version")
            val category = root.getString("category")
            val minAndroidApi = root.optInt("minAndroidApi", 23)
            val minAppVersion = root.optString("minAppVersion", "1.0.0")
            val estimatedSeconds = root.optInt("estimatedSeconds", 60)
            val changelog = if (root.has("changelog")) root.optString("changelog") else null

            val stepsJson = root.getJSONArray("steps")
            val stepList = mutableListOf<RecipeStep>()
            val seenStepIds = mutableSetOf<String>()

            for (i in 0 until stepsJson.length()) {
                val stepObj = stepsJson.getJSONObject(i)
                val id = stepObj.optString("id", "step_$i")
                if (!seenStepIds.add(id)) {
                    return Result.failure(IllegalArgumentException("Duplicate step ID: '$id'"))
                }

                val actionId = stepObj.getString("action")
                val actionType = ActionType.fromIdentifier(actionId)
                    ?: return Result.failure(
                        IllegalArgumentException("Unknown or unallowlisted action: '$actionId'. Recipe rejected.")
                    )

                val riskStr = stepObj.optString("risk", "read_only")
                val risk = StepRisk.fromId(riskStr)
                    ?: return Result.failure(IllegalArgumentException("Unsupported step risk: '$riskStr'"))
                val requiresConfirmation = stepObj.getBoolean("requiresConfirmation")

                val paramsMap = mutableMapOf<String, Any?>()
                val paramsSource = stepObj.getJSONObject("params")
                run {
                    val keys = paramsSource.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = paramsSource.get(key)
                        if (value is JSONArray) {
                            val list = mutableListOf<String>()
                            for (j in 0 until value.length()) {
                                list.add(value.getString(j))
                            }
                            paramsMap[key] = list
                        } else {
                            paramsMap[key] = value
                        }
                    }
                }

                val condition = if (stepObj.has("condition")) {
                    val cObj = stepObj.getJSONObject("condition")
                    val prevStep = cObj.getString("previousStep")
                    val equals = cObj.getString("equals")
                    StepCondition(prevStep, equals)
                } else null

                val step = RecipeStep(
                    id = id,
                    action = actionType,
                    params = paramsMap,
                    risk = risk,
                    requiresConfirmation = requiresConfirmation,
                    condition = condition
                )

                // Validate step parameters against allowlist
                val validation = actionRegistry.validateStep(step)
                if (validation.isFailure) {
                    return Result.failure(validation.exceptionOrNull()!!)
                }

                stepList.add(step)
            }

            Result.success(
                RepairRecipe(
                    schemaVersion = schemaVersion,
                    toolId = toolId,
                    title = title,
                    version = version,
                    category = category,
                    minAndroidApi = minAndroidApi,
                    minAppVersion = minAppVersion,
                    estimatedSeconds = estimatedSeconds,
                    changelog = changelog,
                    steps = stepList
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Malformed recipe schema: ${e.message}", e))
        }
    }

    /**
     * Executes a signed repair recipe end-to-end.
     *
     * @param tool The metadata for the tool to execute.
     * @param rawSignedPayload The exact JSON string signed by the Owner.
     * @param signatureBase64 The RSA-SHA256 signature in Base64.
     * @param userEmail Email of the current authenticated user.
     * @param publicKeyPem Optional RSA public key override.
     */
    suspend fun executeRecipe(
        tool: RepairTool,
        rawSignedPayload: String,
        signatureBase64: String,
        userEmail: String,
        publicKeyPem: String? = null
    ): RepairRunReport {
        val startTime = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        val stepRecords = mutableListOf<StepExecutionRecord>()

        // 1. Signature Verification
        _engineState.value = ExecutionEvent.VerifyingSignature(tool.name)
        val verificationKey = publicKeyPem?.takeIf { it.isNotBlank() }
            ?: BuildConfig.RECIPE_SIGNING_PUBLIC_KEY_PEM
        val isVerified = runCatching {
            RecipeContractVerifier.verifyAndParse(rawSignedPayload, signatureBase64, verificationKey)
        }.isSuccess

        if (!isVerified) {
            val failRecord = StepExecutionRecord(
                stepId = "verify_sig",
                actionName = "RSA_SIGNATURE_VERIFICATION",
                title = "Checking repair",
                status = StepStatus.FAILED,
                message = "This repair could not be verified. No changes were made.",
                durationMs = System.currentTimeMillis() - startTime
            )
            val report = RepairRunReport(
                runId = runId,
                toolId = tool.id,
                toolName = tool.name,
                toolVersion = tool.version,
                userEmail = userEmail,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                result = RepairRunResult.FAILED,
                stepRecords = listOf(failRecord),
                finalDiagnosis = "This repair could not be verified. No changes were made. Sync the app and try again."
            )
            _engineState.value = ExecutionEvent.SignatureFailed("This repair could not be verified. No changes were made. Sync the app and try again.")
            _engineState.value = ExecutionEvent.Finished(report)
            return report
        }

        // 2. Schema and Parsing
        val recipeResult = parseRecipeJson(rawSignedPayload)
        if (recipeResult.isFailure) {
            val err = "This repair is not compatible with the current app version. Sync the app or try another repair."
            val failRecord = StepExecutionRecord(
                stepId = "parse_schema",
                actionName = "VALIDATE_SCHEMA",
                title = "Validate Schema",
                status = StepStatus.FAILED,
                message = err,
                durationMs = System.currentTimeMillis() - startTime
            )
            val report = RepairRunReport(
                runId = runId,
                toolId = tool.id,
                toolName = tool.name,
                toolVersion = tool.version,
                userEmail = userEmail,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                result = RepairRunResult.FAILED,
                stepRecords = listOf(failRecord),
                finalDiagnosis = "Validation Error: $err"
            )
            _engineState.value = ExecutionEvent.Finished(report)
            return report
        }

        val recipe = recipeResult.getOrThrow()

        // 3. Bind the signed payload to the exact authorized catalog entry.
        // The server returns repair_tools.id (UUID) as the API tool_id,
        // while the signed recipe toolId is the canonical Admin Panel slug.
        // They are deliberately different identifiers; the signed version must match.
        if (recipe.version.toString() != tool.version) {
            val message = "This repair release is out of date. Sync the app and try again."
            val failRecord = StepExecutionRecord(
                stepId = "recipe_identity",
                actionName = "VALIDATE_RECIPE_IDENTITY",
                title = "Validate Repair Release",
                status = StepStatus.FAILED,
                message = message,
                durationMs = System.currentTimeMillis() - startTime
            )
            val report = RepairRunReport(
                runId = runId,
                toolId = tool.id,
                toolName = tool.name,
                toolVersion = tool.version,
                userEmail = userEmail,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                result = RepairRunResult.FAILED,
                stepRecords = listOf(failRecord),
                finalDiagnosis = message
            )
            _engineState.value = ExecutionEvent.Finished(report)
            return report
        }

        // 4. App version compatibility
        if (!isAppVersionSupported(BuildConfig.VERSION_NAME, recipe.minAppVersion)) {
            val message = "Update Mahi Repair to a newer version before running this repair."
            val failRecord = StepExecutionRecord(
                stepId = "app_version_check",
                actionName = "APP_VERSION_COMPATIBILITY",
                title = "App update required",
                status = StepStatus.FAILED,
                message = message,
                durationMs = System.currentTimeMillis() - startTime
            )
            val report = RepairRunReport(
                runId = runId,
                toolId = tool.id,
                toolName = tool.name,
                toolVersion = tool.version,
                userEmail = userEmail,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                result = RepairRunResult.FAILED,
                stepRecords = listOf(failRecord),
                finalDiagnosis = message
            )
            _engineState.value = ExecutionEvent.Finished(report)
            return report
        }

        // 5. Android SDK compatibility
        if (Build.VERSION.SDK_INT < recipe.minAndroidApi) {
            val msg = "This repair requires a newer version of Android and cannot run on this device."
            val failRecord = StepExecutionRecord(
                stepId = "compat_check",
                actionName = "COMPATIBILITY_CHECK",
                title = "Android Version Check",
                status = StepStatus.FAILED,
                message = msg,
                durationMs = System.currentTimeMillis() - startTime
            )
            val report = RepairRunReport(
                runId = runId,
                toolId = tool.id,
                toolName = tool.name,
                toolVersion = tool.version,
                userEmail = userEmail,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                result = RepairRunResult.FAILED,
                stepRecords = listOf(failRecord),
                finalDiagnosis = msg
            )
            _engineState.value = ExecutionEvent.IncompatibleDevice(recipe.minAndroidApi, Build.VERSION.SDK_INT)
            _engineState.value = ExecutionEvent.Finished(report)
            return report
        }

        // 4. Sequential Step Execution
        var overallResult = RepairRunResult.SUCCESS
        var stopPipeline = false

        for ((index, step) in recipe.steps.withIndex()) {
            if (stopPipeline) {
                stepRecords.add(
                    StepExecutionRecord(
                        stepId = step.id,
                        actionName = step.action.identifier,
                        title = step.name,
                        status = StepStatus.SKIPPED,
                        message = "Skipped due to earlier failure.",
                        durationMs = 0
                    )
                )
                continue
            }

            // Check condition if specified
            if (step.condition != null) {
                val prevRecord = stepRecords.firstOrNull { it.stepId == step.condition.previousStep }
                val prevSuccess = prevRecord?.status == StepStatus.COMPLETED
                val shouldRun = if (step.condition.equals == "success") prevSuccess else !prevSuccess
                if (!shouldRun) {
                    stepRecords.add(
                        StepExecutionRecord(
                            stepId = step.id,
                            actionName = step.action.identifier,
                            title = step.name,
                            status = StepStatus.SKIPPED,
                            message = "Skipped: condition on '${step.condition.previousStep}' == ${step.condition.equals} was not met.",
                            durationMs = 0
                        )
                    )
                    continue
                }
            }

            _engineState.value = ExecutionEvent.Running(
                tool = tool,
                totalSteps = recipe.steps.size,
                currentStepIndex = index,
                currentStep = step,
                stepRecords = stepRecords.toList()
            )

            val stepStart = System.currentTimeMillis()

            // Explicit user confirmation required for guided actions
            if (step.requiresConfirmation &&
                step.action != ActionType.ASK_USER_CONFIRMATION &&
                step.action != ActionType.SHOW_GUIDE &&
                step.action != ActionType.REQUEST_OWN_APP_PERMISSION
            ) {
                val deferred = CompletableDeferred<Boolean>()
                pendingUserResponse = deferred

                _engineState.value = ExecutionEvent.PromptUserAction(
                    type = UserActionType.CONFIRMATION,
                    title = "Confirm: ${step.name}",
                    message = "This step will perform a guided action: ${step.description}. Proceed?",
                    payload = mapOf("stepId" to step.id)
                )

                val userApproved = deferred.await()
                pendingUserResponse = null

                if (!userApproved) {
                    stepRecords.add(
                        StepExecutionRecord(
                            stepId = step.id,
                            actionName = step.action.identifier,
                            title = step.name,
                            status = StepStatus.CANCELLED,
                            message = "Cancelled by user.",
                            durationMs = System.currentTimeMillis() - stepStart
                        )
                    )
                    overallResult = RepairRunResult.CANCELLED
                    stopPipeline = true
                    continue
                }
            }

            val result = actionRegistry.executeAction(step)

            if (result.requiresUserAction && result.userActionType != null) {
                // Interactive suspension (Guide or Confirmation)
                val deferred = CompletableDeferred<Boolean>()
                pendingUserResponse = deferred

                val title = result.userActionPayload["title"] ?: step.name
                val message = result.userActionPayload["message"]
                    ?: result.userActionPayload["description"]
                    ?: "Please review this step to continue."
                val rawSteps = result.userActionPayload["steps"]
                val stepsList = if (!rawSteps.isNullOrBlank()) rawSteps.split("|||") else emptyList()

                _engineState.value = ExecutionEvent.PromptUserAction(
                    type = result.userActionType,
                    title = title,
                    message = message,
                    steps = stepsList,
                    payload = result.userActionPayload
                )

                val userApproved = deferred.await()
                pendingUserResponse = null

                if (!userApproved) {
                    val duration = System.currentTimeMillis() - stepStart
                    stepRecords.add(
                        StepExecutionRecord(
                            stepId = step.id,
                            actionName = step.action.identifier,
                            title = step.name,
                            status = StepStatus.CANCELLED,
                            message = "Cancelled by user.",
                            durationMs = duration
                        )
                    )
                    if (step.isRequired) {
                        overallResult = RepairRunResult.CANCELLED
                        stopPipeline = true
                    }
                    continue
                } else {
                    // User confirmed or completed guide
                    val duration = System.currentTimeMillis() - stepStart
                    stepRecords.add(
                        StepExecutionRecord(
                            stepId = step.id,
                            actionName = step.action.identifier,
                            title = step.name,
                            status = StepStatus.COMPLETED,
                            message = "User completed guidance and confirmed step.",
                            durationMs = duration,
                            details = result.details
                        )
                    )
                    continue
                }
            }

            val stepDuration = System.currentTimeMillis() - stepStart

            if (result.status == StepStatus.COMPLETED) {
                stepRecords.add(
                    StepExecutionRecord(
                        stepId = step.id,
                        actionName = step.action.identifier,
                        title = step.name,
                        status = StepStatus.COMPLETED,
                        message = result.message,
                        durationMs = stepDuration,
                        details = result.details
                    )
                )
            } else {
                stepRecords.add(
                    StepExecutionRecord(
                        stepId = step.id,
                        actionName = step.action.identifier,
                        title = step.name,
                        status = StepStatus.FAILED,
                        message = result.message,
                        durationMs = stepDuration,
                        details = result.details
                    )
                )

                if (step.isRequired) {
                    if (step.stopOnFailure) {
                        overallResult = RepairRunResult.FAILED
                        stopPipeline = true
                    } else {
                        overallResult = RepairRunResult.PARTIAL
                    }
                }
            }

            // Small natural delay between steps for smooth UI visualization
            delay(300)
        }

        // 5. Build Final Diagnosis
        val finalDiagnosis = when (overallResult) {
            RepairRunResult.SUCCESS -> "All configured checks and guided steps completed successfully."
            RepairRunResult.PARTIAL -> "Some non-critical steps encountered errors, but required diagnostics succeeded."
            RepairRunResult.FAILED -> {
                val failedStep = stepRecords.firstOrNull { it.status == StepStatus.FAILED }
                if (failedStep != null) {
                    "Issue diagnosed at step '${failedStep.title}': ${failedStep.message}"
                } else {
                    "Repair recipe did not complete successfully."
                }
            }
            RepairRunResult.CANCELLED -> "Repair was cancelled by user."
        }

        val report = RepairRunReport(
            runId = runId,
            toolId = tool.id,
            toolName = tool.name,
            toolVersion = tool.version,
            userEmail = userEmail,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidSdk = Build.VERSION.SDK_INT,
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            result = overallResult,
            stepRecords = stepRecords,
            finalDiagnosis = finalDiagnosis
        )

        _engineState.value = ExecutionEvent.Finished(report)
        return report
    }

    fun reset() {
        pendingUserResponse = null
        _engineState.value = ExecutionEvent.Idle
    }
}
