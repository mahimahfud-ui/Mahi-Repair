package com.example.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.workers.ReportOutboxWorker
import com.example.workers.ToolSyncWorker
import java.util.concurrent.TimeUnit

object SyncManager {
    private const val PERIODIC_SYNC_WORK_NAME = "mahi_tool_periodic_sync"
    private const val IMMEDIATE_SYNC_WORK_NAME = "mahi_tool_immediate_sync"
    private const val PERIODIC_REPORT_WORK_NAME = "mahi_report_outbox_periodic"
    private const val IMMEDIATE_REPORT_WORK_NAME = "mahi_report_outbox_immediate"

    private fun connectedConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** A periodic report worker recovers queued reports after app process death. */
    fun schedulePeriodicSync(context: Context) {
        try {
            val periodicRequest = PeriodicWorkRequestBuilder<ToolSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(connectedConstraint())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodicRequest
            )

            val reportRequest = PeriodicWorkRequestBuilder<ReportOutboxWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connectedConstraint())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_REPORT_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, reportRequest
            )
        } catch (_: Exception) {
            // Handled gracefully in environments where WorkManager is not initialized (e.g. tests).
        }
    }

    /** Enqueue prompt delivery; periodic work remains the crash-recovery fallback. */
    fun triggerReportUpload(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<ReportOutboxWorker>()
                .setConstraints(connectedConstraint())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_REPORT_WORK_NAME, ExistingWorkPolicy.KEEP, request
            )
        } catch (_: Exception) {
            // The periodic worker will recover persisted reports if immediate work cannot queue.
        }
    }

    fun triggerImmediateSync(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<ToolSyncWorker>()
                .setConstraints(connectedConstraint())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request
            )
        } catch (_: Exception) {
            // Handled gracefully in unit test environments.
        }
    }
}
