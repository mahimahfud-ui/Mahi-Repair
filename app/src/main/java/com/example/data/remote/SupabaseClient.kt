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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class InvalidAuthSessionException(message: String) : IOException(message)

class ServerAuthorizationException(val statusCode: Int, message: String) : IOException(message)

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
                    Result.failure(IOException("Failed to send OTP (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    Result.failure(IOException("OTP verification failed (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    if (it.code == 401 || it.code == 403) Result.failure(ServerAuthorizationException(it.code, "This account or device is not authorized.")) else Result.failure(IOException("Device registration failed (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    if (envelope == null) Result.failure(IOException("Repair catalog response was empty or malformed.")) else Result.success(envelope.data)
                } else {
                    if (it.code == 401 || it.code == 403) Result.failure(ServerAuthorizationException(it.code, "Access to the repair catalog was denied.")) else Result.failure(IOException("Failed to fetch repair tools (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    if (it.code == 401 || it.code == 403) Result.failure(ServerAuthorizationException(it.code, "This repair is no longer authorized for this device.")) else Result.failure(IOException("Failed to fetch signed repair (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Persist and replay the same compact server request for every idempotent retry. */
    fun encodeSubmitRunRequest(report: SubmitRunRequest): String =
        moshi.adapter(SubmitRunRequest::class.java).toJson(report)

    fun decodeSubmitRunRequest(payloadJson: String): SubmitRunRequest =
        requireNotNull(moshi.adapter(SubmitRunRequest::class.java).fromJson(payloadJson)) {
            "The queued repair request is malformed."
        }

    /**
     * Submit repair run execution report to Supabase backend.
     * A serialized body may be supplied by the durable outbox to preserve exact retries.
     */
    suspend fun submitRepairRun(
        report: SubmitRunRequest,
        authToken: String,
        serializedRequestJson: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/submit-repair-run"
        val adapter = moshi.adapter(SubmitRunRequest::class.java)
        val bodyJson = serializedRequestJson ?: adapter.toJson(report)
        val persistedRequest = adapter.fromJson(bodyJson)
            ?: return@withContext Result.failure(IOException("The queued repair request is malformed."))
        if (persistedRequest.runId != report.runId) {
            return@withContext Result.failure(IOException("The queued repair request ID does not match its outbox key."))
        }

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
                        ?: return@withContext Result.failure(IOException("Report acceptance response was empty."))
                    if (!parsed.data.accepted || parsed.data.runId != report.runId) {
                        return@withContext Result.failure(IOException("Server did not confirm acceptance of this repair report."))
                    }
                    Result.success(parsed.data.runId)
                } else {
                    if (it.code == 401 || it.code == 403) Result.failure(ServerAuthorizationException(it.code, "The server denied this report submission.")) else Result.failure(IOException("Failed to submit run report (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
