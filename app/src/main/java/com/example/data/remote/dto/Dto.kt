package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OtpRequest(
    val email: String,
    @Json(name = "create_user") val createUser: Boolean = false
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val type: String = "email",
    val email: String,
    val token: String
)

@JsonClass(generateAdapter = true)
data class AuthSessionResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "expires_in") val expiresIn: Long?,
    @Json(name = "refresh_token") val refreshToken: String?,
    val user: AuthUser?
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String?,
    @Json(name = "user_metadata") val userMetadata: Map<String, String>? = null,
    @Json(name = "app_metadata") val appMetadata: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class RemoteToolDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val version: String,
    @Json(name = "problem_description") val problemDescription: String,
    @Json(name = "min_app_version") val minAppVersion: Int = 1,
    @Json(name = "min_android_sdk") val minAndroidSdk: Int = 26,
    @Json(name = "is_new") val isNew: Boolean = false,
    val enabled: Boolean = true,
    @Json(name = "target_package") val targetPackage: String? = null,
    @Json(name = "raw_signed_payload") val rawSignedPayload: String? = null,
    @Json(name = "signature_base64") val signatureBase64: String? = null,
    @Json(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class SignedRecipeResponse(
    @Json(name = "tool_id") val toolId: String,
    @Json(name = "version") val version: String,
    @Json(name = "signed_payload") val signedPayload: String,
    @Json(name = "signature_base64") val signatureBase64: String,
    @Json(name = "public_key_fingerprint") val publicKeyFingerprint: String? = null
)

@JsonClass(generateAdapter = true)
data class RepairRunReportRequest(
    @Json(name = "run_id") val runId: String,
    @Json(name = "tool_id") val toolId: String,
    @Json(name = "tool_name") val toolName: String,
    @Json(name = "tool_version") val toolVersion: String,
    @Json(name = "user_email") val userEmail: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "android_sdk") val androidSdk: Int,
    @Json(name = "start_time") val startTime: Long,
    @Json(name = "end_time") val endTime: Long,
    val result: String,
    @Json(name = "final_diagnosis") val finalDiagnosis: String,
    @Json(name = "steps") val steps: List<StepReportDto>
)

@JsonClass(generateAdapter = true)
data class StepReportDto(
    @Json(name = "step_id") val stepId: String,
    val action: String,
    val title: String,
    val status: String,
    val message: String,
    @Json(name = "duration_ms") val durationMs: Long
)
