package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Delivers persisted repair reports with WorkManager's durable retry/backoff policy. */
class ReportOutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as com.example.MahiRepairApplication
            app.toolRepository.flushPendingReports().fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
