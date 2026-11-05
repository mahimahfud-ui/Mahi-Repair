package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DefaultToolCatalog
import com.example.data.local.PreferencesManager
import com.example.data.local.RepairHistoryEntity
import com.example.data.local.ToolEntity
import com.example.data.remote.SupabaseClient
import com.example.data.remote.dto.RepairRunReportRequest
import com.example.data.remote.dto.StepReportDto
import com.example.domain.models.RepairRunReport
import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

    suspend fun syncTools(): Result<Int> {
        val session = preferencesManager.sessionFlow.first()

        if (supabaseClient.isConfigured()) {
            if (!session.isAuthenticated) {
                return Result.failure(IllegalStateException("User is not signed in."))
            }

            val remoteResult = supabaseClient.getMyRepairTools(session.token)
            return if (remoteResult.isSuccess) {
                val dtos = remoteResult.getOrThrow()
                val entities = dtos.filter { it.enabled }.map { dto ->
                    ToolEntity(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description,
                        category = dto.category,
                        version = dto.version,
                        problemDescription = dto.problemDescription,
                        minAppVersion = dto.minAppVersion,
                        minAndroidSdk = dto.minAndroidSdk,
                        isNew = dto.isNew,
                        enabled = dto.enabled,
                        targetPackage = dto.targetPackage,
                        rawSignedPayload = dto.rawSignedPayload ?: "",
                        signatureBase64 = dto.signatureBase64 ?: "",
                        lastUpdated = dto.updatedAt
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
            val remoteResult = supabaseClient.getSignedRepairTool(toolId, session.token)
            if (remoteResult.isSuccess) {
                val signed = remoteResult.getOrThrow()
                return Result.success(Pair(signed.signedPayload, signed.signatureBase64))
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
            val req = RepairRunReportRequest(
                runId = report.runId,
                toolId = report.toolId,
                toolName = report.toolName,
                toolVersion = report.toolVersion,
                userEmail = report.userEmail,
                deviceModel = report.deviceModel,
                androidSdk = report.androidSdk,
                startTime = report.startTime,
                endTime = report.endTime,
                result = report.result.name,
                finalDiagnosis = report.finalDiagnosis,
                steps = report.stepRecords.map {
                    StepReportDto(
                        stepId = it.stepId,
                        action = it.actionName,
                        title = it.title,
                        status = it.status.name,
                        message = it.message,
                        durationMs = it.durationMs
                    )
                }
            )
            supabaseClient.submitRepairRun(req, session.token)
        }
    }

    private fun ToolEntity.toDomain(): RepairTool {
        val cat = ToolCategory.entries.firstOrNull { it.title.equals(category, ignoreCase = true) }
            ?: ToolCategory.GENERAL

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
