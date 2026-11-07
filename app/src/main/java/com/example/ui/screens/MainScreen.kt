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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.device.DeviceIntelligence
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
    onSignOut: () -> Unit = {}
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.session.email,
                    color = MahiTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(26.dp))

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

                Spacer(modifier = Modifier.height(18.dp))

                DeviceDiagnosticsCard(isDeviceRegistered = state.isDeviceRegistered)

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Text("SIGN OUT", color = MahiTextSecondary, fontSize = 12.sp)
                }
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
        onDismiss = onDismissExecution
    )
}


@Composable
private fun DeviceDiagnosticsCard(isDeviceRegistered: Boolean) {
    val context = LocalContext.current
    var snapshot by remember(context) { mutableStateOf(DeviceIntelligence.collect(context)) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191B23)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30323D))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("THIS DEVICE", color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        if (isDeviceRegistered) "Device ID saved; online revalidation required"
                        else "Server registration: pending",
                        color = MahiTextSecondary,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = { snapshot = DeviceIntelligence.collect(context) }) {
                    Text("REFRESH", color = MahiRedPrimary, fontSize = 10.sp)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "HIDE" else "DETAILS", color = MahiTextSecondary, fontSize = 10.sp)
                }
            }
            Text(
                text = "${snapshot.manufacturer} ${snapshot.model} · Android ${snapshot.androidVersion} (API ${snapshot.sdkInt})",
                color = MahiTextSecondary,
                fontSize = 11.sp
            )
            if (expanded) {
                DeviceDiagnosticLine("Build", snapshot.buildDisplay)
                DeviceDiagnosticLine("CPU ABI", snapshot.cpuAbis.takeIf { it.isNotEmpty() }?.joinToString() ?: "Unavailable")
                DeviceDiagnosticLine(
                    "RAM",
                    "${DeviceIntelligence.formatBytes(snapshot.availableRamBytes)} available / ${DeviceIntelligence.formatBytes(snapshot.totalRamBytes)} total"
                )
                DeviceDiagnosticLine(
                    "Storage",
                    "${DeviceIntelligence.formatBytes(snapshot.availableStorageBytes)} available / ${DeviceIntelligence.formatBytes(snapshot.totalStorageBytes)} total"
                )
                DeviceDiagnosticLine("Battery", snapshot.batteryPercent?.let { "$it%" } ?: "Unavailable")
                DeviceDiagnosticLine(
                    "Display",
                    if (snapshot.displayWidthPx != null && snapshot.displayHeightPx != null) {
                        "${snapshot.displayWidthPx} × ${snapshot.displayHeightPx} px (${snapshot.densityDpi ?: "?"} dpi)"
                    } else "Unavailable"
                )
                DeviceDiagnosticLine(
                    "Network",
                    "${snapshot.networkTransport} · ${if (snapshot.networkValidated) "Internet verified" else "Not validated"}"
                )
                DeviceDiagnosticLine("App version", snapshot.appVersion)
                DeviceDiagnosticLine(
                    "Hardware",
                    snapshot.hardwareCapabilities.takeIf { it.isNotEmpty() }?.joinToString() ?: "No optional capabilities reported"
                )
                Text(
                    "Diagnostics stay on this device; device registration sends only the minimal metadata required by the service.",
                    color = MahiTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DeviceDiagnosticLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = MahiTextMuted, fontSize = 11.sp, modifier = Modifier.width(76.dp))
        Text(value, color = MahiTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}
