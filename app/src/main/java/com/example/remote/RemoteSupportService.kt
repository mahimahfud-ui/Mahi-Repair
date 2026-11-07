package com.example.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.domain.actions.ActionRegistry
import com.example.domain.models.ActionType
import com.example.domain.models.RecipeStep
import com.example.domain.models.StepStatus
import com.example.security.RsaSignatureVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native Android Foreground Service for Live Remote Support and Screen Sharing.
 * Conforms to Android 14+ MediaProjection foreground service policy:
 * - Requires explicit user consent via MediaProjectionManager.
 * - Displays a persistent notification with a one-tap STOP action.
 * - Restricts all incoming technician commands strictly to allowlisted ActionRegistry steps.
 * - Terminates instantly upon user request, app exit, or session completion.
 */
class RemoteSupportService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var pollingJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var actionRegistry: ActionRegistry? = null
    private var sessionId: String? = null
    private var backendUrl: String = BuildConfig.MAHI_API_BASE_URL.trim().trimEnd('/')
    private var authToken: String? = null

    companion object {
        const val ACTION_START = "com.example.remote.START"
        const val ACTION_STOP = "com.example.remote.STOP"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_PROJECTION_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        const val EXTRA_BACKEND_URL = "extra_backend_url"
        const val EXTRA_AUTH_TOKEN = "extra_auth_token"

        private const val NOTIFICATION_ID = 9110
        private const val CHANNEL_ID = "mahi_remote_support_channel"
    }

    override fun onCreate() {
        super.onCreate()
        actionRegistry = ActionRegistry(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        if (action == ACTION_STOP) {
            stopRemoteSession("User tapped Stop Session button in notification")
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            sessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
            authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)?.takeIf { it.isNotBlank() }

            val requestedBackend = intent.getStringExtra(EXTRA_BACKEND_URL)?.trim()?.trimEnd('/')
            val configuredBackend = BuildConfig.MAHI_API_BASE_URL.trim().trimEnd('/')
            backendUrl = requestedBackend?.takeIf { it.startsWith("https://") } ?: configuredBackend

            // Fail closed when credentials or an actual HTTPS backend are not configured.
            if (sessionId == null || authToken == null ||
                !backendUrl.startsWith("https://") || backendUrl.endsWith(".invalid")
            ) {
                stopSelf(startId)
                return START_NOT_STICKY
            }

            val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, 0)
            val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
            }

            if (resultCode != android.app.Activity.RESULT_OK || projectionData == null) {
                // A fresh, explicit MediaProjection consent result is required for every session.
                stopSelf(startId)
                return START_NOT_STICKY
            }

            if (resultCode != android.app.Activity.RESULT_OK || projectionData == null) {
                // A fresh, explicit MediaProjection consent result is required for every session.
                stopSelf(startId)
                return START_NOT_STICKY
            }

            // Foreground service notification with STOP action
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Initialize MediaProjection if provided
            if (resultCode != 0 && projectionData != null) {
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                mediaProjection = mpManager?.getMediaProjection(resultCode, projectionData)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        stopRemoteSession("Media projection stopped by system or user")
                    }
                }, null)
            }

            // Start command polling loop
            startCommandPolling()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mahi Live Remote Support",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active live remote assistance session indicator and control"
                setShowBadge(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = Intent(this, RemoteSupportService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mahi Live Remote Support Active")
            .setContentText("Remote diagnostics are active. Live video transport is not configured in this build.")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_delete,
                "STOP SESSION",
                stopPendingIntent
            )
            .build()
    }

    private fun startCommandPolling() {
        pollingJob?.cancel()
        val currentSessionId = sessionId ?: return

        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    pollAndExecuteCommand(currentSessionId)
                } catch (e: Exception) {
                    // Suppress and retry after delay
                }
                delay(3000)
            }
        }
    }

    private suspend fun pollAndExecuteCommand(currentSessionId: String) = withContext(Dispatchers.IO) {
        val url = URL("$backendUrl/api/v1/remote-support/$currentSessionId/commands")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        try {
            if (conn.responseCode != 200) return@withContext
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val commands = JSONObject(responseText).optJSONArray("data") ?: return@withContext

            for (index in 0 until commands.length()) {
                val cmd = commands.optJSONObject(index) ?: continue
                if (cmd.optString("status") != "PENDING") continue

                val commandId = cmd.optString("id")
                val canonicalPayload = cmd.optString("canonicalPayload")
                val signature = cmd.optString("signature")

                if (commandId.isBlank() || canonicalPayload.isBlank() ||
                    !RsaSignatureVerifier.verify(canonicalPayload, signature)
                ) {
                    if (commandId.isNotBlank()) {
                        submitCommandResult(currentSessionId, commandId, "REJECTED", "Command signature is missing or invalid.")
                    }
                    continue
                }

                val signedCommand = try {
                    JSONObject(canonicalPayload)
                } catch (_: Exception) {
                    submitCommandResult(currentSessionId, commandId, "REJECTED", "Signed command payload is invalid.")
                    continue
                }

                val signedExpiry = signedCommand.optString("expiresAt")
                val isExpired = runCatching {
                    java.time.Instant.parse(signedExpiry).isBefore(java.time.Instant.now())
                }.getOrDefault(true)
                val signedCommandId = signedCommand.optString("commandId")
                val signedSessionId = signedCommand.optString("sessionId")
                val signedDeviceId = signedCommand.optString("deviceId")
                if (isExpired || signedCommandId != commandId ||
                    signedSessionId != currentSessionId ||
                    signedDeviceId.isBlank() ||
                    signedDeviceId != cmd.optString("deviceId")
                ) {
                    submitCommandResult(currentSessionId, commandId, "REJECTED", "Command binding or expiry verification failed.")
                    continue
                }

                val actionStr = signedCommand.optString("action")
                val paramsJson = signedCommand.optJSONObject("params") ?: JSONObject()
                val params = mutableMapOf<String, Any?>()
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    params[key] = paramsJson.opt(key)
                }

                val actionType = ActionType.fromIdentifier(actionStr)
                if (actionType == null) {
                    submitCommandResult(currentSessionId, commandId, "REJECTED", "Action is not in the native allowlist.")
                    continue
                }

                val recipeStep = RecipeStep(
                    id = commandId,
                    action = actionType,
                    params = params
                )

                val validation = actionRegistry?.validateStep(recipeStep)
                if (validation?.isFailure == true) {
                    submitCommandResult(
                        currentSessionId,
                        commandId,
                        "REJECTED",
                        validation.exceptionOrNull()?.message ?: "Native action validation failed."
                    )
                    continue
                }

                val result = actionRegistry?.executeAction(recipeStep)
                val status = when (result?.status) {
                    StepStatus.COMPLETED -> "COMPLETED"
                    StepStatus.FAILED -> "FAILED"
                    StepStatus.WAITING_USER -> "WAITING_USER"
                    else -> "FAILED"
                }
                submitCommandResult(currentSessionId, commandId, status, result?.message ?: "No action result.")
                return@withContext
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun submitCommandResult(
        currentSessionId: String,
        commandId: String,
        status: String,
        message: String,
        details: Map<String, String>? = null
    ) {
        try {
            val url = URL("$backendUrl/api/v1/remote-support/$currentSessionId/command-result")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
                authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val result = JSONObject().put("message", message)
            if (details != null) result.put("details", JSONObject(details))
            val body = JSONObject().apply {
                put("command_id", commandId)
                put("status", status)
                put("result", result)
                if (status == "FAILED" || status == "REJECTED") put("error", message.take(500))
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
            // A failed acknowledgement is retried by the server's pending-command flow.
        }
    }

    private fun stopRemoteSession(reason: String) {
        pollingJob?.cancel()
        val currentSessionId = sessionId

        // Notify backend of termination
        if (currentSessionId != null) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val url = URL("$backendUrl/api/v1/remote-support/$currentSessionId/terminate")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 3000
                        readTimeout = 3000
                        authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                    }
                    val body = JSONObject().apply {
                        put("reason", reason)
                    }
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                    conn.responseCode
                    conn.disconnect()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        mediaProjection?.stop()
        mediaProjection = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
