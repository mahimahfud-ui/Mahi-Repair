package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MahiRepairApplication
import com.example.data.local.UserSession
import com.example.domain.engine.ExecutionEvent
import com.example.domain.models.RepairTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class MainUiState(
    val session: UserSession = UserSession("", "", "", false),
    val primaryTool: RepairTool? = null,
    val isPrimaryToolLoading: Boolean = true,
    val canRetryAvailability: Boolean = false,
    val isPreparingRepair: Boolean = false,
    val repairStatusMessage: String? = null,
    val executionState: ExecutionEvent = ExecutionEvent.Idle,
    val authStatusMessage: String? = null,
    val isAuthLoading: Boolean = false,
    val otpSentForEmail: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MahiRepairApplication
    private val authRepo = app.authRepository
    private val toolRepo = app.toolRepository
    private val engine = app.recipeExecutionEngine

    private val _isPrimaryToolLoading = MutableStateFlow(true)
    private val _canRetryAvailability = MutableStateFlow(false)
    private val _isPreparingRepair = MutableStateFlow(false)
    private val _repairStatusMessage = MutableStateFlow<String?>(null)
    private val _authStatusMessage = MutableStateFlow<String?>(null)
    private val _isAuthLoading = MutableStateFlow(false)
    private val _otpSentForEmail = MutableStateFlow<String?>(null)
    private val executionMutex = Mutex()

    val uiState: StateFlow<MainUiState> = combine(
        authRepo.sessionFlow,
        toolRepo.primaryRepairToolFlow,
        _isPrimaryToolLoading,
        _canRetryAvailability,
        _isPreparingRepair,
        _repairStatusMessage,
        engine.engineState,
        _authStatusMessage,
        _isAuthLoading,
        _otpSentForEmail
    ) { values ->
        MainUiState(
            session = values[0] as UserSession,
            primaryTool = values[1] as RepairTool?,
            isPrimaryToolLoading = values[2] as Boolean,
            canRetryAvailability = values[3] as Boolean,
            isPreparingRepair = values[4] as Boolean,
            repairStatusMessage = values[5] as String?,
            executionState = values[6] as ExecutionEvent,
            authStatusMessage = values[7] as String?,
            isAuthLoading = values[8] as Boolean,
            otpSentForEmail = values[9] as String?
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    init {
        viewModelScope.launch {
            authRepo.sessionFlow.collectLatest { session ->
                if (session.isAuthenticated) {
                    refreshPrimaryRepair()
                } else {
                    _isPrimaryToolLoading.value = false
                    _canRetryAvailability.value = false
                    _repairStatusMessage.value = null
                }
            }
        }
    }

    fun onRetryAvailability() {
        if (_isPrimaryToolLoading.value || _isPreparingRepair.value) return
        viewModelScope.launch { refreshPrimaryRepair() }
    }

    private suspend fun refreshPrimaryRepair() {
        _isPrimaryToolLoading.value = true
        _canRetryAvailability.value = false
        _repairStatusMessage.value = null

        val result = toolRepo.syncTools()
        _isPrimaryToolLoading.value = false

        if (result.isSuccess) {
            val available = result.getOrThrow() > 0
            _canRetryAvailability.value = false
            _repairStatusMessage.value = if (available) {
                "Your repair is ready."
            } else {
                "Run Repair is not available yet."
            }
        } else {
            _canRetryAvailability.value = true
            _repairStatusMessage.value =
                "We couldn't check repair availability. Check your connection and try again."
        }
    }

    fun onRunRepair() {
        if (_isPrimaryToolLoading.value || _isPreparingRepair.value) return
        if (!executionMutex.tryLock()) return

        viewModelScope.launch {
            _isPreparingRepair.value = true
            _repairStatusMessage.value = "Preparing your repair..."

            try {
                val tool = toolRepo.primaryRepairToolFlow.first()
                    ?: throw IllegalStateException("The primary repair is not currently available.")

                val currentSession = authRepo.getValidSession().getOrElse { cause ->
                    throw IllegalStateException("Your session has expired. Please sign in again.", cause)
                }
                if (currentSession.email.isBlank()) {
                    throw IllegalStateException("Your session has expired. Please sign in again.")
                }

                val payload = toolRepo.getSignedPayloadAndSignature(tool.id).getOrElse { cause ->
                    throw IllegalStateException("The repair is not currently authorized for this device.", cause)
                }

                val report = engine.executeRecipe(
                    tool = tool,
                    rawSignedPayload = payload.first,
                    signatureBase64 = payload.second,
                    userEmail = currentSession.email
                )

                val uploadResult = toolRepo.recordRepairRun(report)
                _repairStatusMessage.value = if (uploadResult.isSuccess) {
                    null
                } else {
                    "The repair run finished, but its report could not be sent. Check your connection."
                }
            } catch (error: Exception) {
                _canRetryAvailability.value = true
                _repairStatusMessage.value = when {
                    error.message?.contains("session", ignoreCase = true) == true ->
                        "Your session expired. Please sign in again."
                    else -> "The repair could not be started. Check your connection and try again."
                }
            } finally {
                _isPreparingRepair.value = false
                executionMutex.unlock()
            }
        }
    }

    fun onSubmitUserAction(approved: Boolean) {
        engine.submitUserActionResponse(approved)
    }

    fun onDismissExecution() {
        engine.reset()
    }

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
            } else {
                _authStatusMessage.value = result.exceptionOrNull()?.message ?: "Verification failed"
            }
        }
    }
}
