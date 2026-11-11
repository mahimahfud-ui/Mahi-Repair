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
    fun registerDeviceRequestUsesCurrentEdgeFunctionContract() {
        val request = RegisterDeviceRequest(
            deviceId = "a0000000-0000-0000-0000-000000000001",
            deviceLabel = "Pixel 8",
            appVersion = "1.0.0",
            androidVersion = "14",
            androidApi = 34,
        )
        val json = moshi.adapter(RegisterDeviceRequest::class.java).toJson(request)
        assertTrue(json.contains("\"device_id\":\"a0000000-0000-0000-0000-000000000001\""))
        assertTrue(json.contains("\"android_api\":34"))
        assertTrue(json.contains("\"create_user\"") .not())
    }

    @Test
    fun registerDeviceResponseParsesServerIssuedDeviceId() {
        val json = """
            {
              "data": {
                "device_id": "a0000000-0000-0000-0000-000000000001",
                "registered": true
              }
            }
        """.trimIndent()
        val parsed = moshi.adapter(RegisterDeviceResponse::class.java).fromJson(json)
        assertNotNull(parsed?.data)
        assertEquals("a0000000-0000-0000-0000-000000000001", parsed?.data?.deviceId)
        assertTrue(parsed?.data?.registered == true)
    }

    @Test
    fun getToolsRequestAndResponseMatchCurrentCatalogContract() {
        val request = GetToolsRequest(deviceId = "d1234567-0000-0000-0000-000000000000")
        val requestJson = moshi.adapter(GetToolsRequest::class.java).toJson(request)
        assertTrue(requestJson.contains("\"device_id\":\"d1234567-0000-0000-0000-000000000000\""))

        val responseJson = """
            {
              "data": [
                {
                  "tool_id": "a0000000-0000-0000-0000-000000000002",
                  "version": 1,
                  "signed_payload": "{\"schemaVersion\":1,\"toolId\":\"wifi-check\"}",
                  "signature": "signature-base64",
                  "signature_algorithm": "RSA-SHA256",
                  "published_at": "2026-11-05T10:00:00Z"
                }
              ]
            }
        """.trimIndent()
        val response = moshi.adapter(GetToolsResponse::class.java).fromJson(responseJson)
        val tool = response?.data?.single()
        assertEquals("a0000000-0000-0000-0000-000000000002", tool?.toolId)
        assertEquals(1, tool?.version)
        assertEquals("RSA-SHA256", tool?.signatureAlgorithm)
        assertEquals("signature-base64", tool?.signature)
    }

    @Test
    fun remoteSignedToolDtoRoundTripsExpectedFields() {
        val dto = RemoteSignedToolDto(
            toolId = "a0000000-0000-0000-0000-000000000003",
            version = 3,
            signedPayload = "{\"schemaVersion\":1}",
            signature = "detached-signature",
            signatureAlgorithm = "RSA-SHA256",
            publishedAt = "2026-11-05T10:00:00Z",
        )
        val json = moshi.adapter(RemoteSignedToolDto::class.java).toJson(dto)
        assertTrue(json.contains("\"tool_id\""))
        assertTrue(json.contains("\"signed_payload\""))
        assertTrue(json.contains("\"signature_algorithm\":\"RSA-SHA256\""))
        val parsed = moshi.adapter(RemoteSignedToolDto::class.java).fromJson(json)
        assertEquals(dto.toolId, parsed?.toolId)
        assertEquals(dto.signature, parsed?.signature)
    }

    @Test
    fun signedRecipeResponseParsesDetachedSignature() {
        val json = """
            {
              "signed_payload": "{\"schemaVersion\":1,\"toolId\":\"wifi-check\"}",
              "signature": "MIIEvgIBADAN...",
              "signature_algorithm": "RSA-SHA256",
              "version": 4
            }
        """.trimIndent()
        val parsed = moshi.adapter(SignedRecipeResponse::class.java).fromJson(json)
        assertNotNull(parsed)
        assertEquals("MIIEvgIBADAN...", parsed?.signature)
        assertEquals("RSA-SHA256", parsed?.signatureAlgorithm)
        assertEquals(4, parsed?.version)
    }

    @Test
    fun submitRunRequestSerializesIdempotentRunFields() {
        val request = SubmitRunRequest(
            runId = "a0000000-0000-0000-0000-000000000004",
            deviceId = "a0000000-0000-0000-0000-000000000001",
            toolId = "a0000000-0000-0000-0000-000000000002",
            toolVersion = 2,
            startedAt = "2026-11-05T10:00:00Z",
            completedAt = "2026-11-05T10:00:25Z",
            result = "SUCCESS",
            stepResults = listOf(StepRunResultDto(stepId = "s1", status = "SUCCESS")),
            appVersion = "1.0.0",
        )
        val json = moshi.adapter(SubmitRunRequest::class.java).toJson(request)
        assertTrue(json.contains("\"run_id\""))
        assertTrue(json.contains("\"device_id\""))
        assertTrue(json.contains("\"tool_version\":2"))
        assertTrue(json.contains("\"started_at\""))
        assertTrue(json.contains("\"step_results\""))
        assertTrue(json.contains("\"step_id\":\"s1\""))
    }

    @Test
    fun submitRunResponseConfirmsTheSameRunId() {
        val json = """
            {
              "data": {
                "run_id": "a0000000-0000-0000-0000-000000000004",
                "accepted": true
              }
            }
        """.trimIndent()
        val parsed = moshi.adapter(SubmitRunResponse::class.java).fromJson(json)
        assertEquals("a0000000-0000-0000-0000-000000000004", parsed?.data?.runId)
        assertTrue(parsed?.data?.accepted == true)
    }
}
