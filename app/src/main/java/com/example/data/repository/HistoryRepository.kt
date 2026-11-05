package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.RepairHistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val database: AppDatabase
) {
    private val historyDao = database.repairHistoryDao()

    fun getHistoryFlow(userEmail: String): Flow<List<RepairHistoryEntity>> {
        return historyDao.getHistoryForUserFlow(userEmail)
    }
}
