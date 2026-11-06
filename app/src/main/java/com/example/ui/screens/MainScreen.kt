package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onSelectTool: (String) -> Unit,
    onDismissToolPicker: () -> Unit,
    onSubmitUserAction: (Boolean) -> Unit,
    onDismissExecution: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onCancelPermission: () -> Unit
) {
    val canRun = state.tools.isNotEmpty() &&
        !state.isToolsLoading &&
        !state.isPreparingRepair
    val canRetry = state.tools.isEmpty() &&
        state.canRetryAvailability &&
        !state.isToolsLoading &&
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
                        if (state.tools.isNotEmpty()) onRunRepair() else onRetryAvailability()
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
                    if (state.isToolsLoading || state.isPreparingRepair) {
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
                    state.isToolsLoading -> "Checking available repairs..."
                    state.isPreparingRepair -> "Preparing your repair..."
                    state.tools.isNotEmpty() -> state.repairStatusMessage
                        ?: "${state.tools.size} repair tools available."
                    else -> state.repairStatusMessage ?: "No repair tools are currently available."
                }

                Text(
                    text = status,
                    color = if (state.tools.isEmpty() && !state.isToolsLoading) {
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

    if (state.isToolPickerOpen) {
        AlertDialog(
            onDismissRequest = onDismissToolPicker,
            title = {
                Text(
                    text = "Choose a repair",
                    color = MahiTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(state.tools, key = { it.id }) { tool ->
                        TextButton(
                            onClick = { onSelectTool(tool.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = tool.name,
                                    color = MahiTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${tool.category.title} · v${tool.version}",
                                    color = MahiTextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        text = tool.description,
                                        color = MahiTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissToolPicker) {
                    Text("CLOSE", color = MahiRedPrimary)
                }
            },
            containerColor = Color(0xFF191B23),
            titleContentColor = MahiTextPrimary,
            textContentColor = MahiTextSecondary
        )
    }

    ExecutionDialog(
        executionEvent = state.executionState,
        onSubmitUserAction = onSubmitUserAction,
        onDismiss = onDismissExecution,
        onRequestPermission = onRequestPermission,
        onCancelPermission = onCancelPermission
    )
}
