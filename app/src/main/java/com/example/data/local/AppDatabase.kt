package com.example.data.local

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
import android.content.Context
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
    @ColumnInfo(name = "last_updated") val lastUpdated: Long,
    @ColumnInfo(name = "recipe_slug", defaultValue = "''") val recipeSlug: String = ""
)

@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "device_id") val deviceId: String? = null,
    @ColumnInfo(name = "app_version", defaultValue = "'1.0.0'") val appVersion: String = "1.0.0"
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

    @androidx.room.Transaction
    suspend fun replaceAll(tools: List<ToolEntity>) {
        clearAll()
        if (tools.isNotEmpty()) insertTools(tools)
    }
}

@Dao
interface ReportOutboxDao {
    // A retry with the same run UUID must never overwrite the first accepted request body.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(report: PendingReportEntity)

    @Query("SELECT * FROM pending_reports WHERE id = :reportId LIMIT 1")
    suspend fun get(reportId: String): PendingReportEntity?

    @Query("SELECT id FROM pending_reports ORDER BY created_at ASC")
    suspend fun getPendingIds(): List<String>

    @Query("DELETE FROM pending_reports WHERE id = :reportId")
    suspend fun delete(reportId: String)

    @Query("SELECT COUNT(*) FROM pending_reports")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_reports")
    fun observeCount(): Flow<Int>
}

@Dao
interface RepairHistoryDao {
    @Query("SELECT * FROM repair_history WHERE user_email = :userEmail ORDER BY timestamp DESC")
    fun getHistoryForUserFlow(userEmail: String): Flow<List<RepairHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RepairHistoryEntity)
}

@Database(
    entities = [ToolEntity::class, RepairHistoryEntity::class, PendingReportEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun repairHistoryDao(): RepairHistoryDao
    abstract fun reportOutboxDao(): ReportOutboxDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tools ADD COLUMN recipe_slug TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_reports (" +
                        "id TEXT NOT NULL, payload_json TEXT NOT NULL, created_at INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_reports ADD COLUMN device_id TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_reports ADD COLUMN app_version TEXT NOT NULL DEFAULT '1.0.0'")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
