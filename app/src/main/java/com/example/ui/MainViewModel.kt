package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MahiRepairApplication
import com.example.data.local.RepairHistoryEntity
import com.example.data.local.UserSession
import com.example.domain.engine.ExecutionEvent
import com.example.domain.models.RepairRunReport
import com.example.domain.models.RepairTool
import com.example.domain.models.ToolCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val session: UserSession = UserSession("", "", "", false),
    val tools: List<RepairTool> = emptyList(),
    val filteredTools: List<RepairTool> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ToolCategory = ToolCategory.ALL,
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val executionState: ExecutionEvent = ExecutionEvent.Idle,
    val showHistoryDialog: Boolean = false,
    val historyList: List<RepairHistoryEntity> = emptyList(),
    val authStatusMessage: String? = null,
    val isAuthLoading: Boolean = false,
    val otpSentForEmail: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MahiRepairApplication
    private val authRepo = app.authRepository
    private val toolRepo = app.toolRepository
    private val historyRepo = app.historyRepository
    private val engine = app.recipeExecutionEngine

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(ToolCategory.ALL)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    private val _showHistoryDialog = MutableStateFlow(false)
    private val _authStatusMessage = MutableStateFlow<String?>(null)
    private val _isAuthLoading = MutableStateFlow(false)
    private val _otpSentForEmail = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        authRepo.sessionFlow,
        toolRepo.toolsFlow,
        _searchQuery,
        _selectedCategory,
        toolRepo.lastSyncFlow,
        _isSyncing,
        _syncStatusMessage,
        engine.engineState,
        _showHistoryDialog,
        _authStatusMessage,
        _isAuthLoading,
        _otpSentForEmail
    ) { params ->
        val session = params[0] as UserSession
        @Suppress("UNCHECKED_CAST")
        val allTools = params[1] as List<RepairTool>
        val query = params[2] as String
        val category = params[3] as ToolCategory
        val lastSync = params[4] as Long
        val syncing = params[5] as Boolean
        val syncMsg = params[6] as String?
        val execState = params[7] as ExecutionEvent
        val showHistory = params[8] as Boolean
        val authMsg = params[9] as String?
        val authLoading = params[10] as Boolean
        val otpEmail = params[11] as String?

        val filtered = allTools.filter { tool ->
            val matchesCategory = category == ToolCategory.ALL || tool.category == category
            val matchesQuery = query.isBlank() ||
                    tool.name.contains(query, ignoreCase = true) ||
                    tool.description.contains(query, ignoreCase = true) ||
                    tool.problemDescription.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }

        MainUiState(
            session = session,
            tools = allTools,
            filteredTools = filtered,
            searchQuery = query,
            selectedCategory = category,
            lastSyncTime = lastSync,
            isSyncing = syncing,
            syncStatusMessage = syncMsg,
            executionState = execState,
            showHistoryDialog = showHistory,
            authStatusMessage = authMsg,
            isAuthLoading = authLoading,
            otpSentForEmail = otpEmail
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    init {
        viewModelScope.launch {
            toolRepo.syncTools()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSelectCategory(category: ToolCategory) {
        _selectedCategory.value = category
    }

    fun onSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Synchronizing recipes with Mahi Cloud..."
            val result = toolRepo.syncTools()
            _isSyncing.value = false
            if (result.isSuccess) {
                _syncStatusMessage.value = "Synchronized ${result.getOrThrow()} repair tools."
            } else {
                _syncStatusMessage.value = result.exceptionOrNull()?.message ?: "Sync completed."
            }
        }
    }

    fun onRunTool(tool: RepairTool) {
        viewModelScope.launch {
            val payloadResult = toolRepo.getSignedPayloadAndSignature(tool.id)
            if (payloadResult.isFailure) {
                _syncStatusMessage.value = payloadResult.exceptionOrNull()?.message ?: "Recipe not found."
                return@launch
            }

            val (signedPayload, signature) = payloadResult.getOrThrow()
            val currentEmail = uiState.value.session.email.ifBlank { "friend@mahirepair.local" }

            val report = engine.executeRecipe(
                tool = tool,
                rawSignedPayload = signedPayload,
                signatureBase64 = signature,
                userEmail = currentEmail
            )

            toolRepo.recordRepairRun(report)
        }
    }

    fun onSubmitUserAction(approved: Boolean) {
        engine.submitUserActionResponse(approved)
    }

    fun onDismissExecution() {
        engine.reset()
    }

    fun setShowHistoryDialog(show: Boolean) {
        _showHistoryDialog.value = show
    }

    fun getHistoryFlow() = historyRepo.getHistoryFlow(uiState.value.session.email.ifBlank { "friend@mahirepair.local" })

    fun onRequestEmailOtp(email: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authStatusMessage.value = null
            val result = authRepo.requestEmailOtp(email)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                _otpSentForEmail.value = email
                _authStatusMessage.value = result.getOrThrow()
            } else {
                _authStatusMessage.value = result.exceptionOrNull()?.message ?: "Failed to send OTP"
            }
        }
    }

    fun onVerifyEmailOtp(otp: String) {
        val email = _otpSentForEmail.value ?: return
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authStatusMessage.value = null
            val result = authRepo.verifyEmailOtp(email, otp)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                _authStatusMessage.value = "Welcome to Mahi Repair!"
                _otpSentForEmail.value = null
                toolRepo.syncTools()
            } else {
                _authStatusMessage.value = result.exceptionOrNull()?.message ?: "Verification failed"
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            authRepo.signOut()
            _otpSentForEmail.value = null
            _authStatusMessage.value = "Signed out successfully."
        }
    }

    fun onClearStatusMessage() {
        _syncStatusMessage.value = null
        _authStatusMessage.value = null
    }
}
