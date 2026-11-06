package com.example.sync

import com.example.BuildConfig
import com.example.data.repository.RealtimeIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class RepairRealtimeEvent(val name: String, val eventId: String?)

class RealtimeAuthorizationException : IOException("The private repair update channel rejected this session.")

/**
 * Minimal Supabase Realtime v1.0.0 client for authenticated private Broadcast channels.
 * It carries no repair payloads: every event only prompts a fresh Edge Function sync.
 */
class SupabaseRealtimeClient(
    private val projectUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val apiKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val refs = AtomicInteger(1)

    fun isConfigured(): Boolean =
        projectUrl.startsWith("https://") &&
            apiKey.isNotBlank() &&
            !projectUrl.contains("placeholder.supabase.co") &&
            !apiKey.contains("placeholder-anon-key")

    /**
     * Connects once, joins a device-specific private channel, and returns when the
     * socket closes. The caller owns reconnect/backoff and refreshes the JWT per attempt.
     */
    suspend fun listenOnce(
        identity: RealtimeIdentity,
        refreshSession: suspend () -> RealtimeIdentity,
        onJoined: suspend () -> Unit,
        onEvent: suspend (RepairRealtimeEvent) -> Unit
    ) {
        if (!isConfigured()) throw IOException("Realtime is not configured.")
        val context = currentCoroutineContext()
        val inbound = Channel<RepairRealtimeEvent>(Channel.BUFFERED)
        val joinResult = CompletableDeferred<Unit>()
        val socketRef = AtomicReference<WebSocket?>(null)
        val joinRef = refs.incrementAndGet().toString()
        val topicName = "mahi-repair:member:${identity.userId}:device:${identity.deviceId}"
        val wireTopic = "realtime:$topicName"

        val websocketUrl = projectUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=" +
            URLEncoder.encode(apiKey, "UTF-8") + "&vsn=1.0.0"

        val request = Request.Builder().url(websocketUrl).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketRef.set(webSocket)
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("enabled", false).put("key", ""))
                    .put("postgres_changes", JSONArray())
                    .put("private", true)
                val payload = JSONObject()
                    .put("config", config)
                    .put("access_token", identity.accessToken)
                val join = JSONObject()
                    .put("topic", wireTopic)
                    .put("event", "phx_join")
                    .put("payload", payload)
                    .put("ref", joinRef)
                    .put("join_ref", joinRef)
                if (!webSocket.send(join.toString())) {
                    joinResult.completeExceptionally(IOException("Could not join the private update channel."))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    when (root.optString("event")) {
                        "phx_reply" -> {
                            if (root.optString("ref") == joinRef) {
                                val payload = root.optJSONObject("payload")
                                if (payload?.optString("status") == "ok") {
                                    joinResult.complete(Unit)
                                } else {
                                    joinResult.completeExceptionally(RealtimeAuthorizationException())
                                }
                            }
                        }
                        "broadcast" -> {
                            parseBroadcastMessage(text)?.let(inbound::trySend)
                        }
                        "phx_error", "phx_close" -> {
                            val error = IOException("The private repair update channel closed.")
                            joinResult.completeExceptionally(error)
                            inbound.close(error)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore malformed frames. No frame content is logged or surfaced to the UI.
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = IOException("Realtime connection interrupted.", t)
                joinResult.completeExceptionally(error)
                inbound.close(error)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val error = IOException("Realtime connection closed.")
                joinResult.completeExceptionally(error)
                inbound.close()
            }
        }

        val socket = client.newWebSocket(request, listener)
        socketRef.set(socket)
        var heartbeatJob: Job? = null
        var authRefreshJob: Job? = null
        try {
            withTimeout(JOIN_TIMEOUT_MS) { joinResult.await() }
            onJoined()

            heartbeatJob = CoroutineScope(context).launch(Dispatchers.IO) {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    val ref = refs.incrementAndGet().toString()
                    val heartbeat = JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", ref)
                    if (socketRef.get()?.send(heartbeat.toString()) != true) {
                        socketRef.get()?.close(1011, "Realtime heartbeat failed")
                        break
                    }
                }
            }

            authRefreshJob = CoroutineScope(context).launch(Dispatchers.IO) {
                while (isActive) {
                    val waitUntilRefresh = (
                        identity.expiresAtMillis - System.currentTimeMillis() - AUTH_REFRESH_SKEW_MS
                    ).coerceAtLeast(MIN_AUTH_REFRESH_DELAY_MS)
                    delay(waitUntilRefresh)

                    val refreshedIdentity = runCatching { refreshSession() }.getOrNull()
                    if (refreshedIdentity == null ||
                        refreshedIdentity.userId != identity.userId ||
                        refreshedIdentity.deviceId != identity.deviceId
                    ) {
                        socketRef.get()?.close(4001, "Authentication refresh required")
                        break
                    }

                    // Refreshing the session updates DataStore; the app's session observer
                    // then replaces this socket with one authenticated by the new JWT.
                    if (refreshedIdentity.accessToken != identity.accessToken) break
                    if (refreshedIdentity.expiresAtMillis <= System.currentTimeMillis() + AUTH_REFRESH_SKEW_MS) {
                        socketRef.get()?.close(4001, "Authentication token expired")
                        break
                    }
                }
            }

            for (event in inbound) {
                if (!context.isActive) break
                onEvent(event)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            heartbeatJob?.cancel()
            authRefreshJob?.cancel()
            val leaveRef = refs.incrementAndGet().toString()
            socketRef.get()?.send(
                JSONObject()
                    .put("topic", wireTopic)
                    .put("event", "phx_leave")
                    .put("payload", JSONObject())
                    .put("ref", leaveRef)
                    .put("join_ref", joinRef)
                    .toString()
            )
            socketRef.get()?.close(1000, "App moved to background")
            inbound.close()
        }
    }

    companion object {
        private const val JOIN_TIMEOUT_MS = 20_000L
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val AUTH_REFRESH_SKEW_MS = 55_000L
        private const val MIN_AUTH_REFRESH_DELAY_MS = 10_000L
        private val SUPPORTED_EVENTS = setOf(
            "catalog_changed",
            "member_access_changed",
            "device_revoked"
        )

        internal fun parseBroadcastMessage(message: String): RepairRealtimeEvent? {
            return runCatching {
                val root = JSONObject(message)
                if (root.optString("event") != "broadcast") return null
                val payload = root.optJSONObject("payload") ?: return null
                val eventName = payload.optString("event")
                if (eventName !in SUPPORTED_EVENTS) return null
                val eventId = payload.optJSONObject("payload")
                    ?.optString("event_id")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                RepairRealtimeEvent(eventName, eventId)
            }.getOrNull()
        }
    }
}
