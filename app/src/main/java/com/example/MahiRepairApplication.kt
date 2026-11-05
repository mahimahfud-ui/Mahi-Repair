package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.SupabaseClient
import com.example.data.repository.AuthRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ToolRepository
import com.example.domain.actions.ActionRegistry
import com.example.domain.engine.RecipeExecutionEngine
import com.example.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MahiRepairApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase private set
    lateinit var preferencesManager: PreferencesManager private set
    lateinit var supabaseClient: SupabaseClient private set
    lateinit var authRepository: AuthRepository private set
    lateinit var toolRepository: ToolRepository private set
    lateinit var historyRepository: HistoryRepository private set
    lateinit var actionRegistry: ActionRegistry private set
    lateinit var recipeExecutionEngine: RecipeExecutionEngine private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        supabaseClient = SupabaseClient()
        authRepository = AuthRepository(preferencesManager, supabaseClient)
        toolRepository = ToolRepository(database, preferencesManager, supabaseClient)
        historyRepository = HistoryRepository(database)
        actionRegistry = ActionRegistry(this)
        recipeExecutionEngine = RecipeExecutionEngine(actionRegistry)

        // Initialize default verified recipes and schedule periodic background sync
        applicationScope.launch {
            toolRepository.initializeCatalogIfEmpty()
        }

        SyncManager.schedulePeriodicSync(this)
    }
}
