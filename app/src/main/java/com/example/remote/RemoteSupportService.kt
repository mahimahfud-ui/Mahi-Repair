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
import com.example.domain.actions.ActionRegistry
import com.example.domain.models.ActionType
import com.example.domain.models.RecipeStep
import com.example.domain.models.StepStatus
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
    private var backendUrl: String = "https://mahi-repair-admin.onrender.com"
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
            sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            intent.getStringExtra(EXTRA_BACKEND_URL)?.let { backendUrl = it.trimEnd('/') }
            authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)

            val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, 0)
            val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
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
            .setContentText("A technician is connected with your explicit consent.")
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
        val url = URL("$backendUrl/api/v1/remote-support/$currentSessionId/commands/pending")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val success = json.optBoolean("success", false)
            if (success && json.has("command") && !json.isNull("command")) {
                val cmd = json.getJSONObject("command")
                val commandId = cmd.getString("id")
                val actionStr = cmd.getString("action")
                val stepName = cmd.optString("name", actionStr)
                val paramsJson = cmd.optJSONObject("params") ?: JSONObject()

                val params = mutableMapOf<String, Any?>()
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsJson.opt(k)
                }

                // Strictly validate action type
                val actionType = try {
                    ActionType.valueOf(actionStr)
                } catch (e: Exception) {
                    null
                }

                if (actionType == null) {
                    submitCommandResult(currentSessionId, commandId, "FAILED", "Rejected unallowlisted action: $actionStr")
                    return@withContext
                }

                val recipeStep = RecipeStep(
                    name = stepName,
                    action = actionType,
                    params = params
                )

                // Validate step
                val validation = actionRegistry?.validateStep(recipeStep)
                if (validation?.isFailure == true) {
                    submitCommandResult(currentSessionId, commandId, "FAILED", validation.exceptionOrNull()?.message ?: "Validation failed")
                    return@withContext
                }

                // Execute step
                val result = actionRegistry?.executeAction(recipeStep)
                val statusStr = when (result?.status) {
                    StepStatus.COMPLETED -> "COMPLETED"
                    StepStatus.FAILED -> "FAILED"
                    StepStatus.WAITING_USER -> "WAITING_USER"
                    else -> "COMPLETED"
                }
                submitCommandResult(currentSessionId, commandId, statusStr, result?.message ?: "Executed", result?.details)
            }
        }
        conn.disconnect()
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

            val body = JSONObject().apply {
                put("commandId", commandId)
                put("status", status)
                put("message", message)
                if (details != null) {
                    put("details", JSONObject(details))
                }
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            // Ignore submission failure
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
