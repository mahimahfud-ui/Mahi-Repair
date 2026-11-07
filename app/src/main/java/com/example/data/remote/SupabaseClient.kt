package com.example.data.remote

import com.example.BuildConfig
import com.example.data.remote.dto.AuthSessionResponse
import com.example.data.remote.dto.GetSignedToolRequest
import com.example.data.remote.dto.GetToolsRequest
import com.example.data.remote.dto.GetToolsResponse
import com.example.data.remote.dto.OtpRequest
import com.example.data.remote.dto.RefreshTokenRequest
import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.data.remote.dto.RegisterDeviceResponse
import com.example.data.remote.dto.RemoteSignedToolDto
import com.example.data.remote.dto.SignedRecipeResponse
import com.example.data.remote.dto.SubmitRunRequest
import com.example.data.remote.dto.SubmitRunResponse
import com.example.data.remote.dto.VerifyOtpRequest
import com.example.data.remote.dto.VerifyInvitationTokenRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class InvalidAuthSessionException(message: String) : IOException(message)

class SupabaseClient(
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun isConfigured(): Boolean {
        return baseUrl.startsWith("https://") &&
                anonKey.isNotBlank() &&
                !baseUrl.contains("placeholder.supabase.co") &&
                !anonKey.contains("placeholder-anon-key")
    }

    /**
     * Request Email OTP from Supabase Auth.
     */
    suspend fun requestEmailOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured in .env or settings."))
        }

        val url = "$baseUrl/auth/v1/otp"
        val adapter = moshi.adapter(OtpRequest::class.java)
        val bodyJson = adapter.toJson(OtpRequest(email = email, createUser = false))

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errBody = it.body?.string() ?: ""
                    Result.failure(IOException("Failed to send OTP (HTTP ${it.code}): $errBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify 6-digit Email OTP from Supabase Auth.
     */
    suspend fun verifyEmailOtp(email: String, token: String): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/auth/v1/verify"
        val adapter = moshi.adapter(VerifyOtpRequest::class.java)
        val bodyJson = adapter.toJson(VerifyOtpRequest(type = "email", email = email, token = token))

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string() ?: ""
                if (it.isSuccessful) {
                    val session = moshi.adapter(AuthSessionResponse::class.java).fromJson(resBody)
                        ?: return@withContext Result.failure(IOException("Empty session response"))
                    Result.success(session)
                } else {
                    Result.failure(IOException("OTP verification failed (HTTP ${it.code}): $resBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consume a Supabase invitation token hash. AuthRepository persists the returned
     * credentials only after Supabase Auth reports successful verification.
     */
    suspend fun verifyInvitationToken(tokenHash: String): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }
        if (!Regex("^[A-Za-z0-9_-]{16,512}$").matches(tokenHash)) {
            return@withContext Result.failure(IllegalArgumentException("Invitation link is invalid."))
        }

        val bodyJson = moshi.adapter(VerifyInvitationTokenRequest::class.java)
            .toJson(VerifyInvitationTokenRequest(tokenHash = tokenHash))
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/verify")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val body = it.body?.string().orEmpty()
                    val session = moshi.adapter(AuthSessionResponse::class.java).fromJson(body)
                        ?: return@withContext Result.failure(IOException("Empty invitation verification response."))
                    Result.success(session)
                } else if (it.code == 400 || it.code == 401 || it.code == 404) {
                    Result.failure(IOException("Invitation link is invalid, expired, or already used."))
                } else {
                    Result.failure(IOException("Invitation verification failed (HTTP ${it.code})."))
                }
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Refresh an expired or near-expiry Supabase session.
     */
    suspend fun refreshSession(refreshToken: String): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Repair service is not configured."))
        }
        if (refreshToken.isBlank()) {
            return@withContext Result.failure(InvalidAuthSessionException("Refresh token is missing."))
        }

        val bodyJson = moshi.adapter(RefreshTokenRequest::class.java)
            .toJson(RefreshTokenRequest(refreshToken))
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val body = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    val session = moshi.adapter(AuthSessionResponse::class.java).fromJson(body)
                        ?: return@withContext Result.failure(IOException("Empty refreshed session response"))
                    if (session.accessToken.isNullOrBlank() || session.refreshToken.isNullOrBlank()) {
                        return@withContext Result.failure(IOException("Refresh response omitted required session tokens"))
                    }
                    Result.success(session)
                } else if (it.code == 400 || it.code == 401) {
                    Result.failure(InvalidAuthSessionException("Your session has expired. Please sign in again."))
                } else {
                    Result.failure(IOException("Session refresh failed (HTTP ${it.code})."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revoke the current Supabase session on the server.
     */
    suspend fun signOut(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured() || accessToken.isBlank()) {
            return@withContext Result.success(Unit)
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/logout")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .post("".toRequestBody(jsonMediaType))
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful || it.code == 401) Result.success(Unit)
                else Result.failure(IOException("Server sign-out failed (HTTP ${it.code})."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register or heartbeat device with register-device Edge Function.
     */
    suspend fun registerDevice(req: RegisterDeviceRequest, authToken: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/register-device"
        val adapter = moshi.adapter(RegisterDeviceRequest::class.java)
        val bodyJson = adapter.toJson(req)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string() ?: ""
                if (it.isSuccessful) {
                    val parsed = moshi.adapter(RegisterDeviceResponse::class.java).fromJson(resBody)
                        ?: return@withContext Result.failure(IOException("Empty device registration response"))
                    Result.success(parsed.data.deviceId)
                } else {
                    Result.failure(IOException("Device registration failed (HTTP ${it.code}): $resBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Call get-my-repair-tools Edge Function.
     */
    suspend fun getMyRepairTools(deviceId: String, authToken: String): Result<List<RemoteSignedToolDto>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/get-my-repair-tools"
        val adapter = moshi.adapter(GetToolsRequest::class.java)
        val bodyJson = adapter.toJson(GetToolsRequest(deviceId = deviceId))

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string() ?: ""
                if (it.isSuccessful) {
                    val envelope = moshi.adapter(GetToolsResponse::class.java).fromJson(resBody)
                    Result.success(envelope?.data ?: emptyList())
                } else {
                    Result.failure(IOException("Failed to fetch tools (HTTP ${it.code}): $resBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Call get-signed-repair-tool Edge Function.
     */
    suspend fun getSignedRepairTool(toolId: String, deviceId: String, authToken: String): Result<SignedRecipeResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/get-signed-repair-tool"
        val adapter = moshi.adapter(GetSignedToolRequest::class.java)
        val bodyJson = adapter.toJson(GetSignedToolRequest(toolId = toolId, deviceId = deviceId))

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string() ?: ""
                if (it.isSuccessful) {
                    val recipe = moshi.adapter(SignedRecipeResponse::class.java).fromJson(resBody)
                        ?: return@withContext Result.failure(IOException("Empty signed recipe response"))
                    Result.success(recipe)
                } else {
                    Result.failure(IOException("Failed to fetch signed recipe (HTTP ${it.code}): $resBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit repair run execution report to Supabase backend.
     */
    suspend fun submitRepairRun(report: SubmitRunRequest, authToken: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/submit-repair-run"
        val adapter = moshi.adapter(SubmitRunRequest::class.java)
        val bodyJson = adapter.toJson(report)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string() ?: ""
                if (it.isSuccessful) {
                    val parsed = moshi.adapter(SubmitRunResponse::class.java).fromJson(resBody)
                    Result.success(parsed?.data?.runId ?: "")
                } else {
                    Result.failure(IOException("Failed to submit run report (HTTP ${it.code}): $resBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
