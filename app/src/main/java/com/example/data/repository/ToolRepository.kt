package com.example.data.repository

import android.os.Build
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.RepairHistoryEntity
import com.example.data.local.ToolEntity
import com.example.data.remote.SupabaseClient
import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.data.remote.dto.StepRunResultDto
import com.example.data.remote.dto.SubmitRunRequest
import com.example.domain.models.RepairRunReport
import com.example.domain.models.RepairTool
import com.example.domain.models.StepStatus
import com.example.domain.models.ToolCategory
import com.example.security.RecipeContractVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ToolRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    private val toolDao = database.toolDao()
    private val historyDao = database.repairHistoryDao()
    private val recipeSlugsByToolId = ConcurrentHashMap<String, String>()
    private val syncMutex = Mutex()
    private val deviceRegistrationMutex = Mutex()

    val lastSyncFlow: Flow<Long> = preferencesManager.lastSyncFlow

    val toolsFlow: Flow<List<RepairTool>> = toolDao.getEnabledToolsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Device identifiers are issued by Supabase. A random local UUID is never an
     * acceptable substitute because it would bypass server-side device ownership checks.
     */
    private suspend fun ensureRegisteredDeviceId(accessToken: String): String =
        deviceRegistrationMutex.withLock {
            val savedId = preferencesManager.deviceIdFlow.first()
            if (!savedId.isNullOrBlank()) {
                UUID.fromString(savedId)
                return@withLock savedId
            }

            val request = RegisterDeviceRequest(
                deviceId = null,
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".take(60),
                appVersion = BuildConfig.VERSION_NAME,
                androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                androidApi = Build.VERSION.SDK_INT
            )
            val result = supabaseClient.registerDevice(request, accessToken)
            val deviceId = result.getOrElse { cause ->
                throw IllegalStateException("This device could not be authorized by the repair service.", cause)
            }.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The repair service returned an invalid device identity.")

            UUID.fromString(deviceId)
            preferencesManager.saveDeviceId(deviceId)
            deviceId
        }

    private fun verifySignatureAlgorithm(algorithm: String) {
        if (algorithm != "RSA-SHA256") {
            throw IllegalArgumentException("The repair release uses an unsupported signature algorithm.")
        }
    }

    private fun verifiedPayload(
        signedPayload: String,
        signature: String,
        signatureAlgorithm: String,
        expectedVersion: Int
    ): org.json.JSONObject {
        verifySignatureAlgorithm(signatureAlgorithm)
        val publicKey = BuildConfig.RECIPE_SIGNING_PUBLIC_KEY_PEM
        val recipe = RecipeContractVerifier.verifyAndParse(signedPayload, signature, publicKey)
        val payloadVersion = recipe.getInt("version")
        if (payloadVersion != expectedVersion) {
            throw IllegalArgumentException("The signed repair release version does not match the server authorization.")
        }
        return recipe
    }

    /**
     * Refresh session, register/heartbeat the server-issued device, and verify every
     * signed release before it is written to the local catalog. No bundled fallback exists.
     */
    suspend fun syncTools(): Result<Int> {
        if (!supabaseClient.isConfigured()) {
            return Result.failure(IllegalStateException("The repair service is not configured."))
        }

        val sessionResult = authRepository.getValidSession()
        if (sessionResult.isFailure) {
            return Result.failure(sessionResult.exceptionOrNull() ?: IllegalStateException("Please sign in."))
        }

        return try {
            val session = sessionResult.getOrThrow()
            val deviceId = ensureRegisteredDeviceId(session.token)
            val remoteResult = supabaseClient.getMyRepairTools(deviceId, session.token)
            if (remoteResult.isFailure) {
                throw remoteResult.exceptionOrNull() ?: IllegalStateException("Repair catalog synchronization failed.")
            }

            val dtos = remoteResult.getOrThrow()
            val now = System.currentTimeMillis()
            val nextRecipeSlugs = mutableMapOf<String, String>()
            val entities = dtos.map { dto ->
                UUID.fromString(dto.toolId)
                val recipe = verifiedPayload(
                    signedPayload = dto.signedPayload,
                    signature = dto.signature,
                    signatureAlgorithm = dto.signatureAlgorithm,
                    expectedVersion = dto.version
                )
                nextRecipeSlugs[dto.toolId] = recipe.getString("toolId")
                val category = recipe.getString("category")
                val minAndroidApi = recipe.optInt("minAndroidApi", 23)
                val minAppVersionText = recipe.optString("minAppVersion", "1.0.0")
                val minAppVersion = minAppVersionText.substringBefore('.').toIntOrNull() ?: 1
                val title = recipe.getString("title")
                val changelog = recipe.optString("changelog", "").ifBlank {
                    "Verified repair instructions."
                }

                ToolEntity(
                    id = dto.toolId,
                    name = title,
                    description = changelog,
                    category = ToolCategory.fromCategory(category).title,
                    version = dto.version.toString(),
                    problemDescription = "Guided repair for $title.",
                    minAppVersion = minAppVersion,
                    minAndroidSdk = minAndroidApi,
                    isNew = true,
                    enabled = true,
                    targetPackage = null,
                    // The signature is verified at sync and fetched/verified again at execution.
                    // Do not treat cached payloads as current authorization.
                    rawSignedPayload = "",
                    signatureBase64 = "",
                    lastUpdated = now
                )
            }

            // Only replace the local catalog after every returned recipe has passed verification.
            toolDao.clearAll()
            if (entities.isNotEmpty()) toolDao.insertTools(entities)
            recipeSlugsByToolId.clear()
            recipeSlugsByToolId.putAll(nextRecipeSlugs)
            preferencesManager.updateLastSyncTime(now)
            Result.success(entities.size)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not securely synchronize repair tools.", error))
        }
    }

    /**
     * Always fetch a current signed release from the server at execution time.
     * A cached payload cannot prove that the member or device is still authorized.
     */
    suspend fun getSignedPayloadAndSignature(toolId: String): Result<Pair<String, String>> {
        return try {
            if (!supabaseClient.isConfigured()) {
                throw IllegalStateException("The repair service is not configured.")
            }
            val session = authRepository.getValidSession().getOrElse { cause ->
                throw IllegalStateException("Please sign in again before running a repair.", cause)
            }
            val deviceId = ensureRegisteredDeviceId(session.token)
            val signed = supabaseClient.getSignedRepairTool(toolId, deviceId, session.token).getOrElse { cause ->
                throw IllegalStateException("This repair is no longer authorized for this device.", cause)
            }
            val recipe = verifiedPayload(
                signedPayload = signed.signedPayload,
                signature = signed.signature,
                signatureAlgorithm = signed.signatureAlgorithm,
                expectedVersion = signed.version
            )
            val expectedSlug = recipeSlugsByToolId[toolId]
                ?: throw IllegalStateException("Refresh the repair list before running this repair.")
            if (recipe.getString("toolId") != expectedSlug) {
                throw IllegalArgumentException("The repair release does not match the selected repair.")
            }
            Result.success(signed.signedPayload to signed.signature)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun recordRepairRun(report: RepairRunReport) {
        val entity = RepairHistoryEntity(
            id = report.runId,
            toolId = report.toolId,
            toolName = report.toolName,
            toolVersion = report.toolVersion,
            timestamp = report.endTime,
            durationMs = report.endTime - report.startTime,
            result = report.result.name,
            diagnosis = report.finalDiagnosis,
            userEmail = report.userEmail
        )
        historyDao.insertRecord(entity)

        if (!supabaseClient.isConfigured()) return
        val sessionResult = authRepository.getValidSession()
        if (sessionResult.isFailure) return

        runCatching {
            val session = sessionResult.getOrThrow()
            val deviceId = ensureRegisteredDeviceId(session.token)
            val stepResults = report.stepRecords.map { record ->
                val status = when (record.status) {
                    StepStatus.COMPLETED -> "SUCCESS"
                    StepStatus.SKIPPED -> "SKIPPED"
                    StepStatus.CANCELLED -> "CANCELLED"
                    else -> "FAILED"
                }
                StepRunResultDto(
                    stepId = record.stepId,
                    status = status,
                    errorCode = if (status == "FAILED") "ERR_STEP_FAILED" else null
                )
            }
            val version = report.toolVersion.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid repair version in execution report.")
            val request = SubmitRunRequest(
                deviceId = deviceId,
                toolId = report.toolId,
                toolVersion = version,
                startedAt = Instant.ofEpochMilli(report.startTime).toString(),
                completedAt = Instant.ofEpochMilli(report.endTime).toString(),
                result = report.result.name,
                stepResults = stepResults,
                errorCode = if (report.result.name == "FAILED") "ERR_RUN_FAILED" else null,
                appVersion = BuildConfig.VERSION_NAME
            )
            supabaseClient.submitRepairRun(request, session.token).getOrThrow()
        }
    }

    private fun ToolEntity.toDomain(): RepairTool {
        return RepairTool(
            id = id,
            name = name,
            description = description,
            category = ToolCategory.fromCategory(category),
            version = version,
            problemDescription = problemDescription,
            minAppVersion = minAppVersion,
            minAndroidSdk = minAndroidSdk,
            isNew = isNew,
            enabled = enabled,
            targetPackage = targetPackage,
            lastUpdated = lastUpdated
        )
    }
}
