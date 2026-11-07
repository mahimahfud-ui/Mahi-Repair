package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MahiRepairApplication
import com.example.BuildConfig
import com.example.security.VerifiedAppUpdateRelease
import com.example.data.local.UserSession
import com.example.domain.engine.ExecutionEvent
import com.example.domain.models.RepairTool
import com.example.domain.models.RepairRunReport
import com.example.security.InvitationLinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val isInstalling: Boolean = false,
    val release: VerifiedAppUpdateRelease? = null,
    val message: String? = null,
    val error: String? = null
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
    val isDeviceRegistered: Boolean = false,
    val appUpdate: AppUpdateUiState = AppUpdateUiState()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MahiRepairApplication
    private val authRepo = app.authRepository
    private val toolRepo = app.toolRepository
    private val engine = app.recipeExecutionEngine
    private val appUpdateRepository = app.appUpdateRepository

    private val _isToolsLoading = MutableStateFlow(true)
    private val _canRetryAvailability = MutableStateFlow(false)
    private val _isPreparingRepair = MutableStateFlow(false)
    private val _repairStatusMessage = MutableStateFlow<String?>(null)
    private val _isToolPickerOpen = MutableStateFlow(false)
    private val _authStatusMessage = MutableStateFlow<String?>(null)
    private val _isAuthLoading = MutableStateFlow(false)
    private val _otpSentForEmail = MutableStateFlow<String?>(null)
    private val _appUpdate = MutableStateFlow(AppUpdateUiState())
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
        app.preferencesManager.deviceIdFlow,
        _appUpdate
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
            isDeviceRegistered = !(values[11] as String?).isNullOrBlank(),
            appUpdate = values[12] as AppUpdateUiState
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
                _repairStatusMessage.value = if (uploadResult.isSuccess) {
                    "Repair completed."
                } else {
                    "The repair finished, but its report could not be sent. Check your connection."
                }
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

    fun onSubmitUserAction(approved: Boolean) {
        engine.submitUserActionResponse(approved)
    }

    fun onDismissExecution() {
        engine.reset()
    }

    fun onCheckForAppUpdates() {
        if (_appUpdate.value.isChecking || _appUpdate.value.isInstalling) return
        _appUpdate.value = AppUpdateUiState(isChecking = true)
        viewModelScope.launch {
            val result = appUpdateRepository.checkForUpdate()
            if (result.isFailure) {
                _appUpdate.value = AppUpdateUiState(
                    error = result.exceptionOrNull()?.message
                        ?: "The app-update service could not be reached."
                )
                return@launch
            }

            val release = result.getOrNull()
            val installedVersion = BuildConfig.VERSION_CODE.toLong()
            val message = when {
                release == null -> "No signed stable release is currently published."
                release.isNativeUpdateAvailable(installedVersion) ->
                    "A signed Android update is available."
                release.requiresUpdate(installedVersion) ->
                    "This app version is below the minimum supported version, but no compatible APK is available. Contact the Owner."
                release.contentPackageCount > 0 ->
                    "A signed content release is available. This app build does not activate content packages yet."
                else -> "Mahi Repair is up to date."
            }
            _appUpdate.value = AppUpdateUiState(release = release, message = message)
        }
    }

    fun onInstallAppUpdate() {
        val release = _appUpdate.value.release ?: return
        if (_appUpdate.value.isChecking || _appUpdate.value.isInstalling ||
            !release.isNativeUpdateAvailable(BuildConfig.VERSION_CODE.toLong())
        ) return

        _appUpdate.value = _appUpdate.value.copy(isInstalling = true, error = null, message = null)
        viewModelScope.launch {
            val result = appUpdateRepository.downloadAndPromptInstall(release)
            _appUpdate.value = _appUpdate.value.copy(
                isInstalling = false,
                message = result.getOrNull(),
                error = result.exceptionOrNull()?.message
                    ?: if (result.isFailure) "The update could not be installed." else null
            )
        }
    }

    fun onSignOut() {
        if (_isAuthLoading.value) return
        viewModelScope.launch {
            _isAuthLoading.value = true
            val remoteSignOutFailure = runCatching { authRepo.signOut() }.exceptionOrNull()
            val catalogClearFailure = runCatching { toolRepo.clearLocalCatalog() }.exceptionOrNull()

            _otpSentForEmail.value = null
            _appUpdate.value = AppUpdateUiState()
            _repairStatusMessage.value = null
            _authStatusMessage.value = when {
                catalogClearFailure != null ->
                    "Signed out, but local repair data could not be cleared. Reopen the app and retry."
                remoteSignOutFailure != null ->
                    "Signed out on this device. Server-side sign-out could not be confirmed."
                else -> null
            }
            _isAuthLoading.value = false
        }
    }

    fun onInvitationLink(uri: Uri?) {
        if (uri == null || uri.scheme != "mahirepair" || uri.host != "invite") return
        val tokenHash = InvitationLinkParser.parse(uri)
        if (tokenHash == null) {
            _authStatusMessage.value =
                "This invitation link is invalid. Enter your invited email to request a new access code."
            return
        }
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authStatusMessage.value = null
            try {
                val result = authRepo.verifyInvitationToken(tokenHash)
                if (result.isSuccess) {
                    _otpSentForEmail.value = null
                    _authStatusMessage.value = "Invitation verified. Your device will now be registered securely."
                } else {
                    _authStatusMessage.value = result.exceptionOrNull()?.message
                        ?: "Invitation verification failed. Enter your invited email to request a new access code."
                }
            } finally {
                _isAuthLoading.value = false
            }
        }
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
