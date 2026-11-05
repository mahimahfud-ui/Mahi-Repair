package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
            onDismissExecution = { viewModel.onDismissExecution() }
        )
    }
}
