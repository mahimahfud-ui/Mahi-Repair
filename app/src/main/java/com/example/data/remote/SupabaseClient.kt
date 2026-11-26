package com.example.data.remote

import com.example.BuildConfig
import com.example.data.remote.dto.AppUpdateManifestRequest
import com.example.data.remote.dto.AppUpdateManifestResponse
import com.example.data.remote.dto.AppUpdateManifestData
import com.example.data.remote.dto.AppUpdateAssetRequest
import com.example.data.remote.dto.AppUpdateAssetResponse
import com.example.data.remote.dto.AppUpdateAssetData
import com.example.data.remote.dto.AuthSessionResponse
import com.example.data.remote.dto.MemberLoginRequest
import com.example.data.remote.dto.GetSignedToolRequest
import com.example.data.remote.dto.GetToolsRequest
import com.example.data.remote.dto.GetToolsResponse
import com.example.data.remote.dto.RefreshTokenRequest
import com.example.data.remote.dto.RegisterDeviceRequest
import com.example.data.remote.dto.RegisterDeviceResponse
import com.example.data.remote.dto.RemoteSignedToolDto
import com.example.data.remote.dto.SignedRecipeResponse
import com.example.data.remote.dto.SubmitRunRequest
import com.example.data.remote.dto.SubmitRunResponse
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
     * Create a per-installation anonymous Supabase identity. Anonymous Sign-Ins
     * must be enabled in the project's Auth settings. This is a scoped Auth JWT,
     * not a service-role bypass; device and tool authorization remain server-side.
     */
    suspend fun signInAnonymously(): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Repair service is not configured."))
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val body = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    val session = moshi.adapter(AuthSessionResponse::class.java).fromJson(body)
                        ?: return@withContext Result.failure(IOException("Public access response was empty."))
                    if (session.accessToken.isNullOrBlank() ||
                        session.refreshToken.isNullOrBlank() ||
                        session.user == null
                    ) {
                        return@withContext Result.failure(IOException("Public access response omitted its session identity."))
                    }
                    Result.success(session)
                } else if (it.code == 429) {
                    Result.failure(IOException("Public access is temporarily rate-limited. Please wait and try again."))
                } else if (it.code == 400 || it.code == 422) {
                    Result.failure(IOException("Public access is not enabled yet. Enable Anonymous Sign-Ins in Supabase Auth settings."))
                } else {
                    Result.failure(IOException("Public access is temporarily unavailable (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Exchange a server-issued Member ID and PIN for a scoped Supabase session.
     * Retained only for compatibility with previously issued Member credentials.
     */
    suspend fun signInWithMemberCredentials(memberCode: String, pin: String): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Repair service is not configured."))
        val body = moshi.adapter(MemberLoginRequest::class.java).toJson(MemberLoginRequest(memberCode, pin))
        val request = Request.Builder()
            .url("$baseUrl/functions/v1/member-login")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val responseBody = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    val session = moshi.adapter(AuthSessionResponse::class.java).fromJson(responseBody)
                        ?: return@withContext Result.failure(IOException("Member sign-in response was empty."))
                    if (session.accessToken.isNullOrBlank() || session.refreshToken.isNullOrBlank()) {
                        return@withContext Result.failure(IOException("Member sign-in response omitted session tokens."))
                    }
                    Result.success(session)
                } else if (it.code == 401 || it.code == 403) {
                    Result.failure(IOException("Member ID or PIN is incorrect, or account access is inactive."))
                } else if (it.code == 429) {
                    Result.failure(IOException("Too many sign-in attempts. Wait before trying again."))
                } else {
                    Result.failure(IOException("Member sign-in is temporarily unavailable (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
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



    /**
     * Fetch the latest signed update manifest for this authenticated, registered device.
     * The caller must verify the detached signature before parsing or trusting its contents.
     */
    suspend fun getAppUpdateManifest(
        deviceId: String,
        channel: String,
        authToken: String
    ): Result<AppUpdateManifestData?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }
        if (authToken.isBlank() || channel !in setOf("stable", "beta")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid app-update request."))
        }

        val body = moshi.adapter(AppUpdateManifestRequest::class.java)
            .toJson(AppUpdateManifestRequest(deviceId = deviceId, channel = channel))
        val request = Request.Builder()
            .url("$baseUrl/functions/v1/get-app-update-manifest")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val responseBody = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    val envelope = moshi.adapter(AppUpdateManifestResponse::class.java).fromJson(responseBody)
                        ?: return@withContext Result.failure(IOException("Empty app-update manifest response."))
                    Result.success(envelope.data)
                } else {
                    Result.failure(IOException("App-update manifest request failed (HTTP ${it.code})."))
                }
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Request a short-lived Storage URL for an asset already included in a verified release.
     * The backend rechecks the signed-in member and registered, non-revoked device.
     */
    suspend fun getAppUpdateAssetUrl(
        deviceId: String,
        releaseId: String,
        assetId: String,
        authToken: String
    ): Result<AppUpdateAssetData> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }
        if (authToken.isBlank() || !Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(assetId)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid app-update asset request."))
        }

        val body = moshi.adapter(AppUpdateAssetRequest::class.java)
            .toJson(AppUpdateAssetRequest(deviceId = deviceId, releaseId = releaseId, assetId = assetId))
        val request = Request.Builder()
            .url("$baseUrl/functions/v1/get-app-update-asset-url")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val responseBody = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    val envelope = moshi.adapter(AppUpdateAssetResponse::class.java).fromJson(responseBody)
                        ?: return@withContext Result.failure(IOException("Empty app-update asset response."))
                    Result.success(envelope.data)
                } else {
                    Result.failure(IOException("App-update asset request failed (HTTP ${it.code})."))
                }
            }
        } catch (error: Exception) {
            Result.failure(error)
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
