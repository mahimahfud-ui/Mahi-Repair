package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PendingReportEntity
import com.example.data.local.ToolEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseReliabilityTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun catalogReplacementIsAtomicAndAcceptsAValidEmptyCatalog() = runBlocking {
        val old = tool("old-tool")
        database.toolDao().insertTools(listOf(old))

        database.toolDao().replaceAll(listOf(tool("new-tool")))
        assertNull(database.toolDao().getToolById("old-tool"))
        assertEquals("new-tool", database.toolDao().getToolById("new-tool")?.id)

        database.toolDao().replaceAll(emptyList())
        val cursor = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tools")
        val rowCount = cursor.use { it.moveToFirst(); it.getInt(0) }
        assertEquals(0, rowCount)
    }

    @Test
    fun failedCatalogInsertRollsBackTheDeleteAndPreservesPreviousCatalog() = runBlocking {
        val old = tool("old-tool")
        database.toolDao().insertTools(listOf(old))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_bad_tool BEFORE INSERT ON tools " +
                "WHEN NEW.id = 'bad-tool' BEGIN SELECT RAISE(ABORT, 'forced test failure'); END"
        )

        val failure = runCatching {
            database.toolDao().replaceAll(listOf(tool("bad-tool")))
        }
        assertTrue(failure.isFailure)
        assertEquals(old, database.toolDao().getToolById("old-tool"))
        assertNull(database.toolDao().getToolById("bad-tool"))
    }

    @Test
    fun reportOutboxPersistsByRunUuidUntilExplicitAcknowledgementDelete() = runBlocking {
        val report = PendingReportEntity(
            id = "3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6",
            payloadJson = """{"runId":"3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6"}""",
            createdAt = 100L,
            deviceId = "b65e6c13-56f2-4a73-b9f9-7cb8af6a708c",
            appVersion = "1.0.0"
        )
        database.reportOutboxDao().enqueue(report)

        assertEquals(report, database.reportOutboxDao().get(report.id))
        database.reportOutboxDao().enqueue(report.copy(payloadJson = """{"runId":"changed"}""", appVersion = "9.9.9"))
        assertEquals("The first immutable request must be retained.", report, database.reportOutboxDao().get(report.id))
        assertEquals(listOf(report.id), database.reportOutboxDao().getPendingIds())
        assertEquals(1, database.reportOutboxDao().count())

        database.reportOutboxDao().delete(report.id)
        assertNull(database.reportOutboxDao().get(report.id))
        assertEquals(0, database.reportOutboxDao().count())
    }

    @Test
    fun outboxRequestBackfillIsWriteOnceAndPreservesFirstAccountIdentity() = runBlocking {
        val report = PendingReportEntity(
            id = "3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6",
            payloadJson = """{"runId":"3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6"}""",
            createdAt = 100L,
            deviceId = "b65e6c13-56f2-4a73-b9f9-7cb8af6a708c",
            appVersion = "1.0.0"
        )
        database.reportOutboxDao().enqueue(report)

        database.reportOutboxDao().backfillRequestIfMissing(
            report.id,
            """{"run_id":"${report.id}"}""",
            "member@example.invalid"
        )
        database.reportOutboxDao().backfillRequestIfMissing(
            report.id,
            """{"run_id":"changed"}""",
            "attacker@example.invalid"
        )

        val saved = database.reportOutboxDao().get(report.id)
        assertEquals("""{"run_id":"${report.id}"}""", saved?.requestJson)
        assertEquals("member@example.invalid", saved?.accountEmail)
    }

    @Test
    fun versionFourOutboxMigrationPreservesQueuedReportsAndAddsNullableReplayColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v4-test.db"
        context.deleteDatabase(databaseName)
        val legacyFile = context.getDatabasePath(databaseName)
        legacyFile.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
        try {
            legacy.execSQL(
                """
                CREATE TABLE tools (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT NOT NULL,
                    version TEXT NOT NULL,
                    problem_description TEXT NOT NULL,
                    min_app_version INTEGER NOT NULL,
                    min_android_sdk INTEGER NOT NULL,
                    is_new INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    target_package TEXT,
                    raw_signed_payload TEXT NOT NULL,
                    signature_base64 TEXT NOT NULL,
                    last_updated INTEGER NOT NULL,
                    recipe_slug TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE repair_history (
                    id TEXT NOT NULL PRIMARY KEY,
                    tool_id TEXT NOT NULL,
                    tool_name TEXT NOT NULL,
                    tool_version TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    result TEXT NOT NULL,
                    diagnosis TEXT NOT NULL,
                    user_email TEXT NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE pending_reports (
                    id TEXT NOT NULL PRIMARY KEY,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    device_id TEXT,
                    app_version TEXT NOT NULL DEFAULT '1.0.0'
                )
                """.trimIndent()
            )
            legacy.execSQL(
                "INSERT INTO pending_reports(id, payload_json, created_at, device_id, app_version) VALUES (?, ?, ?, ?, ?)",
                arrayOf(
                    "3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6",
                    """{"runId":"3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6"}""",
                    100L,
                    "b65e6c13-56f2-4a73-b9f9-7cb8af6a708c",
                    "1.2.3"
                )
            )
            legacy.version = 4
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()

        try {
            val saved = runBlocking {
                migrated.reportOutboxDao().get("3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6")
            }
            assertEquals("1.2.3", saved?.appVersion)
            assertEquals("b65e6c13-56f2-4a73-b9f9-7cb8af6a708c", saved?.deviceId)
            assertEquals(
                """{"runId":"3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6"}""",
                saved?.payloadJson
            )
            assertNull(saved?.requestJson)
            assertNull(saved?.accountEmail)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun tool(id: String) = ToolEntity(
        id = id,
        name = id,
        description = "",
        category = "General",
        version = "1",
        problemDescription = "",
        minAppVersion = 1,
        minAndroidSdk = 23,
        isNew = true,
        enabled = true,
        targetPackage = null,
        rawSignedPayload = "",
        signatureBase64 = "",
        lastUpdated = 1L,
        recipeSlug = "slug-$id"
    )
}
