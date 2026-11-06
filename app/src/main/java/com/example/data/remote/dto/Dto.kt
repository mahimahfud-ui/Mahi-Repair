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
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
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
data class RegisterDeviceRequest(
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "device_label") val deviceLabel: String? = null,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "android_version") val androidVersion: String,
    @Json(name = "android_api") val androidApi: Int
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceData(
    @Json(name = "device_id") val deviceId: String,
    val registered: Boolean
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceResponse(
    val data: RegisterDeviceData
)

@JsonClass(generateAdapter = true)
data class GetToolsRequest(
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class RemoteSignedToolDto(
    @Json(name = "tool_id") val toolId: String,
    val version: Int,
    @Json(name = "signed_payload") val signedPayload: String,
    val signature: String,
    @Json(name = "signature_algorithm") val signatureAlgorithm: String,
    @Json(name = "published_at") val publishedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GetToolsResponse(
    val data: List<RemoteSignedToolDto>
)

@JsonClass(generateAdapter = true)
data class GetSignedToolRequest(
    @Json(name = "tool_id") val toolId: String,
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class SignedRecipeResponse(
    @Json(name = "signed_payload") val signedPayload: String,
    val signature: String,
    @Json(name = "signature_algorithm") val signatureAlgorithm: String,
    val version: Int
)

@JsonClass(generateAdapter = true)
data class StepRunResultDto(
    @Json(name = "step_id") val stepId: String,
    val status: String,
    @Json(name = "error_code") val errorCode: String? = null
)

@JsonClass(generateAdapter = true)
data class SubmitRunRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "tool_id") val toolId: String,
    @Json(name = "tool_version") val toolVersion: Int,
    @Json(name = "started_at") val startedAt: String,
    @Json(name = "completed_at") val completedAt: String? = null,
    val result: String,
    @Json(name = "step_results") val stepResults: List<StepRunResultDto>,
    @Json(name = "error_code") val errorCode: String? = null,
    @Json(name = "app_version") val appVersion: String
)

@JsonClass(generateAdapter = true)
data class SubmitRunData(
    @Json(name = "run_id") val runId: String,
    val accepted: Boolean
)

@JsonClass(generateAdapter = true)
data class SubmitRunResponse(
    val data: SubmitRunData
)
