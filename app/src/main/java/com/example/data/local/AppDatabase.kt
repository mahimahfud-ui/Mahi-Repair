package com.example.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val version: String,
    @ColumnInfo(name = "problem_description") val problemDescription: String,
    @ColumnInfo(name = "min_app_version") val minAppVersion: Int,
    @ColumnInfo(name = "min_android_sdk") val minAndroidSdk: Int,
    @ColumnInfo(name = "is_new") val isNew: Boolean,
    val enabled: Boolean,
    @ColumnInfo(name = "target_package") val targetPackage: String?,
    @ColumnInfo(name = "raw_signed_payload") val rawSignedPayload: String,
    @ColumnInfo(name = "signature_base64") val signatureBase64: String,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Entity(tableName = "repair_history")
data class RepairHistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tool_id") val toolId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "tool_version") val toolVersion: String,
    val timestamp: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val result: String,
    val diagnosis: String,
    @ColumnInfo(name = "user_email") val userEmail: String
)

/** Immutable local submission body retained until the server acknowledges the run ID. */
@Entity(tableName = "repair_report_outbox")
data class RepairReportOutboxEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_email") val accountEmail: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null
)

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools WHERE enabled = 1 ORDER BY is_new DESC, name ASC")
    fun getEnabledToolsFlow(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools WHERE id = :toolId LIMIT 1")
    suspend fun getToolById(toolId: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<ToolEntity>)

    @Query("DELETE FROM tools")
    suspend fun clearAll()

    /** Replace the complete verified catalog in one SQLite transaction. */
    @Transaction
    suspend fun replaceAll(tools: List<ToolEntity>) {
        clearAll()
        if (tools.isNotEmpty()) insertTools(tools)
    }
}

@Dao
interface RepairHistoryDao {
    @Query("SELECT * FROM repair_history WHERE user_email = :userEmail ORDER BY timestamp DESC")
    fun getHistoryForUserFlow(userEmail: String): Flow<List<RepairHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RepairHistoryEntity)
}

@Dao
interface RepairReportOutboxDao {
    @Query("SELECT * FROM repair_report_outbox ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<RepairReportOutboxEntity>

    @Query("SELECT * FROM repair_report_outbox WHERE id = :runId LIMIT 1")
    suspend fun getById(runId: String): RepairReportOutboxEntity?

    /** A repeated enqueue must never replace the original request body. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(report: RepairReportOutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: RepairHistoryEntity)

    @Transaction
    suspend fun persistRepairRunAndQueue(history: RepairHistoryEntity, outbox: RepairReportOutboxEntity) {
        insertHistory(history)
        enqueue(outbox)
    }

    @Query("UPDATE repair_report_outbox SET attempt_count = attempt_count + 1, last_error = :error WHERE id = :runId")
    suspend fun recordAttempt(runId: String, error: String?)

    @Query("DELETE FROM repair_report_outbox WHERE id = :runId")
    suspend fun remove(runId: String)
}

@Database(
    entities = [ToolEntity::class, RepairHistoryEntity::class, RepairReportOutboxEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun repairHistoryDao(): RepairHistoryDao
    abstract fun repairReportOutboxDao(): RepairReportOutboxDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS repair_report_outbox (
                        id TEXT NOT NULL,
                        account_email TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mahi_repair.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
