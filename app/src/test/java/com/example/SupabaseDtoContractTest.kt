package com.example

import com.example.data.remote.InviteMemberRequest
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
    fun `member invitation DTO keeps the display name and omits role input`() {
        val request = InviteMemberRequest(
            email = "member@example.test",
            displayName = "Repair Member"
        )
        val encoded = moshi.adapter(InviteMemberRequest::class.java).toJson(request)
        assertTrue(encoded.contains("\"email\":\"member@example.test\""))
        assertTrue(encoded.contains("\"display_name\":\"Repair Member\""))
        assertTrue(!encoded.contains("\"role\""))
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
    fun `device profile registration serializes Supabase hardware telemetry fields`() {
        val request = RegisterDeviceRequest(
            deviceId = "a0000000-0000-0000-0000-000000000001",
            deviceLabel = "Pixel 8",
            appVersion = "1.0.0",
            androidVersion = "14",
            androidApi = 34,
            hardwareProfile = mapOf(
                "manufacturer" to "Google",
                "model" to "Pixel 8",
                "cpuCoreCount" to 8
            ),
            telemetry = mapOf(
                "totalRamBytes" to 8589934592L,
                "batteryTemperature" to 31.5
            ),
            fieldAvailability = mapOf("batteryHealth" to "OBSERVED")
        )

        val encoded = moshi.adapter(RegisterDeviceRequest::class.java).toJson(request)
        assertTrue(encoded.contains("\"hardware_profile\""))
        assertTrue(encoded.contains("\"model\":\"Pixel 8\""))
        assertTrue(encoded.contains("\"cpuCoreCount\":8"))
        assertTrue(encoded.contains("\"totalRamBytes\":8589934592"))
        assertTrue(encoded.contains("\"batteryTemperature\":31.5"))
        assertTrue(encoded.contains("\"field_availability\""))
        assertTrue(encoded.contains("\"batteryHealth\":\"OBSERVED\""))
        assertTrue(!encoded.contains("app_installation_id"))
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
