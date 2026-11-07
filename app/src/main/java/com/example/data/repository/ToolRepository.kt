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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class RealtimeIdentity(
    val userId: String,
    val deviceId: String,
    val accessToken: String,
    val expiresAtMillis: Long
)

class ToolRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    private val toolDao = database.toolDao()
    private val historyDao = database.repairHistoryDao()
    private val recipeSlugsByToolId = MutableStateFlow<Map<String, String>>(emptyMap())
    private val syncMutex = Mutex()
    private val deviceRegistrationMutex = Mutex()
    @Volatile private var heartbeatDeviceId: String? = null
    @Volatile private var heartbeatOwnerEmail: String? = null

    val lastSyncFlow: Flow<Long> = preferencesManager.lastSyncFlow

    val toolsFlow: Flow<List<RepairTool>> = toolDao.getEnabledToolsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Device identifiers are issued by Supabase. A random local UUID is never an
     * acceptable substitute because it would bypass server-side device ownership checks.
     */
    private suspend fun ensureRegisteredDeviceId(accessToken: String, ownerEmail: String): String =
        deviceRegistrationMutex.withLock {
            val savedId = preferencesManager.deviceIdFlow.first()
            val savedOwner = preferencesManager.deviceOwnerEmailFlow.first()
            val sameOwnerDeviceId = savedId?.takeIf {
                it.isNotBlank() && savedOwner.equals(ownerEmail, ignoreCase = true)
            }
            val normalizedOwner = ownerEmail.trim().lowercase()
            if (
                sameOwnerDeviceId != null &&
                sameOwnerDeviceId == heartbeatDeviceId &&
                normalizedOwner == heartbeatOwnerEmail
            ) {
                return@withLock sameOwnerDeviceId
            }

            // Existing device identities are heartbeated once per app process (and therefore
            // once on app start), not on every tool action. The Edge Functions still re-check
            // device ownership/revocation for catalog and signed-recipe requests. A 403
            // for a revoked UUID is not converted into a fresh registration.
            val request = RegisterDeviceRequest(
                deviceId = sameOwnerDeviceId,
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".take(60),
                appVersion = BuildConfig.VERSION_NAME,
                androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                androidApi = Build.VERSION.SDK_INT
            )
            val result = supabaseClient.registerDevice(request, accessToken)
            val deviceId = result.getOrElse { cause ->
                throw IllegalStateException(
                    if (sameOwnerDeviceId != null) {
                        "This device could not be revalidated. It may have been revoked by the Owner."
                    } else {
                        "This device could not be authorized by the repair service."
                    },
                    cause
                )
            }.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The repair service returned an invalid device identity.")

            UUID.fromString(deviceId)
            if (sameOwnerDeviceId == null || !sameOwnerDeviceId.equals(deviceId, ignoreCase = true)) {
                preferencesManager.saveDeviceId(deviceId, ownerEmail)
            }
            heartbeatDeviceId = deviceId
            heartbeatOwnerEmail = normalizedOwner
            deviceId
        }
    private fun verifySignatureAlgorithm(algorithm: String) {
        RecipeContractVerifier.requireSupportedSignatureAlgorithm(algorithm)
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
    suspend fun syncTools(): Result<Int> = syncMutex.withLock {
        if (!supabaseClient.isConfigured()) {
            return@withLock Result.failure(IllegalStateException("The repair service is not configured."))
        }

        val sessionResult = authRepository.getValidSession()
        if (sessionResult.isFailure) {
            return@withLock Result.failure(
                sessionResult.exceptionOrNull() ?: IllegalStateException("Please sign in.")
            )
        }

        try {
            val session = sessionResult.getOrThrow()
            val deviceId = ensureRegisteredDeviceId(session.token, session.email)
            val remoteResult = supabaseClient.getMyRepairTools(deviceId, session.token)
            if (remoteResult.isFailure) {
                throw remoteResult.exceptionOrNull()
                    ?: IllegalStateException("Repair catalog synchronization failed.")
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
                val recipeSlug = recipe.getString("toolId")
                require(recipeSlug.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))) {
                    "The signed repair tool identifier is invalid."
                }
                nextRecipeSlugs[dto.toolId] = recipeSlug
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
                    rawSignedPayload = "",
                    signatureBase64 = "",
                    lastUpdated = now
                )
            }

            // Do not replace a verified local catalog unless every remote entry passed verification.
            // Publish the verified ID-to-slug map before Room notifies UI collectors.
            recipeSlugsByToolId.value = nextRecipeSlugs
            toolDao.clearAll()
            if (entities.isNotEmpty()) toolDao.insertTools(entities)
            preferencesManager.updateLastSyncTime(now)
            Result.success(entities.size)
        } catch (error: Exception) {
            clearCatalogWhenServerRejects(error)
            Result.failure(IllegalStateException("Could not securely synchronize repair tools.", error))
        }
    }

    private suspend fun clearCatalogWhenServerRejects(error: Throwable) {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        if (Regex("HTTP\\s+(401|403|404)").containsMatchIn(messages)) {
            clearLocalCatalog()
        }
    }

    /**
     * Realtime topic identity is derived from the signed-in JWT subject. The server
     * independently validates the JWT and the device/user relationship through RLS.
     */
    suspend fun getRealtimeIdentity(): Result<RealtimeIdentity> {
        return try {
            val session = authRepository.getValidSession().getOrElse { cause ->
                throw IllegalStateException("Sign in is required for live repair updates.", cause)
            }
            val deviceId = ensureRegisteredDeviceId(session.token, session.email)
            val tokenParts = session.token.split('.')
            require(tokenParts.size == 3) { "The authentication session is malformed." }
            val payload = Base64.decode(
                tokenParts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val userId = JSONObject(String(payload, StandardCharsets.UTF_8))
                .getString("sub")
                .also { UUID.fromString(it) }
            Result.success(RealtimeIdentity(userId, deviceId, session.token, session.expiresAtMillis))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun clearLocalCatalog() {
        toolDao.clearAll()
        recipeSlugsByToolId.value = emptyMap()
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
            val deviceId = ensureRegisteredDeviceId(session.token, session.email)
            val signed = supabaseClient.getSignedRepairTool(toolId, deviceId, session.token).getOrElse { cause ->
                throw IllegalStateException("This repair is no longer authorized for this device.", cause)
            }
            val recipe = verifiedPayload(
                signedPayload = signed.signedPayload,
                signature = signed.signature,
                signatureAlgorithm = signed.signatureAlgorithm,
                expectedVersion = signed.version
            )
            val expectedSlug = recipeSlugsByToolId.value[toolId]
                ?: throw IllegalStateException("Refresh the repair list before running this repair.")
            if (recipe.getString("toolId") != expectedSlug) {
                throw IllegalArgumentException("The repair release does not match the selected repair.")
            }
            Result.success(signed.signedPayload to signed.signature)
        } catch (error: Exception) {
            clearCatalogWhenServerRejects(error)
            Result.failure(error)
        }
    }

    suspend fun recordRepairRun(report: RepairRunReport): Result<Unit> {
        return try {
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

            if (!supabaseClient.isConfigured()) {
                throw IllegalStateException("The repair report cannot be sent right now.")
            }
            val session = authRepository.getValidSession().getOrElse { cause ->
                throw IllegalStateException("The session expired before the report could be sent.", cause)
            }
            val deviceId = ensureRegisteredDeviceId(session.token, session.email)
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
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
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
