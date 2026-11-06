package com.example

import android.content.Context
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
