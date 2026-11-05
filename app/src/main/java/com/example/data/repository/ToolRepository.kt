package com.example.data.repository

import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.DefaultToolCatalog
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant

class ToolRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val supabaseClient: SupabaseClient
) {

    private val toolDao = database.toolDao()
    private val historyDao = database.repairHistoryDao()

    val lastSyncFlow: Flow<Long> = preferencesManager.lastSyncFlow

    val toolsFlow: Flow<List<RepairTool>> = toolDao.getEnabledToolsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun initializeCatalogIfEmpty() {
        val existing = toolDao.getEnabledToolsFlow().first()
        if (existing.isEmpty()) {
            val defaults = DefaultToolCatalog.getInitialTools()
            toolDao.insertTools(defaults)
            preferencesManager.updateLastSyncTime()
        }
    }

    private suspend fun ensureRegisteredDeviceId(authToken: String): String {
        val savedId = preferencesManager.deviceIdFlow.first()
        if (!savedId.isNullOrBlank()) {
            return savedId
        }

        val regReq = RegisterDeviceRequest(
            deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}",
            appVersion = "1.0.0",
            androidVersion = Build.VERSION.RELEASE ?: "14",
            androidApi = Build.VERSION.SDK_INT
        )
        val regRes = supabaseClient.registerDevice(regReq, authToken)
        val deviceId = regRes.getOrNull() ?: java.util.UUID.randomUUID().toString()
        preferencesManager.saveDeviceId(deviceId)
        return deviceId
    }

    suspend fun syncTools(): Result<Int> {
        val session = preferencesManager.sessionFlow.first()

        if (supabaseClient.isConfigured()) {
            if (!session.isAuthenticated) {
                return Result.failure(IllegalStateException("User is not signed in."))
            }

            val deviceId = try {
                ensureRegisteredDeviceId(session.token)
            } catch (e: Exception) {
                return Result.failure(Exception("Device registration failed: ${e.message}"))
            }

            val remoteResult = supabaseClient.getMyRepairTools(deviceId, session.token)
            return if (remoteResult.isSuccess) {
                val dtos = remoteResult.getOrThrow()
                val entities = dtos.map { dto ->
                    var title = dto.toolId
                    var categoryStr = "Apps"
                    var description = "Verified repair tool"
                    var problemDesc = "Troubleshooting and recovery tool"
                    var minSdk = 26
                    var minApp = 1
                    var targetPackage: String? = null

                    try {
                        val parsed = JSONObject(dto.signedPayload)
                        title = parsed.optString("title", dto.toolId)
                        val cat = parsed.optString("category", "apps")
                        categoryStr = ToolCategory.fromCategory(cat).title
                        description = parsed.optString("changelog", "Mahi Repair cloud verified release.")
                        problemDesc = "Resolves issues configured by recipe."
                        minSdk = parsed.optInt("minAndroidApi", 26)
                        val appVer = parsed.optString("minAppVersion", "1.0.0")
                        minApp = appVer.filter { it.isDigit() }.toIntOrNull() ?: 1
                    } catch (_: Exception) {
                        // Keep safe fallback metadata
                    }

                    ToolEntity(
                        id = dto.toolId,
                        name = title,
                        description = description,
                        category = categoryStr,
                        version = dto.version.toString(),
                        problemDescription = problemDesc,
                        minAppVersion = minApp,
                        minAndroidSdk = minSdk,
                        isNew = true,
                        enabled = true,
                        targetPackage = targetPackage,
                        rawSignedPayload = dto.signedPayload,
                        signatureBase64 = dto.signature,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                toolDao.insertTools(entities)
                preferencesManager.updateLastSyncTime()
                Result.success(entities.size)
            } else {
                val err = remoteResult.exceptionOrNull()?.message ?: "Failed to sync"
                Result.failure(Exception("Cloud sync failed: $err. Showing local cached tools."))
            }
        } else {
            // Local verified sync
            initializeCatalogIfEmpty()
            preferencesManager.updateLastSyncTime()
            val count = toolDao.getEnabledToolsFlow().first().size
            return Result.success(count)
        }
    }

    suspend fun getSignedPayloadAndSignature(toolId: String): Result<Pair<String, String>> {
        val cached = toolDao.getToolById(toolId)
        if (cached != null && cached.rawSignedPayload.isNotBlank() && cached.signatureBase64.isNotBlank()) {
            return Result.success(Pair(cached.rawSignedPayload, cached.signatureBase64))
        }

        val session = preferencesManager.sessionFlow.first()
        if (supabaseClient.isConfigured() && session.isAuthenticated) {
            val deviceId = preferencesManager.deviceIdFlow.first() ?: ensureRegisteredDeviceId(session.token)
            val remoteResult = supabaseClient.getSignedRepairTool(toolId, deviceId, session.token)
            if (remoteResult.isSuccess) {
                val signed = remoteResult.getOrThrow()
                return Result.success(Pair(signed.signedPayload, signed.signature))
            }
        }

        return Result.failure(IllegalStateException("No signed recipe available for tool '$toolId'."))
    }

    suspend fun recordRepairRun(report: RepairRunReport) {
        // 1. Save locally to Room audit log
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

        // 2. Submit to Supabase if configured and user is signed in
        val session = preferencesManager.sessionFlow.first()
        if (supabaseClient.isConfigured() && session.isAuthenticated) {
            try {
                val deviceId = preferencesManager.deviceIdFlow.first() ?: ensureRegisteredDeviceId(session.token)
                val isoStarted = Instant.ofEpochMilli(report.startTime).toString()
                val isoCompleted = Instant.ofEpochMilli(report.endTime).toString()
                val toolVer = report.toolVersion.filter { it.isDigit() }.toIntOrNull() ?: 1

                val stepResults = report.stepRecords.map {
                    val statusStr = when (it.status) {
                        StepStatus.COMPLETED -> "SUCCESS"
                        StepStatus.SKIPPED -> "SKIPPED"
                        StepStatus.CANCELLED -> "CANCELLED"
                        else -> "FAILED"
                    }
                    StepRunResultDto(
                        stepId = it.stepId,
                        status = statusStr,
                        errorCode = if (statusStr == "FAILED") "ERR_STEP_FAILED" else null
                    )
                }

                val req = SubmitRunRequest(
                    deviceId = deviceId,
                    toolId = report.toolId,
                    toolVersion = toolVer,
                    startedAt = isoStarted,
                    completedAt = isoCompleted,
                    result = report.result.name,
                    stepResults = stepResults,
                    errorCode = if (report.result.name == "FAILED") "ERR_RUN_FAILED" else null,
                    appVersion = "1.0.0"
                )
                supabaseClient.submitRepairRun(req, session.token)
            } catch (_: Exception) {
                // Network report submission failures do not disrupt local audit log
            }
        }
    }

    private fun ToolEntity.toDomain(): RepairTool {
        val cat = ToolCategory.fromCategory(category)

        return RepairTool(
            id = id,
            name = name,
            description = description,
            category = cat,
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
