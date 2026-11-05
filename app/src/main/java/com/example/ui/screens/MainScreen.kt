package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainUiState
import com.example.ui.components.ExecutionDialog
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary

@Composable
fun MainScreen(
    state: MainUiState,
    onRunRepair: () -> Unit,
    onRetryAvailability: () -> Unit,
    onSubmitUserAction: (Boolean) -> Unit,
    onDismissExecution: () -> Unit
) {
    val canRun = state.primaryTool != null &&
        !state.isPrimaryToolLoading &&
        !state.isPreparingRepair
    val canRetry = state.primaryTool == null &&
        state.canRetryAvailability &&
        !state.isPrimaryToolLoading &&
        !state.isPreparingRepair

    Scaffold(containerColor = MahiBlack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF161820),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MahiRedPrimary.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_logo),
                            contentDescription = "Mahi Repair",
                            modifier = Modifier.size(70.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "MAHI REPAIR",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = MahiTextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                )

                Spacer(modifier = Modifier.height(38.dp))

                Button(
                    onClick = {
                        if (state.primaryTool != null) onRunRepair()
                        else onRetryAvailability()
                    },
                    enabled = canRun || canRetry,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MahiRedPrimary,
                        disabledContainerColor = MahiRedPrimary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("run_repair_button")
                ) {
                    if (state.isPrimaryToolLoading || state.isPreparingRepair) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(21.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "RUN REPAIR",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val status = when {
                    state.isPrimaryToolLoading -> "Checking repair availability..."
                    state.isPreparingRepair -> "Preparing your repair..."
                    state.primaryTool != null -> state.repairStatusMessage ?: "Ready when you are."
                    else -> state.repairStatusMessage ?: "Run Repair is not available yet."
                }

                Text(
                    text = status,
                    color = if (state.primaryTool == null && !state.isPrimaryToolLoading) {
                        MahiTextMuted
                    } else {
                        MahiTextSecondary
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("repair_status")
                )
            }
        }
    }

    ExecutionDialog(
        executionEvent = state.executionState,
        onSubmitUserAction = onSubmitUserAction,
        onDismiss = onDismissExecution
    )
}
