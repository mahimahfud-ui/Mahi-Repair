package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.SupabaseClient
import com.example.data.repository.AuthRepository
import com.example.data.repository.ToolRepository

class ToolSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as com.example.MahiRepairApplication
            val repository = app.toolRepository

            val syncResult = repository.syncTools()
            if (syncResult.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
