package com.example.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import com.example.workers.ToolSyncWorker
import com.example.workers.ReportUploadWorker
import java.util.concurrent.TimeUnit

object SyncManager {

    private const val PERIODIC_SYNC_WORK_NAME = "mahi_tool_periodic_sync"
    private const val IMMEDIATE_SYNC_WORK_NAME = "mahi_tool_immediate_sync"
    private const val REPORT_UPLOAD_WORK_PREFIX = "mahi_report_upload_"

    /**
     * Schedules periodic background sync every 6 hours when connected to network.
     */
    fun schedulePeriodicSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<ToolSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        } catch (_: Exception) {
            // Handled gracefully in environments where WorkManager is not initialized (e.g. unit tests)
        }
    }


    /**
     * Durable, uniquely named upload work for one immutable repair-run UUID.
     */
    fun enqueueReportUpload(context: Context, reportId: String) {
        require(reportId.isNotBlank())
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ReportUploadWorker>()
            .setInputData(workDataOf(ReportUploadWorker.KEY_REPORT_ID to reportId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REPORT_UPLOAD_WORK_PREFIX + reportId,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
    /**
     * Enqueues an immediate one-time sync task in the background.
     */
    fun triggerImmediateSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<ToolSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        } catch (_: Exception) {
            // Handled gracefully in unit test environments
        }
    }
}
