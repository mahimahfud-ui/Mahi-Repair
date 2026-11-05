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
import com.example.domain.models.StepExecutionRecord
import com.example.domain.models.StepStatus
import com.example.security.RsaSignatureVerifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
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

    /**
     * Resolves pending user confirmation or interactive guide.
     */
    fun submitUserActionResponse(approved: Boolean) {
        pendingUserResponse?.complete(approved)
    }

    /**
     * Parses and strictly validates a JSON recipe string.
     */
    fun parseRecipeJson(jsonString: String): Result<RepairRecipe> {
        return try {
            val root = JSONObject(jsonString)
            val recipeId = root.optString("recipe_id", UUID.randomUUID().toString())
            val toolId = root.getString("tool_id")
            val version = root.optString("version", "1.0.0")
            val targetPkg = if (root.has("target_package")) root.optString("target_package").takeIf { it.isNotBlank() } else null
            val requiredSdk = root.optInt("required_android_sdk", 26)

            val stepsJson = root.getJSONArray("steps")
            val stepList = mutableListOf<RecipeStep>()

            for (i in 0 until stepsJson.length()) {
                val stepObj = stepsJson.getJSONObject(i)
                val actionId = stepObj.getString("action")
                val actionType = ActionType.fromIdentifier(actionId)
                    ?: return Result.failure(
                        IllegalArgumentException("Unknown or unallowlisted action: '$actionId'. Recipe rejected.")
                    )

                val id = stepObj.optString("id", "step_$i")
                val name = stepObj.getString("name")
                val description = stepObj.optString("description", "")
                val isRequired = stepObj.optBoolean("is_required", true)
                val stopOnFailure = stepObj.optBoolean("stop_on_failure", true)

                val paramsMap = mutableMapOf<String, Any?>()
                if (stepObj.has("parameters")) {
                    val pObj = stepObj.getJSONObject("parameters")
                    val keys = pObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = pObj.get(key)
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

                val step = RecipeStep(
                    id = id,
                    action = actionType,
                    name = name,
                    description = description,
                    parameters = paramsMap,
                    isRequired = isRequired,
                    stopOnFailure = stopOnFailure
                )

                // Validate step parameters
                val validation = actionRegistry.validateStep(step)
                if (validation.isFailure) {
                    return Result.failure(validation.exceptionOrNull()!!)
                }

                stepList.add(step)
            }

            Result.success(
                RepairRecipe(
                    recipeId = recipeId,
                    toolId = toolId,
                    version = version,
                    targetPackage = targetPkg,
                    requiredAndroidSdk = requiredSdk,
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
        val isVerified = if (!publicKeyPem.isNullOrBlank()) {
            RsaSignatureVerifier.verify(rawSignedPayload, signatureBase64, publicKeyPem)
        } else {
            RsaSignatureVerifier.verify(rawSignedPayload, signatureBase64)
        }

        if (!isVerified) {
            val failRecord = StepExecutionRecord(
                stepId = "verify_sig",
                actionName = "RSA_SIGNATURE_VERIFICATION",
                title = "Verify Owner Signature",
                status = StepStatus.FAILED,
                message = "Digital signature verification failed. Recipe rejected.",
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
                finalDiagnosis = "Security Error: The recipe signature did not match the Owner's trusted public key. Execution aborted."
            )
            _engineState.value = ExecutionEvent.SignatureFailed("Invalid Owner signature.")
            _engineState.value = ExecutionEvent.Finished(report)
            return report
        }

        // 2. Schema and Parsing
        val recipeResult = parseRecipeJson(rawSignedPayload)
        if (recipeResult.isFailure) {
            val err = recipeResult.exceptionOrNull()?.message ?: "Invalid recipe schema"
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

        // 3. Android SDK Compatibility
        if (Build.VERSION.SDK_INT < recipe.requiredAndroidSdk) {
            val msg = "Device running Android API ${Build.VERSION.SDK_INT}, but recipe requires API ${recipe.requiredAndroidSdk}+."
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
            _engineState.value = ExecutionEvent.IncompatibleDevice(recipe.requiredAndroidSdk, Build.VERSION.SDK_INT)
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

            _engineState.value = ExecutionEvent.Running(
                tool = tool,
                totalSteps = recipe.steps.size,
                currentStepIndex = index,
                currentStep = step,
                stepRecords = stepRecords.toList()
            )

            val stepStart = System.currentTimeMillis()
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
            RepairRunResult.SUCCESS -> "All repair and diagnostic steps passed successfully. Device issue resolved."
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
