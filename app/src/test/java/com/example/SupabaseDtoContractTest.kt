package com.example

import com.example.data.remote.dto.GetMyRepairToolsRequest
import com.example.data.remote.dto.GetMyRepairToolsResponse
import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.data.remote.dto.RegisterDeviceResponse
import com.example.data.remote.dto.RemoteToolDto
import com.example.data.remote.dto.RepairRunReportRequest
import com.example.data.remote.dto.SignedRecipeResponse
import com.example.data.remote.dto.StepResultDto
import com.example.data.remote.dto.SubmitRepairRunResponse
import com.example.data.remote.dto.SyncedToolDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseDtoContractTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun `test RegisterDeviceRequest and RegisterDeviceResponse`() {
        val req = RegisterDeviceRequest(
            deviceId = "a0000000-0000-0000-0000-000000000001",
            deviceLabel = "Pixel 8",
            appVersion = "1.0.0",
            androidVersion = "14",
            androidApi = 34
        )
        val jsonReq = moshi.adapter(RegisterDeviceRequest::class.java).toJson(req)
        assertTrue(jsonReq.contains("\"device_id\":\"a0000000-0000-0000-0000-000000000001\""))
        assertTrue(jsonReq.contains("\"android_api\":34"))

        val resJson = """
        {
          "data": {
            "device_id": "a0000000-0000-0000-0000-000000000001",
            "registered": true
          }
        }
        """.trimIndent()
        val resp = moshi.adapter(RegisterDeviceResponse::class.java).fromJson(resJson)
        assertNotNull(resp?.data)
        assertEquals("a0000000-0000-0000-0000-000000000001", resp?.data?.deviceId)
        assertEquals(true, resp?.data?.registered)
    }

    @Test
    fun `test GetMyRepairToolsRequest and GetMyRepairToolsResponse`() {
        val req = GetMyRepairToolsRequest(deviceId = "d1234567-0000-0000-0000-000000000000")
        val jsonReq = moshi.adapter(GetMyRepairToolsRequest::class.java).toJson(req)
        assertTrue(jsonReq.contains("\"device_id\":\"d1234567-0000-0000-0000-000000000000\""))

        val resJson = """
        {
          "data": [
            {
              "tool_id": "tool-yt-repair",
              "version": 1,
              "signed_payload": "{\"schemaVersion\":1,\"toolId\":\"tool-yt-repair\"}",
              "signature": "MIIEvg...",
              "signature_algorithm": "RSA-SHA256",
              "published_at": "2026-11-05T10:00:00Z"
            }
          ]
        }
        """.trimIndent()
        val resp = moshi.adapter(GetMyRepairToolsResponse::class.java).fromJson(resJson)
        assertNotNull(resp?.data)
        assertEquals(1, resp?.data?.size)
        val tool = resp?.data?.first()
        assertEquals("tool-yt-repair", tool?.toolId)
        assertEquals(1, tool?.version)
        assertEquals("RSA-SHA256", tool?.signatureAlgorithm)
    }

    @Test
    fun `test SignedRecipeResponse parses with signature field`() {
        val json = """
        {
          "tool_id": "tool_yt_01",
          "version": 1,
          "signed_payload": "{\"recipe_id\":\"rec_1\"}",
          "signature": "MIIEvgIBADAN...",
          "signature_algorithm": "RSA-SHA256"
        }
        """.trimIndent()

        val adapter = moshi.adapter(SignedRecipeResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("tool_yt_01", response?.toolId)
        assertEquals(1, response?.version)
        assertEquals("MIIEvgIBADAN...", response?.effectiveSignature)
        assertEquals("RSA-SHA256", response?.signatureAlgorithm)
    }

    @Test
    fun `test RepairRunReportRequest and SubmitRepairRunResponse`() {
        val report = RepairRunReportRequest(
            deviceId = "a0000000-0000-0000-0000-000000000001",
            toolId = "tool_yt_01",
            toolVersion = 1,
            startedAt = "2026-11-05T10:00:00Z",
            completedAt = "2026-11-05T10:00:25Z",
            result = "SUCCESS",
            stepResults = listOf(
                StepResultDto(
                    stepId = "s1",
                    status = "SUCCESS"
                )
            ),
            appVersion = "1.0.0"
        )

        val adapter = moshi.adapter(RepairRunReportRequest::class.java)
        val json = adapter.toJson(report)

        assertTrue(json.contains("\"device_id\":\"a0000000-0000-0000-0000-000000000001\""))
        assertTrue(json.contains("\"tool_version\":1"))
        assertTrue(json.contains("\"result\":\"SUCCESS\""))
        assertTrue(json.contains("\"started_at\":\"2026-11-05T10:00:00Z\""))

        val resJson = """
        {
          "data": {
            "run_id": "r0000000-0000-0000-0000-000000000001",
            "accepted": true
          }
        }
        """.trimIndent()
        val res = moshi.adapter(SubmitRepairRunResponse::class.java).fromJson(resJson)
        assertNotNull(res?.data)
        assertEquals("r0000000-0000-0000-0000-000000000001", res?.data?.runId)
        assertEquals(true, res?.data?.accepted)
    }
}
