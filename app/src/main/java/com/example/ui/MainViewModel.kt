package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MahiRepairApplication
import com.example.data.local.UserSession
import com.example.data.local.RepairHistoryEntity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.domain.engine.ExecutionEvent
import com.example.domain.engine.PermissionExecutionResult
import com.example.security.RuntimePermissionContract
import com.example.domain.models.RepairTool
import com.example.domain.models.RepairRunReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class PermissionLaunchRequest(
    val permissionName: String,
    val androidPermission: String,
    val wasRequestedBefore: Boolean
)

data class MainUiState(
    val session: UserSession = UserSession("", "", "", false),
    val tools: List<RepairTool> = emptyList(),
    val isToolsLoading: Boolean = true,
    val canRetryAvailability: Boolean = false,
    val isPreparingRepair: Boolean = false,
    val repairStatusMessage: String? = null,
    val isToolPickerOpen: Boolean = false,
    val executionState: ExecutionEvent = ExecutionEvent.Idle,
    val authStatusMessage: String? = null,
    val isAuthLoading: Boolean = false,
    val otpSentForEmail: String? = null,
    val history: List<RepairHistoryEntity> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val pendingReportCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MahiRepairApplication
    private val authRepo = app.authRepository
    private val toolRepo = app.toolRepository
    private val engine = app.recipeExecutionEngine

    private val _isToolsLoading = MutableStateFlow(true)
    private val _canRetryAvailability = MutableStateFlow(false)
    private val _isPreparingRepair = MutableStateFlow(false)
    private val _repairStatusMessage = MutableStateFlow<String?>(null)
    private val _isToolPickerOpen = MutableStateFlow(false)
    private val _authStatusMessage = MutableStateFlow<String?>(null)
    private val _isAuthLoading = MutableStateFlow(false)
    private val _otpSentForEmail = MutableStateFlow<String?>(null)
    private val _history = MutableStateFlow<List<RepairHistoryEntity>>(emptyList())
    private val _lastSyncTimestamp = MutableStateFlow(0L)
    private val _pendingReportCount = MutableStateFlow(0)
    private val executionMutex = Mutex()

    val uiState: StateFlow<MainUiState> = combine(
        authRepo.sessionFlow,
        toolRepo.toolsFlow,
        _isToolsLoading,
        _canRetryAvailability,
        _isPreparingRepair,
        _repairStatusMessage,
        _isToolPickerOpen,
        engine.engineState,
        _authStatusMessage,
        _isAuthLoading,
        _otpSentForEmail,
        _history,
        _lastSyncTimestamp,
        _pendingReportCount
    ) { values ->
        MainUiState(
            session = values[0] as UserSession,
            tools = values[1] as List<RepairTool>,
            isToolsLoading = values[2] as Boolean,
            canRetryAvailability = values[3] as Boolean,
            isPreparingRepair = values[4] as Boolean,
            repairStatusMessage = values[5] as String?,
            isToolPickerOpen = values[6] as Boolean,
            executionState = values[7] as ExecutionEvent,
            authStatusMessage = values[8] as String?,
            isAuthLoading = values[9] as Boolean,
            otpSentForEmail = values[10] as String?,
            history = values[11] as List<RepairHistoryEntity>,
            lastSyncTimestamp = values[12] as Long,
            pendingReportCount = values[13] as Int
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    init {
        viewModelScope.launch {
            toolRepo.lastSyncFlow.collect { timestamp -> _lastSyncTimestamp.value = timestamp }
        }
        viewModelScope.launch {
            toolRepo.pendingReportCountFlow.collect { count -> _pendingReportCount.value = count }
        }
        viewModelScope.launch {
            authRepo.sessionFlow.collectLatest { session ->
                if (session.isAuthenticated) {
                    app.historyRepository.getHistoryFlow(session.email).collect { records ->
                        _history.value = records
                    }
                } else {
                    _history.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            toolRepo.accessDeniedMessage.collectLatest { message ->
                if (!message.isNullOrBlank()) _authStatusMessage.value = message
            }
        }
        viewModelScope.launch {
            authRepo.sessionFlow.collect { session ->
                if (session.isAuthenticated) {
                    refreshTools()
                } else {
                    _isToolsLoading.value = false
                    _canRetryAvailability.value = false
                    _isToolPickerOpen.value = false
                    _repairStatusMessage.value = null
                }
            }
        }
    }

    fun onRetryAvailability() {
        if (_isToolsLoading.value || _isPreparingRepair.value) return
        viewModelScope.launch { refreshTools() }
    }

    private suspend fun refreshTools() {
        _isToolsLoading.value = true
        _canRetryAvailability.value = false
        _repairStatusMessage.value = null

        val result = toolRepo.syncTools()
        _isToolsLoading.value = false

        if (result.isSuccess) {
            val count = result.getOrThrow()
            _canRetryAvailability.value = false
            _repairStatusMessage.value = when (count) {
                0 -> "No repair tools are currently available."
                1 -> "1 repair tool is ready."
                else -> "$count repair tools are ready."
            }
        } else {
            _canRetryAvailability.value = true
            _repairStatusMessage.value =
                "We couldn't refresh your repair list. Check your connection and try again."
        }
    }

    /** RUN REPAIR opens the live, server-authorized catalogue instead of executing a fixed recipe. */
    fun onRunRepair() {
        if (_isToolsLoading.value || _isPreparingRepair.value) return
        viewModelScope.launch {
            val tools = toolRepo.toolsFlow.first()
            if (tools.isEmpty()) {
                refreshTools()
            } else {
                _isToolPickerOpen.value = true
            }
        }
    }

    fun onDismissToolPicker() {
        _isToolPickerOpen.value = false
    }

    fun onSelectTool(toolId: String) {
        if (_isToolsLoading.value || _isPreparingRepair.value || !executionMutex.tryLock()) return
        _isToolPickerOpen.value = false

        viewModelScope.launch {
            _isPreparingRepair.value = true
            _repairStatusMessage.value = "Preparing your repair..."

            try {
                val tool = toolRepo.toolsFlow.first().firstOrNull { it.id == toolId }
                    ?: throw IllegalStateException("This repair is no longer in your available list.")

                val currentSession = authRepo.getValidSession().getOrElse { cause ->
                    throw IllegalStateException("Your session has expired. Please sign in again.", cause)
                }
                if (currentSession.email.isBlank()) {
                    throw IllegalStateException("Your session has expired. Please sign in again.")
                }

                // Never execute the cached Room entry. The Edge Function rechecks membership,
                // device ownership, publication state and current version on every run.
                val payload = toolRepo.getSignedPayloadAndSignature(tool.id).getOrElse { cause ->
                    throw IllegalStateException("This repair is no longer authorized for this device.", cause)
                }

                val report: RepairRunReport = engine.executeRecipe(
                    tool = tool,
                    rawSignedPayload = payload.first,
                    signatureBase64 = payload.second,
                    userEmail = currentSession.email
                )

                val uploadResult = toolRepo.recordRepairRun(report)
                _pendingReportCount.value = toolRepo.pendingReportCount()
                _repairStatusMessage.value = if (uploadResult.isSuccess) {
                    "Repair completed. Report saved on this device and queued for secure upload."
                } else {
                    "The repair finished, but its report could not be saved locally."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _canRetryAvailability.value = true
                _repairStatusMessage.value = when {
                    error.message?.contains("session", ignoreCase = true) == true ->
                        "Your session expired. Please sign in again."
                    else -> "This repair is no longer available. Refresh the repair list and try again."
                }
                // A 401/403 means the local list may be stale. The repository clears it
                // when the server rejects the catalogue request; never trust the cache to execute.
            } finally {
                _isPreparingRepair.value = false
                executionMutex.unlock()
            }
        }
    }

    suspend fun prepareRuntimePermissionRequest(permissionName: String): PermissionLaunchRequest? {
        val prompt = engine.engineState.value as? ExecutionEvent.PromptUserAction ?: return null
        if (prompt.type != com.example.domain.actions.UserActionType.PERMISSION ||
            prompt.payload["permission"] != permissionName
        ) return null

        val spec = RuntimePermissionContract.resolve(permissionName) ?: return null
        val androidPermission = spec.platformPermission ?: run {
            engine.submitPermissionResult(PermissionExecutionResult.GRANTED)
            return null
        }
        if (ContextCompat.checkSelfPermission(getApplication(), androidPermission) == PackageManager.PERMISSION_GRANTED) {
            engine.submitPermissionResult(PermissionExecutionResult.GRANTED)
            return null
        }

        val wasRequestedBefore = app.preferencesManager.wasRuntimePermissionRequested(permissionName)
        app.preferencesManager.markRuntimePermissionRequested(permissionName)
        return PermissionLaunchRequest(permissionName, androidPermission, wasRequestedBefore)
    }

    fun onRuntimePermissionResult(
        permissionName: String,
        granted: Boolean,
        permanentlyDenied: Boolean
    ) {
        val prompt = engine.engineState.value as? ExecutionEvent.PromptUserAction ?: return
        if (prompt.type != com.example.domain.actions.UserActionType.PERMISSION ||
            prompt.payload["permission"] != permissionName
        ) return
        val result = when {
            granted -> PermissionExecutionResult.GRANTED
            permanentlyDenied -> PermissionExecutionResult.PERMANENTLY_DENIED
            else -> PermissionExecutionResult.DENIED
        }
        engine.submitPermissionResult(result)
    }

    fun onCancelRuntimePermission() {
        engine.submitPermissionResult(PermissionExecutionResult.CANCELLED)
    }

    fun onSubmitUserAction(approved: Boolean) {
        engine.submitUserActionResponse(approved)
    }

    fun onDismissExecution() {
        engine.reset()
    }

    fun onSignOut() {
        viewModelScope.launch {
            try {
                authRepo.signOut()
                _otpSentForEmail.value = null
                _authStatusMessage.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // AuthRepository clears the local credentials even when remote sign-out fails.
            }
        }
    }

    fun onRequestEmailOtp(email: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authStatusMessage.value = null
            val result = try {
                authRepo.requestEmailOtp(email)
            } catch (cancelled: CancellationException) {
                _isAuthLoading.value = false
                throw cancelled
            }
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
            val result = try {
                authRepo.verifyEmailOtp(email, otp)
            } catch (cancelled: CancellationException) {
                _isAuthLoading.value = false
                throw cancelled
            }
            _isAuthLoading.value = false
            if (result.isSuccess) {
                toolRepo.clearAccessDeniedMessage()
                toolRepo.enqueuePendingReportUploads()
                _authStatusMessage.value = "Welcome to Mahi Repair!"
                _otpSentForEmail.value = null
            } else {
                _authStatusMessage.value = result.exceptionOrNull()?.message ?: "Verification failed"
            }
        }
    }
}
