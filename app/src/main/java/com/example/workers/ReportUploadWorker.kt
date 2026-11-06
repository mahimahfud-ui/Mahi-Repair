package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MahiRepairApplication
import kotlinx.coroutines.CancellationException

class ReportUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reportId = inputData.getString(KEY_REPORT_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        return try {
            val app = applicationContext as MahiRepairApplication
            val result = app.toolRepository.uploadPendingReport(reportId)
            if (result.isSuccess) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_REPORT_ID = "repair_report_id"
    }
}
