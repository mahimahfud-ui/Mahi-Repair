package com.example

import com.example.data.remote.dto.GetToolsRequest
import com.example.data.remote.dto.GetToolsResponse
import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.data.remote.dto.RegisterDeviceResponse
import com.example.data.remote.dto.RemoteSignedToolDto
import com.example.data.remote.dto.SignedRecipeResponse
import com.example.data.remote.dto.StepRunResultDto
import com.example.data.remote.dto.SubmitRunRequest
import com.example.data.remote.dto.SubmitRunResponse
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
    fun `register device DTOs match the Edge Function contract`() {
        val request = RegisterDeviceRequest(
            deviceId = "a0000000-0000-0000-0000-000000000001",
            deviceLabel = "Pixel 8",
            appVersion = "1.0.0",
            androidVersion = "14",
            androidApi = 34
        )
        val encoded = moshi.adapter(RegisterDeviceRequest::class.java).toJson(request)
        assertTrue(encoded.contains("\"device_id\":\"a0000000-0000-0000-0000-000000000001\""))
        assertTrue(encoded.contains("\"android_api\":34"))

        val responseJson = """
            {
              "data": {
                "device_id": "a0000000-0000-0000-0000-000000000001",
                "registered": true
              }
            }
        """.trimIndent()
        val response = moshi.adapter(RegisterDeviceResponse::class.java).fromJson(responseJson)
        assertNotNull(response?.data)
        assertEquals("a0000000-0000-0000-0000-000000000001", response?.data?.deviceId)
        assertTrue(response?.data?.registered == true)
    }

    @Test
    fun `repair catalogue DTOs match the Edge Function contract`() {
        val request = GetToolsRequest(deviceId = "d1234567-0000-0000-0000-000000000000")
        val encoded = moshi.adapter(GetToolsRequest::class.java).toJson(request)
        assertTrue(encoded.contains("\"device_id\":\"d1234567-0000-0000-0000-000000000000\""))

        val responseJson = """
            {
              "data": [
                {
                  "tool_id": "a0000000-0000-0000-0000-000000000003",
                  "version": 1,
                  "signed_payload": "{\"schemaVersion\":1,\"toolId\":\"tool-network-check\"}",
                  "signature": "base64-signature",
                  "signature_algorithm": "RSA-SHA256",
                  "published_at": "2026-11-05T10:00:00Z"
                }
              ]
            }
        """.trimIndent()
        val response = moshi.adapter(GetToolsResponse::class.java).fromJson(responseJson)
        assertEquals(1, response?.data?.size)
        assertEquals("a0000000-0000-0000-0000-000000000003", response?.data?.first()?.toolId)
        assertEquals("RSA-SHA256", response?.data?.first()?.signatureAlgorithm)
        assertEquals("2026-11-05T10:00:00Z", response?.data?.first()?.publishedAt)
    }

    @Test
    fun `signed recipe response DTO matches the Edge Function contract`() {
        val responseJson = """
            {
              "signed_payload": "{\"schemaVersion\":1,\"toolId\":\"tool-network-check\"}",
              "signature": "base64-signature",
              "signature_algorithm": "RSA-SHA256",
              "version": 1
            }
        """.trimIndent()
        val response = moshi.adapter(SignedRecipeResponse::class.java).fromJson(responseJson)
        assertNotNull(response)
        assertTrue(response?.signedPayload?.contains("tool-network-check") == true)
        assertEquals("base64-signature", response?.signature)
        assertEquals("RSA-SHA256", response?.signatureAlgorithm)
        assertEquals(1, response?.version)
    }

    @Test
    fun `repair run submission DTOs match the idempotent report contract`() {
        val report = SubmitRunRequest(
            runId = "a0000000-0000-0000-0000-000000000002",
            deviceId = "a0000000-0000-0000-0000-000000000001",
            toolId = "tool-network-check",
            toolVersion = 1,
            startedAt = "2026-11-05T10:00:00Z",
            completedAt = "2026-11-05T10:00:25Z",
            result = "SUCCESS",
            stepResults = listOf(
                StepRunResultDto(
                    stepId = "check-internet",
                    status = "SUCCESS"
                )
            ),
            appVersion = "1.0.0"
        )
        val encoded = moshi.adapter(SubmitRunRequest::class.java).toJson(report)
        assertTrue(encoded.contains("\"run_id\":\"a0000000-0000-0000-0000-000000000002\""))
        assertTrue(encoded.contains("\"device_id\":\"a0000000-0000-0000-0000-000000000001\""))
        assertTrue(encoded.contains("\"tool_version\":1"))
        assertTrue(encoded.contains("\"result\":\"SUCCESS\""))
        assertTrue(encoded.contains("\"step_id\":\"check-internet\""))

        val responseJson = """
            {
              "data": {
                "run_id": "a0000000-0000-0000-0000-000000000002",
                "accepted": true
              }
            }
        """.trimIndent()
        val response = moshi.adapter(SubmitRunResponse::class.java).fromJson(responseJson)
        assertNotNull(response?.data)
        assertEquals("a0000000-0000-0000-0000-000000000002", response?.data?.runId)
        assertTrue(response?.data?.accepted == true)
    }
}
