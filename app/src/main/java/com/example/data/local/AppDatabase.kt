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
}

@Dao
interface RepairHistoryDao {
    @Query("SELECT * FROM repair_history WHERE user_email = :userEmail ORDER BY timestamp DESC")
    fun getHistoryForUserFlow(userEmail: String): Flow<List<RepairHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RepairHistoryEntity)
}

@Database(entities = [ToolEntity::class, RepairHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun repairHistoryDao(): RepairHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mahi_repair.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
