package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiRepairTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MahiRepairTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MahiBlack
                ) {
                    MahiRepairApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as MahiRepairApplication).setAppForegrounded(true)
    }

    override fun onStop() {
        (application as MahiRepairApplication).setAppForegrounded(false)
        super.onStop()
    }
}

@Composable
fun MahiRepairApp(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionRequestInFlight = rememberSaveable { mutableStateOf(false) }
    val activePermissionName = rememberSaveable { mutableStateOf("") }
    val activePermissionWasRequestedBefore = rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val permissionName = activePermissionName.value
        if (permissionName.isNotBlank()) {
            val activity = context as? Activity
            val androidPermission = com.example.security.RuntimePermissionContract
                .resolve(permissionName)?.platformPermission
            val showRationale = activity != null && androidPermission != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, androidPermission)
            val permanentlyDenied = !granted &&
                activePermissionWasRequestedBefore.value &&
                !showRationale
            viewModel.onRuntimePermissionResult(permissionName, granted, permanentlyDenied)
        }
        activePermissionName.value = ""
        activePermissionWasRequestedBefore.value = false
        permissionRequestInFlight.value = false
    }

    if (!state.session.isAuthenticated) {
        AuthScreen(
            state = state,
            onRequestOtp = { viewModel.onRequestEmailOtp(it) },
            onVerifyOtp = { viewModel.onVerifyEmailOtp(it) }
        )
    } else {
        MainScreen(
            state = state,
            onRunRepair = { viewModel.onRunRepair() },
            onRetryAvailability = { viewModel.onRetryAvailability() },
            onSelectTool = { viewModel.onSelectTool(it) },
            onDismissToolPicker = { viewModel.onDismissToolPicker() },
            onSubmitUserAction = { viewModel.onSubmitUserAction(it) },
            onDismissExecution = { viewModel.onDismissExecution() },
            onRequestPermission = { permissionName ->
                if (!permissionRequestInFlight.value) {
                    permissionRequestInFlight.value = true
                    scope.launch {
                        val request = runCatching {
                            viewModel.prepareRuntimePermissionRequest(permissionName)
                        }.getOrNull()
                        if (request == null) {
                            permissionRequestInFlight.value = false
                        } else {
                            activePermissionName.value = request.permissionName
                            activePermissionWasRequestedBefore.value = request.wasRequestedBefore
                            try {
                                permissionLauncher.launch(request.androidPermission)
                            } catch (_: Exception) {
                                viewModel.onCancelRuntimePermission()
                                activePermissionName.value = ""
                                activePermissionWasRequestedBefore.value = false
                                permissionRequestInFlight.value = false
                            }
                        }
                    }
                }
            },
            onCancelPermission = { viewModel.onCancelRuntimePermission() }
        )
    }
}
