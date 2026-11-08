package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AdminDashboardSnapshot(
    val analytics: AdminAnalyticsData,
    val tools: List<AdminToolItem> = emptyList(),
    val members: List<AdminMemberItem> = emptyList(),
    val devices: List<AdminDeviceItem> = emptyList(),
    val auditLogs: List<AdminAuditLogItem> = emptyList()
)

/**
 * Owner-only Android administration client. All privileged operations are
 * dispatched through JWT-protected Supabase Edge Functions; service-role
 * credentials never leave the server.
 */
class SupabaseOwnerAdminApiClient(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
    private val publishableKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun isConfigured(): Boolean =
        supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("placeholder.supabase.co") &&
            publishableKey.isNotBlank() &&
            !publishableKey.contains("placeholder")

    private suspend fun invoke(
        function: String,
        authToken: String,
        body: JSONObject = JSONObject()
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase Owner services are not configured in this build.")
            )
        }
        if (authToken.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Sign in with the active Owner account before using administration.")
            )
        }
        try {
            val request = Request.Builder()
                .url("$supabaseUrl/functions/v1/$function")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer $authToken")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(mediaType))
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(raw) }.getOrNull()
                if (!response.isSuccessful) {
                    val message = root?.optJSONObject("error")?.optString("message")
                        ?.takeIf { !it.isNullOrBlank() }
                        ?: "Owner request failed (HTTP " + response.code + ")."
                    return@withContext Result.failure(IOException(message))
                }
                val data = root?.optJSONObject("data")
                    ?: return@withContext Result.failure(
                        IOException("Supabase returned an invalid Owner response.")
                    )
                Result.success(data)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(IOException("Could not reach Supabase Owner services.", error))
        }
    }

    suspend fun getDashboard(authToken: String): Result<AdminDashboardSnapshot> =
        invoke("get-admin-dashboard", authToken).mapCatching { data ->
            moshi.adapter(AdminDashboardSnapshot::class.java).fromJson(data.toString())
                ?: throw IOException("Owner dashboard response was empty.")
        }

    suspend fun getAnalytics(authToken: String): Result<AdminAnalyticsData> =
        getDashboard(authToken).map { it.analytics }

    suspend fun getAllTools(authToken: String): Result<List<AdminToolItem>> =
        getDashboard(authToken).map { it.tools }

    suspend fun getMembers(authToken: String): Result<List<AdminMemberItem>> =
        getDashboard(authToken).map { it.members }

    suspend fun getDevices(authToken: String): Result<List<AdminDeviceItem>> =
        getDashboard(authToken).map { it.devices }

    suspend fun getAuditLogs(authToken: String): Result<List<AdminAuditLogItem>> =
        getDashboard(authToken).map { it.auditLogs }

    suspend fun createTool(req: CreateToolRequest, authToken: String): Result<AdminToolItem> {
        val body = runCatching {
            JSONObject(moshi.adapter(CreateToolRequest::class.java).toJson(req))
        }.getOrElse { return Result.failure(it) }
        val saved = invoke("admin-upsert-tool", authToken, body)
        if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        return getAllTools(authToken).mapCatching { tools ->
            tools.firstOrNull { it.slug == req.slug }
                ?: throw IOException("Tool was saved but was not returned by the Owner catalogue.")
        }
    }

    suspend fun updateTool(
        toolId: String,
        requestData: UpdateToolRequest,
        authToken: String
    ): Result<AdminToolItem> {
        val existing = getAllTools(authToken)
        if (existing.isFailure) return Result.failure(existing.exceptionOrNull()!!)
        val tool = existing.getOrThrow().firstOrNull { it.id == toolId }
            ?: return Result.failure(IOException("Tool not found. Refresh the Owner catalogue and retry."))

        val recipe = tool.draftRecipe.toMutableMap()
        recipe["title"] = requestData.name.trim()
        recipe["category"] = requestData.category.lowercase()
        recipe["changelog"] = requestData.description.trim()
        val body = JSONObject()
            .put("id", tool.id)
            .put("name", requestData.name.trim())
            .put("slug", tool.slug)
            .put("category", requestData.category.lowercase())
            .put("description", requestData.description.trim())
            .put("icon", tool.icon)
            .put("draft_recipe", JSONObject(recipe))

        val saved = invoke("admin-upsert-tool", authToken, body)
        if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        return getAllTools(authToken).mapCatching { tools ->
            tools.firstOrNull { it.id == toolId }
                ?: throw IOException("Updated tool could not be reloaded.")
        }
    }

    suspend fun publishTool(toolId: String, authToken: String): Result<AdminToolItem> {
        val saved = invoke("admin-publish-tool", authToken, JSONObject().put("tool_id", toolId))
        if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        return getAllTools(authToken).mapCatching { tools ->
            tools.firstOrNull { it.id == toolId }
                ?: throw IOException("Published tool could not be reloaded.")
        }
    }

    suspend fun disableTool(toolId: String, authToken: String): Result<AdminToolItem> {
        val saved = invoke(
            "admin-disable-tool",
            authToken,
            JSONObject().put("tool_id", toolId).put("disabled", true)
        )
        if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        return getAllTools(authToken).mapCatching { tools ->
            tools.firstOrNull { it.id == toolId }
                ?: throw IOException("Tool state could not be reloaded.")
        }
    }

    suspend fun inviteMember(req: InviteMemberRequest, authToken: String): Result<Unit> {
        val body = JSONObject().put("email", req.email.trim().lowercase())
        return invoke("admin-invite-member", authToken, body).map { Unit }
    }

    suspend fun setMemberStatus(
        memberId: String,
        status: String,
        authToken: String
    ): Result<Unit> {
        val normalized = status.lowercase()
        if (normalized !in setOf("active", "suspended", "revoked")) {
            return Result.failure(IllegalArgumentException("Unsupported Member status."))
        }
        val body = JSONObject().put("user_id", memberId).put("status", normalized)
        return invoke("admin-manage-member", authToken, body).map { Unit }
    }

    suspend fun revokeDevice(deviceId: String, authToken: String): Result<Unit> =
        invoke(
            "admin-manage-device",
            authToken,
            JSONObject().put("device_id", deviceId).put("revoked", true)
        ).map { Unit }

    suspend fun sendGeminiInstruction(
        instruction: String,
        confirmed: Boolean,
        authToken: String
    ): Result<GeminiAdminData> = Result.failure(
        IllegalStateException(
            "The Gemini Owner assistant is not connected to a production server in this build. " +
                "Tool and account controls remain available through the secured Supabase functions."
        )
    )
}
