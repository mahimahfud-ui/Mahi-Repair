package com.example

import com.example.data.remote.dto.SubmitRunRequest
import com.example.data.remote.dto.StepRunResultDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubmitRunRequestContractTest {
    @Test
    fun serializesRunIdUsingTheExactAgentTwoContractField() {
        val runId = "3e1dc0d4-78b3-4e7f-9ac7-0c4a976d28d6"
        val request = SubmitRunRequest(
            runId = runId,
            deviceId = "d3925f8c-2480-4f4c-95e6-4a932e7e7c4f",
            toolId = "8bff9cb9-0d1b-4c6f-b8c0-9adf0ee5d6cc",
            toolVersion = 3,
            startedAt = "2026-11-06T09:00:00Z",
            completedAt = "2026-11-06T09:01:00Z",
            result = "SUCCESS",
            stepResults = listOf(StepRunResultDto("step-1", "SUCCESS")),
            appVersion = "1.0.0"
        )

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val json = JSONObject(moshi.adapter(SubmitRunRequest::class.java).toJson(request))
        assertEquals(runId, json.getString("run_id"))
        assertTrue(json.has("device_id"))
        assertTrue(!json.has("repair_run_id"))
    }
}
