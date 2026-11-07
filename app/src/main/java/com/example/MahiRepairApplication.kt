package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.SupabaseClient
import com.example.data.repository.AuthRepository
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ToolRepository
import com.example.domain.actions.ActionRegistry
import com.example.domain.engine.RecipeExecutionEngine
import com.example.sync.RepairRealtimeEvent
import com.example.sync.SupabaseRealtimeClient
import com.example.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MahiRepairApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seenRealtimeEvents = LinkedHashSet<String>(128)
    val appUpdateEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @Volatile private var appIsForeground = false
    private var realtimeJob: Job? = null

    lateinit var database: AppDatabase private set
    lateinit var preferencesManager: PreferencesManager private set
    lateinit var supabaseClient: SupabaseClient private set
    lateinit var authRepository: AuthRepository private set
    lateinit var toolRepository: ToolRepository private set
    lateinit var appUpdateRepository: AppUpdateRepository private set
    lateinit var historyRepository: HistoryRepository private set
    lateinit var actionRegistry: ActionRegistry private set
    lateinit var recipeExecutionEngine: RecipeExecutionEngine private set

    private lateinit var realtimeClient: SupabaseRealtimeClient

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        supabaseClient = SupabaseClient()
        authRepository = AuthRepository(preferencesManager, supabaseClient)
        toolRepository = ToolRepository(database, preferencesManager, supabaseClient, authRepository)
        appUpdateRepository = AppUpdateRepository(this, supabaseClient, toolRepository)
        historyRepository = HistoryRepository(database)
        actionRegistry = ActionRegistry(this)
        recipeExecutionEngine = RecipeExecutionEngine(actionRegistry)
        realtimeClient = SupabaseRealtimeClient()

        // Remove credentials written in plaintext by earlier app versions.
        applicationScope.launch {
            preferencesManager.clearLegacyPlaintextSession()
        }

        // WorkManager provides eventual consistency when the process is not alive.
        SyncManager.schedulePeriodicSync(this)
    }

    /**
     * Keep the WebSocket only while the app is foregrounded. Joining/rejoining the
     * device's private channel always triggers a complete server-authorized catalog sync.
     */
    fun setAppForegrounded(foreground: Boolean) {
        appIsForeground = foreground
        if (!foreground) {
            realtimeJob?.cancel()
            realtimeJob = null
            return
        }
        // Recheck updates on each foreground transition so releases published while
        // the app was backgrounded are visible even though its private socket was closed.
        appUpdateEvents.tryEmit(Unit)
        if (realtimeJob?.isActive == true) return

        realtimeJob = applicationScope.launch {
            authRepository.sessionFlow.collectLatest { session ->
                if (!appIsForeground || !session.isAuthenticated) return@collectLatest

                var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                while (appIsForeground && currentCoroutineContext().isActive) {
                    val identityResult = toolRepository.getRealtimeIdentity()
                    if (identityResult.isFailure) {
                        if (!authRepository.getSession().isAuthenticated) break
                        delay(reconnectDelayMs)
                        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                        continue
                    }

                    try {
                        realtimeClient.listenOnce(
                            identity = identityResult.getOrThrow(),
                            refreshSession = {
                                val refreshed = toolRepository.getRealtimeIdentity().getOrElse { cause ->
                                    throw cause
                                }
                                if (refreshed.userId != identityResult.getOrThrow().userId ||
                                    refreshed.deviceId != identityResult.getOrThrow().deviceId
                                ) {
                                    throw IllegalStateException("Realtime identity changed.")
                                }
                                refreshed
                            },
                            onJoined = {
                                val sync = toolRepository.syncTools()
                                if (sync.isFailure) {
                                    Log.w(TAG, "Catalog refresh after Realtime join failed.")
                                } else {
                                    reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                                }
                            },
                            onEvent = { event -> handleRealtimeEvent(event) }
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // A failed private-channel join is not trusted as authorization.
                        // Refresh from the Edge Function to clear stale/revoked catalogue entries.
                        if (error is com.example.sync.RealtimeAuthorizationException) {
                            val sync = toolRepository.syncTools()
                            if (sync.isFailure) {
                                Log.w(TAG, "Server authorization rejected the cached catalogue.")
                            }
                        }
                        Log.w(TAG, "Realtime connection ended; will reconnect.")
                    }

                    if (appIsForeground) {
                        delay(reconnectDelayMs)
                        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                    }
                }
            }
        }
    }

    private suspend fun handleRealtimeEvent(event: RepairRealtimeEvent) {
        if (!rememberRealtimeEvent(event.eventId)) return

        when (event.name) {
            "catalog_changed" -> {
                val result = toolRepository.syncTools()
                if (result.isFailure) {
                    Log.w(TAG, "Catalog synchronization after notification failed.")
                }
            }
            "app_update_published" -> {
                // Realtime carries only an opaque event. The member app fetches and
                // verifies the signed manifest using its authenticated device identity.
                appUpdateEvents.tryEmit(Unit)
            }
            "member_access_changed", "device_revoked" -> {
                toolRepository.clearLocalCatalog()
                // Clear the encrypted local session even when the network is unavailable.
                authRepository.signOut()
            }
        }
    }

    private fun rememberRealtimeEvent(eventId: String?): Boolean {
        if (eventId.isNullOrBlank()) return true
        synchronized(seenRealtimeEvents) {
            if (!seenRealtimeEvents.add(eventId)) return false
            while (seenRealtimeEvents.size > MAX_REMEMBERED_EVENTS) {
                seenRealtimeEvents.remove(seenRealtimeEvents.first())
            }
            return true
        }
    }

    companion object {
        private const val TAG = "MahiRepairRealtime"
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val MAX_REMEMBERED_EVENTS = 128
    }
}
