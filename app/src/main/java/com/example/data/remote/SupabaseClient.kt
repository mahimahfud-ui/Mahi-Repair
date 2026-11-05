package com.example.data.remote

import com.example.BuildConfig
import com.example.data.remote.dto.AuthSessionResponse
import com.example.data.remote.dto.OtpRequest
import com.example.data.remote.dto.RemoteToolDto
import com.example.data.remote.dto.RepairRunReportRequest
import com.example.data.remote.dto.SignedRecipeResponse
import com.example.data.remote.dto.VerifyOtpRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient(
    private var baseUrl: String = BuildConfig.SUPABASE_URL,
    private var anonKey: String = BuildConfig.SUPABASE_ANON_KEY
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

    fun updateConfig(url: String, key: String) {
        baseUrl = url.trimEnd('/')
        anonKey = key.trim()
    }

    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank() &&
                anonKey.isNotBlank() &&
                !baseUrl.contains("placeholder.supabase.co")
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
     * Call get-my-repair-tools Edge Function.
     */
    suspend fun getMyRepairTools(authToken: String): Result<List<RemoteToolDto>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/get-my-repair-tools"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string() ?: ""
                if (it.isSuccessful) {
                    val listType = Types.newParameterizedType(List::class.java, RemoteToolDto::class.java)
                    val adapter = moshi.adapter<List<RemoteToolDto>>(listType)
                    val tools = adapter.fromJson(resBody) ?: emptyList()
                    Result.success(tools)
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
    suspend fun getSignedRepairTool(toolId: String, authToken: String): Result<SignedRecipeResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/get-signed-repair-tool"
        val payload = "{\"tool_id\": \"$toolId\"}"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .post(payload.toRequestBody(jsonMediaType))
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
    suspend fun submitRepairRun(report: RepairRunReportRequest, authToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
        }

        val url = "$baseUrl/functions/v1/submit-repair-run"
        val adapter = moshi.adapter(RepairRunReportRequest::class.java)
        val bodyJson = adapter.toJson(report)

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $authToken")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val resBody = it.body?.string() ?: ""
                    Result.failure(IOException("Failed to submit run report (HTTP ${it.code}): $resBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
