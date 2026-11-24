package com.example.ui

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MahiRepairApplication
import com.example.BuildConfig
import com.example.data.local.UserSession
import com.example.data.local.RepairHistoryEntity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.domain.engine.ExecutionEvent
import com.example.domain.engine.PermissionExecutionResult
import com.example.security.RuntimePermissionContract
import com.example.domain.models.RepairTool
import com.example.domain.models.RepairRunReport
import com.example.security.VerifiedAppUpdateRelease
import com.example.security.VerifiedRemoteAnnouncement
import com.example.security.VerifiedRemoteConfig
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

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val isInstalling: Boolean = false,
    val release: VerifiedAppUpdateRelease? = null,
    val message: String? = null,
    val error: String? = null,
    val isApplyingDataUpdate: Boolean = false,
    val downloadProgress: Float? = null
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
    val history: List<RepairHistoryEntity> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val pendingReportCount: Int = 0,
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val remoteAnnouncement: VerifiedRemoteAnnouncement? = null
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
    private val _history = MutableStateFlow<List<RepairHistoryEntity>>(emptyList())
    private val _lastSyncTimestamp = MutableStateFlow(0L)
    private val _pendingReportCount = MutableStateFlow(0)
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
        _history,
        _lastSyncTimestamp,
        _pendingReportCount,
        _appUpdate,
        app.appUpdateContentInstaller.activeRemoteConfigFlow
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
            history = values[10] as List<RepairHistoryEntity>,
            lastSyncTimestamp = values[11] as Long,
            pendingReportCount = values[12] as Int,
            appUpdate = values[13] as AppUpdateUiState,
            featureFlags = (values[14] as VerifiedRemoteConfig?)?.featureFlags.orEmpty(),
            remoteAnnouncement = (values[14] as VerifiedRemoteConfig?)?.announcement
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    init {
        observeAppUpdateEvents()
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
                    syncDeviceIntelligence()
                    onCheckForAppUpdates()
                } else {
                    _isToolsLoading.value = false
                    _canRetryAvailability.value = false
                    _isToolPickerOpen.value = false
                    _repairStatusMessage.value = null
                }
            }
        }
    }

    private fun observeAppUpdateEvents() {
        viewModelScope.launch {
            app.appUpdateEvents.collectLatest {
                if (authRepo.sessionFlow.first().isAuthenticated) {
                    onCheckForAppUpdates()
                }
            }
        }
    }

    private fun syncDeviceIntelligence() {
        viewModelScope.launch {
            try {
                val engine = com.example.domain.intelligence.DeviceIntelligenceEngine(getApplication())
                val (hardwareProfile, fieldAvailability) = engine.collectHardwareProfile()
                val telemetry = engine.collectDynamicTelemetry()
                val identity = toolRepo.getRealtimeIdentity().getOrElse { cause ->
                    Log.w("MahiDeviceProfile", "Device profile upload skipped because device authorization failed.", cause)
                    return@launch
                }

                val upload = com.example.data.remote.DeviceIntelligenceApiClient().upload(
                    accessToken = identity.accessToken,
                    deviceId = identity.deviceId,
                    deviceLabel = "${hardwareProfile.brand} ${hardwareProfile.model}".trim().take(100),
                    appVersion = BuildConfig.VERSION_NAME,
                    androidVersion = Build.VERSION.RELEASE,
                    androidApi = Build.VERSION.SDK_INT,
                    hardwareProfile = hardwareProfile,
                    telemetry = telemetry,
                    fieldAvailability = fieldAvailability
                )
                upload.exceptionOrNull()?.let { cause ->
                    Log.w("MahiDeviceProfile", "Device profile upload failed; local repair features remain available.", cause)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("MahiDeviceProfile", "Device profile collection or upload failed.", error)
            }
        }
    }


    fun onCheckForAppUpdates() {
        if (_appUpdate.value.isChecking || _appUpdate.value.isInstalling) return
        _appUpdate.value = AppUpdateUiState(isChecking = true)
        viewModelScope.launch {
            val result = app.appUpdateRepository.checkForUpdate()
            if (result.isFailure) {
                _appUpdate.value = AppUpdateUiState(
                    error = result.exceptionOrNull()?.message ?: "The app-update service could not be reached."
                )
                return@launch
            }
            val release = result.getOrNull()
            val installedVersion = BuildConfig.VERSION_CODE.toLong()
            val message = when {
                release == null -> "No signed stable release is currently published."
                release.isNativeUpdateAvailable(installedVersion) && release.hasDataUpdate -> "A signed Android and data update are available."
                release.isNativeUpdateAvailable(installedVersion) -> "A signed Android update is available."
                release.hasDataUpdate -> "A signed configuration/content update is available."
                release.requiresUpdate(installedVersion) -> "This app version is below the minimum supported version, but no compatible APK is available. Contact the Owner."
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
            val result = app.appUpdateRepository.downloadAndPromptInstall(release)
            _appUpdate.value = _appUpdate.value.copy(
                isInstalling = false,
                message = result.getOrNull(),
                error = result.exceptionOrNull()?.message ?: if (result.isFailure) "The update could not be installed." else null
            )
        }
    }

    fun onApplyDataUpdate() {
        val release = _appUpdate.value.release ?: return
        if (!release.hasDataUpdate || _appUpdate.value.isChecking ||
            _appUpdate.value.isInstalling || _appUpdate.value.isApplyingDataUpdate
        ) return
        _appUpdate.value = _appUpdate.value.copy(
            isApplyingDataUpdate = true,
            downloadProgress = 0f,
            message = null,
            error = null
        )
        viewModelScope.launch {
            val result = app.appUpdateRepository.applyDataUpdate(release) { progress ->
                _appUpdate.value = _appUpdate.value.copy(downloadProgress = progress.coerceIn(0f, 1f))
            }
            _appUpdate.value = _appUpdate.value.copy(
                isApplyingDataUpdate = false,
                downloadProgress = null,
                message = result.getOrNull(),
                error = result.exceptionOrNull()?.message ?: if (result.isFailure) "The signed data update could not be activated." else null
            )
            if (result.isSuccess) refreshTools()
        }
    }

    fun onRollbackDataUpdate() {
        if (_appUpdate.value.isApplyingDataUpdate) return
        viewModelScope.launch {
            val result = app.appUpdateRepository.rollbackDataUpdate()
            _appUpdate.value = _appUpdate.value.copy(
                message = result.getOrNull(),
                error = result.exceptionOrNull()?.message
            )
            if (result.isSuccess) refreshTools()
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
            _appUpdate.value = AppUpdateUiState()
                _authStatusMessage.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // AuthRepository clears the local credentials even when remote sign-out fails.
            }
        }
    }

    fun onSignInWithMemberCredentials(memberCode: String, pin: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authStatusMessage.value = null
            val result = try {
                authRepo.signInWithMemberCredentials(memberCode, pin)
            } catch (cancelled: CancellationException) {
                _isAuthLoading.value = false
                throw cancelled
            } catch (error: Exception) {
                _isAuthLoading.value = false
                _authStatusMessage.value = error.message ?: "Sign-in failed."
                return@launch
            }
            _isAuthLoading.value = false
            if (result.isSuccess) {
                toolRepo.clearAccessDeniedMessage()
                toolRepo.enqueuePendingReportUploads()
                _authStatusMessage.value = "Welcome to Mahi Repair!"
            } else {
                _authStatusMessage.value = result.exceptionOrNull()?.message ?: "Member ID or PIN is incorrect."
            }
        }
    }
}
