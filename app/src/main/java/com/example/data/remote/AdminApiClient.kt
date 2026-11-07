package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
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

// --- Admin DTOs ---

@JsonClass(generateAdapter = true)
data class AdminPinVerifyRequest(
    val pin: String
)

@JsonClass(generateAdapter = true)
data class AdminPinVerifyResponse(
    val data: AdminPinVerifyData? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class AdminPinVerifyData(
    val verified: Boolean,
    @Json(name = "adminSessionToken") val adminSessionToken: String,
    @Json(name = "expiresInSeconds") val expiresInSeconds: Long
)

@JsonClass(generateAdapter = true)
data class AdminPinChangeRequest(
    @Json(name = "current_pin") val currentPin: String,
    @Json(name = "new_pin") val newPin: String
)

@JsonClass(generateAdapter = true)
data class AdminPinChangeResponse(
    val data: AdminPinChangeData? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class AdminPinChangeData(
    val success: Boolean,
    val message: String
)

@JsonClass(generateAdapter = true)
data class AdminErrorDto(
    val code: String?,
    val message: String?
)

@JsonClass(generateAdapter = true)
data class AdminAnalyticsData(
    @Json(name = "totalRuns") val totalRuns: Int,
    @Json(name = "successRate") val successRate: Double,
    @Json(name = "activeMembersCount") val activeMembersCount: Int,
    @Json(name = "activeDevicesCount") val activeDevicesCount: Int,
    @Json(name = "totalTools") val totalTools: Int,
    @Json(name = "publishedTools") val publishedTools: Int,
    @Json(name = "recentRuns") val recentRuns: List<AdminRecentRunDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AdminAnalyticsResponse(
    val data: AdminAnalyticsData? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class AdminRecentRunDto(
    @Json(name = "runId") val runId: String,
    @Json(name = "toolId") val toolId: String,
    @Json(name = "toolName") val toolName: String? = null,
    @Json(name = "userEmail") val userEmail: String? = null,
    val result: String,
    @Json(name = "startedAt") val startedAt: String,
    @Json(name = "durationMs") val durationMs: Long = 0L,
    @Json(name = "stepCount") val stepCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class AdminToolItem(
    val id: String,
    val slug: String,
    val name: String,
    val category: String,
    val description: String,
    @Json(name = "problemDescription") val problemDescription: String = "",
    val status: String,
    val version: Int,
    val audience: String,
    @Json(name = "estimatedSeconds") val estimatedSeconds: Int = 60,
    @Json(name = "minAndroidSdk") val minAndroidSdk: Int = 26,
    @Json(name = "createdAt") val createdAt: String = "",
    @Json(name = "updatedAt") val updatedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class AdminToolsResponse(
    val data: List<AdminToolItem>? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class AdminToolResponse(
    val data: AdminToolItem? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class CreateToolRequest(
    val name: String,
    val slug: String,
    val category: String,
    val description: String,
    val icon: String = "wrench",
    @Json(name = "draft_recipe") val draftRecipe: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class AdminMemberItem(
    val id: String,
    val email: String,
    @Json(name = "displayName") val displayName: String = "",
    val role: String,
    val status: String,
    @Json(name = "createdAt") val createdAt: String = "",
    @Json(name = "lastSignInAt") val lastSignInAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminMembersResponse(
    val data: List<AdminMemberItem>? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class InviteMemberRequest(
    val email: String,
    val role: String = "member",
    @Json(name = "display_name") val displayName: String = ""
)

@JsonClass(generateAdapter = true)
data class AdminDeviceItem(
    val id: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "userEmail") val userEmail: String? = null,
    @Json(name = "deviceLabel") val deviceLabel: String = "",
    @Json(name = "appVersion") val appVersion: String = "",
    @Json(name = "androidVersion") val androidVersion: String = "",
    @Json(name = "androidApi") val androidApi: Int = 0,
    @Json(name = "revokedAt") val revokedAt: String? = null,
    @Json(name = "lastSeenAt") val lastSeenAt: String = "",
    @Json(name = "createdAt") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class AdminDevicesResponse(
    val data: List<AdminDeviceItem>? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class GeminiAdminRequest(
    val instruction: String,
    val confirmed: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GeminiAdminData(
    val text: String,
    @Json(name = "toolCalls") val toolCalls: List<GeminiToolCallDto> = emptyList(),
    @Json(name = "executedActions") val executedActions: List<GeminiExecutedActionDto> = emptyList(),
    @Json(name = "requiresConfirmation") val requiresConfirmation: Boolean = false,
    @Json(name = "confirmationPrompt") val confirmationPrompt: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiToolCallDto(
    val name: String,
    val args: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GeminiExecutedActionDto(
    val action: String,
    val result: Any? = null
)

@JsonClass(generateAdapter = true)
data class GeminiAdminResponse(
    val data: GeminiAdminData? = null,
    val error: AdminErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class AdminAuditLogItem(
    val id: String,
    @Json(name = "actorId") val actorId: String,
    val action: String,
    @Json(name = "targetType") val targetType: String,
    @Json(name = "targetId") val targetId: String? = null,
    val details: Map<String, Any?> = emptyMap(),
    @Json(name = "createdAt") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class AdminAuditLogsResponse(
    val data: List<AdminAuditLogItem>? = null,
    val error: AdminErrorDto? = null
)

// --- Admin API Client ---

class AdminApiClient(
    private val baseUrl: String = BuildConfig.MAHI_API_BASE_URL.trim().trimEnd('/')
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // 1. Verify Admin PIN (Rate-limited, PBKDF2 on server)
    suspend fun verifyAdminPin(pin: String, authToken: String): Result<AdminPinVerifyData> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/admin/pin/verify"
        val bodyJson = moshi.adapter(AdminPinVerifyRequest::class.java).toJson(AdminPinVerifyRequest(pin))
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminPinVerifyResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    val msg = parsed?.error?.message ?: "Invalid administrative PIN (HTTP ${it.code})."
                    Result.failure(IOException(msg))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. Change Admin PIN
    suspend fun changeAdminPin(currentPin: String, newPin: String, authToken: String): Result<String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/admin/pin/change"
        val bodyJson = moshi.adapter(AdminPinChangeRequest::class.java).toJson(AdminPinChangeRequest(currentPin, newPin))
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminPinChangeResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data.message)
                } else {
                    val msg = parsed?.error?.message ?: "Failed to change PIN (HTTP ${it.code})."
                    Result.failure(IOException(msg))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. Get Dashboard Analytics
    suspend fun getAnalytics(authToken: String): Result<AdminAnalyticsData> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/analytics/dashboard"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminAnalyticsResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to load dashboard metrics."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. Get All Tools (Admin)
    suspend fun getAllTools(authToken: String): Result<List<AdminToolItem>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/tools"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminToolsResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to load tools."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 5. Create Tool
    suspend fun createTool(req: CreateToolRequest, authToken: String): Result<AdminToolItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/tools"
        val bodyJson = moshi.adapter(CreateToolRequest::class.java).toJson(req)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminToolResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to create tool."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 6. Publish Tool (Server canonicalizes & signs with RSA-3072 key)
    suspend fun publishTool(toolId: String, authToken: String): Result<AdminToolItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/tools/$toolId/publish"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminToolResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to publish tool."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 7. Disable Tool
    suspend fun disableTool(toolId: String, authToken: String): Result<AdminToolItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/tools/$toolId/disable"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .put("{}".toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminToolResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to disable tool."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 8. Get Members
    suspend fun getMembers(authToken: String): Result<List<AdminMemberItem>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/members"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminMembersResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to load members."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 9. Invite Member
    suspend fun inviteMember(req: InviteMemberRequest, authToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/members/invite"
        val bodyJson = moshi.adapter(InviteMemberRequest::class.java).toJson(req)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Failed to invite member (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 10. Update Member Status (ACTIVE / SUSPENDED)
    suspend fun setMemberStatus(memberId: String, status: String, authToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/members/$memberId/status"
        val bodyJson = "{\"status\": \"$status\"}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .put(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Failed to update status (HTTP ${it.code})."))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 11. Get Devices
    suspend fun getDevices(authToken: String): Result<List<AdminDeviceItem>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/devices"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminDevicesResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to load devices."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 12. Revoke Device
    suspend fun revokeDevice(deviceId: String, authToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/devices/$deviceId/revoke"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .put("{\"revoked\":true}".toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Failed to revoke device (HTTP ${it.code})."))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 13. Gemini AI Admin Assistant
    suspend fun sendGeminiInstruction(instruction: String, confirmed: Boolean, authToken: String): Result<GeminiAdminData> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/admin/gemini"
        val bodyJson = moshi.adapter(GeminiAdminRequest::class.java).toJson(GeminiAdminRequest(instruction, confirmed))
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(GeminiAdminResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Gemini assistant error (HTTP ${it.code})."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 14. Get Audit Logs
    suspend fun getAuditLogs(authToken: String): Result<List<AdminAuditLogItem>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/audit-logs"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                val resBody = it.body?.string().orEmpty()
                val parsed = moshi.adapter(AdminAuditLogsResponse::class.java).fromJson(resBody)
                if (it.isSuccessful && parsed?.data != null) {
                    Result.success(parsed.data)
                } else {
                    Result.failure(IOException(parsed?.error?.message ?: "Failed to load audit logs."))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
