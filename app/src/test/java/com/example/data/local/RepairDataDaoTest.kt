package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepairDataDaoTest {
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
    fun catalogReplacementReplacesTheOldSnapshotAndSupportsEmptyCatalogs() = runBlocking {
        val dao = database.toolDao()
        dao.insertTools(listOf(tool("old-tool")))
        dao.replaceAll(listOf(tool("new-tool")))

        assertNull(dao.getToolById("old-tool"))
        assertEquals("new-tool", dao.getToolById("new-tool")?.id)

        dao.replaceAll(emptyList())
        assertNull(dao.getToolById("new-tool"))
    }

    @Test
    fun reportOutboxNeverOverwritesTheOriginalPayloadForARepeatedRunId() = runBlocking {
        val dao = database.repairReportOutboxDao()
        val original = outbox("stable-run-id", "{\"run_id\":\"stable-run-id\"}")
        val conflictingRetry = outbox(
            "stable-run-id",
            "{\"run_id\":\"stable-run-id\",\"result\":\"FAILED\"}"
        )

        dao.enqueue(original)
        dao.enqueue(conflictingRetry)

        val queued = dao.getById("stable-run-id")
        assertNotNull(queued)
        assertEquals(original.payloadJson, queued?.payloadJson)
        assertEquals(0, queued?.attemptCount)

        dao.recordAttempt("stable-run-id", "temporary network failure")
        assertEquals(1, dao.getById("stable-run-id")?.attemptCount)
        dao.remove("stable-run-id")
        assertNull(dao.getById("stable-run-id"))
    }

    private fun tool(id: String) = ToolEntity(
        id = id,
        name = id,
        description = "test",
        category = "General",
        version = "1",
        problemDescription = "test",
        minAppVersion = 1,
        minAndroidSdk = 23,
        isNew = false,
        enabled = true,
        targetPackage = null,
        rawSignedPayload = "",
        signatureBase64 = "",
        lastUpdated = 1L
    )

    private fun outbox(id: String, payload: String) = RepairReportOutboxEntity(
        id = id,
        accountEmail = "member@example.com",
        payloadJson = payload,
        createdAt = 1L
    )
}
