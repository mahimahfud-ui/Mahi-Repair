package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.PendingReportEntity
import com.example.data.local.RepairHistoryEntity
import com.example.data.local.ToolEntity
import com.example.data.remote.SupabaseClient
import com.example.data.remote.ServerAuthorizationException
import com.example.data.remote.InvalidAuthSessionException
import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.data.remote.dto.StepRunResultDto
import com.example.data.remote.dto.SubmitRunRequest
import com.example.domain.models.RepairRunReport
import com.example.domain.models.RepairTool
import com.example.domain.models.StepStatus
import com.example.domain.models.ToolCategory
import com.example.security.RecipeContractVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.sync.SyncManager
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
    private val context: Context,
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    private val toolDao = database.toolDao()
    private val historyDao = database.repairHistoryDao()
    private val reportOutboxDao = database.reportOutboxDao()
    private val reportAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(RepairRunReport::class.java)
    private val syncMutex = Mutex()
    private val _accessDeniedMessage = MutableStateFlow<String?>(null)
    val accessDeniedMessage: StateFlow<String?> = _accessDeniedMessage.asStateFlow()
    private val deviceRegistrationMutex = Mutex()

    val lastSyncFlow: Flow<Long> = preferencesManager.lastSyncFlow
    val pendingReportCountFlow: Flow<Int> = reportOutboxDao.observeCount()

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
            if (!savedId.isNullOrBlank() && savedOwner.equals(ownerEmail, ignoreCase = true)) {
                UUID.fromString(savedId)
                return@withLock savedId
            }
            if (!savedId.isNullOrBlank()) preferencesManager.clearDeviceId()

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
            preferencesManager.saveDeviceId(deviceId, ownerEmail)
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
            val cause = sessionResult.exceptionOrNull()
            if (cause is InvalidAuthSessionException) {
                handleAuthorizationRejection(cause)
            }
            return@withLock Result.failure(cause ?: IllegalStateException("Please sign in."))
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
            require(dtos.map { it.toolId }.distinct().size == dtos.size) {
                "The server returned duplicate repair identifiers."
            }
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
                    lastUpdated = now,
                    recipeSlug = recipeSlug
                )
            }

            require(entities.map { it.recipeSlug }.distinct().size == entities.size) {
                "The server returned duplicate signed recipe identifiers."
            }

            // The mapping lives in each Room row, so catalogue metadata and slug identity
            // become visible together or not at all. An empty verified list is a valid replacement.
            toolDao.replaceAll(entities)
            preferencesManager.updateLastSyncTime(now)
            _accessDeniedMessage.value = null
            Result.success(entities.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            handleAuthorizationRejection(error)
            Result.failure(IllegalStateException("Could not securely synchronize repair tools.", error))
        }
    }

    private suspend fun handleAuthorizationRejection(error: Throwable) {
        val causes = generateSequence(error) { it.cause }.toList()
        val rejection = causes.filterIsInstance<ServerAuthorizationException>().firstOrNull()
        val expiredSession = causes.any { it is InvalidAuthSessionException }
        if (rejection == null && !expiredSession) return

        // Publish the reason before changing DataStore; session observers may react immediately.
        _accessDeniedMessage.value = when {
            rejection?.statusCode == 403 ->
                "Access to Mahi Repair was denied for this account or device. Sign in again or contact the Owner."
            else -> "Your repair session expired or is no longer authorized. Please sign in again."
        }
        clearLocalCatalog()
        preferencesManager.clearDeviceId()
        authRepository.invalidateLocalSession()
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            handleAuthorizationRejection(error)
            Result.failure(error)
        }
    }

    suspend fun clearLocalCatalog() {
        toolDao.clearAll()
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
            val catalogEntry = toolDao.getToolById(toolId)
                ?: throw IllegalStateException("Refresh the repair list before running this repair.")
            val expectedSlug = catalogEntry.recipeSlug.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Refresh the repair list before running this repair.")
            if (recipe.getString("toolId") != expectedSlug) {
                throw IllegalArgumentException("The repair release does not match the selected repair.")
            }
            Result.success(signed.signedPayload to signed.signature)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            handleAuthorizationRejection(error)
            Result.failure(error)
        }
    }

    /**
     * Commit local history and its pending upload together. Uploading is deliberately
     * decoupled so a missing network never loses a completed repair report.
     */
    suspend fun recordRepairRun(report: RepairRunReport): Result<Unit> {
        return try {
            val history = RepairHistoryEntity(
                id = report.runId,
                toolId = report.toolId,
                toolName = report.toolName,
                toolVersion = report.toolVersion,
                timestamp = report.endTime,
                durationMs = (report.endTime - report.startTime).coerceAtLeast(0L),
                result = report.result.name,
                diagnosis = report.finalDiagnosis,
                userEmail = report.userEmail
            )
            val pending = PendingReportEntity(
                id = report.runId,
                payloadJson = reportAdapter.toJson(report),
                createdAt = System.currentTimeMillis(),
                deviceId = preferencesManager.deviceIdFlow.first(),
                appVersion = BuildConfig.VERSION_NAME
            )
            database.withTransaction {
                historyDao.insertRecord(history)
                reportOutboxDao.enqueue(pending)
            }
            runCatching { SyncManager.enqueueReportUpload(context, report.runId) }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun enqueuePendingReportUploads() {
        reportOutboxDao.getPendingIds().forEach { reportId ->
            runCatching { SyncManager.enqueueReportUpload(context, reportId) }
        }
    }

    suspend fun pendingReportCount(): Int = reportOutboxDao.count()

    fun clearAccessDeniedMessage() {
        _accessDeniedMessage.value = null
    }

    /** Called by WorkManager. A row is deleted only after the server acknowledges this exact UUID. */
    suspend fun uploadPendingReport(reportId: String): Result<Boolean> {
        return try {
            val pending = reportOutboxDao.get(reportId) ?: return Result.success(false)
            val report = reportAdapter.fromJson(pending.payloadJson)
                ?: throw IllegalStateException("The pending repair report could not be decoded.")
            require(report.runId == reportId) { "Pending report identifier mismatch." }
            if (!supabaseClient.isConfigured()) {
                throw IllegalStateException("The repair service is not configured.")
            }
            val session = authRepository.getValidSession().getOrElse { cause ->
                throw IllegalStateException("Sign in again before pending reports can upload.", cause)
            }
            require(session.email.equals(report.userEmail, ignoreCase = true)) {
                "Sign in with the account that created this repair report before uploading it."
            }
            val deviceId = pending.deviceId?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The original device identity for this report is unavailable.")
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
                runId = report.runId,
                deviceId = deviceId,
                toolId = report.toolId,
                toolVersion = version,
                startedAt = Instant.ofEpochMilli(report.startTime).toString(),
                completedAt = Instant.ofEpochMilli(report.endTime).toString(),
                result = report.result.name,
                stepResults = stepResults,
                errorCode = if (report.result.name == "FAILED") "ERR_RUN_FAILED" else null,
                appVersion = pending.appVersion
            )
            val acceptedRunId = supabaseClient.submitRepairRun(request, session.token).getOrThrow()
            require(acceptedRunId == report.runId) { "Server acknowledged a different repair report." }
            reportOutboxDao.delete(reportId)
            Result.success(true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            handleAuthorizationRejection(error)
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
