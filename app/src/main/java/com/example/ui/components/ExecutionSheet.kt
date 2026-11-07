package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.actions.UserActionType
import com.example.domain.engine.ExecutionEvent
import com.example.domain.models.RepairRunResult
import com.example.domain.models.StepStatus
import com.example.ui.theme.MahiCardStroke
import com.example.ui.theme.MahiCardSurface
import com.example.ui.theme.MahiRedDim
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiSuccessDim
import com.example.ui.theme.MahiSuccessGreen
import com.example.ui.theme.MahiSurfaceDark
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary
import com.example.ui.theme.MahiWarningAmber
import com.example.ui.theme.MahiWarningDim

@Composable
fun ExecutionDialog(
    executionEvent: ExecutionEvent,
    onSubmitUserAction: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (executionEvent is ExecutionEvent.Idle) return

    Dialog(
        onDismissRequest = {
            if (executionEvent is ExecutionEvent.Finished) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = executionEvent is ExecutionEvent.Finished,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .testTag("execution_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MahiSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MahiRedDim,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MahiRedPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Mahi Repair",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MahiTextPrimary
                                )
                            )
                            Text(
                                text = "Guided repair, step by step",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MahiTextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    if (executionEvent is ExecutionEvent.Finished) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MahiTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (executionEvent) {
                    is ExecutionEvent.VerifyingSignature -> {
                        VerifyingSignatureSection(toolName = executionEvent.toolName)
                    }
                    is ExecutionEvent.SignatureFailed -> {
                        SignatureFailedSection(reason = executionEvent.reason, onDismiss = onDismiss)
                    }
                    is ExecutionEvent.IncompatibleDevice -> {
                        IncompatibleSection(
                            req = executionEvent.requiredSdk,
                            cur = executionEvent.currentSdk,
                            onDismiss = onDismiss
                        )
                    }
                    is ExecutionEvent.Running -> {
                        RunningProgressSection(event = executionEvent)
                    }
                    is ExecutionEvent.PromptUserAction -> {
                        UserActionPromptSection(
                            event = executionEvent,
                            onConfirm = { onSubmitUserAction(true) },
                            onCancel = { onSubmitUserAction(false) }
                        )
                    }
                    is ExecutionEvent.Finished -> {
                        FinishedReportSection(report = executionEvent.report, onDismiss = onDismiss)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun VerifyingSignatureSection(toolName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MahiRedPrimary,
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Preparing repair...",
            color = MahiTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Checking the repair instructions for this device.",
            color = MahiTextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SignatureFailedSection(reason: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MahiRedDim,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MahiRedPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Repair could not start",
            color = MahiRedPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reason,
            color = MahiTextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = MahiRedPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun IncompatibleSection(req: Int, cur: Int, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MahiWarningAmber,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Device Incompatible",
            color = MahiTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This repair needs a newer version of Android and cannot run on this device.",
            color = MahiTextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = MahiCardStroke),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun RunningProgressSection(event: ExecutionEvent.Running) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = event.tool.name,
            color = MahiTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Step ${event.currentStepIndex + 1} of ${event.totalSteps}",
            color = MahiTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        val progress = if (event.totalSteps > 0) {
            (event.currentStepIndex + 1).toFloat() / event.totalSteps.toFloat()
        } else 0f

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MahiRedPrimary,
            trackColor = Color(0xFF222530)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // History of completed steps
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            event.stepRecords.forEach { record ->
                StepRecordItem(record = record)
            }

            // Current active step
            CurrentStepItem(step = event.currentStep)
        }
    }
}

@Composable
private fun StepRecordItem(record: com.example.domain.models.StepExecutionRecord) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (record.status) {
                StepStatus.COMPLETED -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MahiSuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                StepStatus.FAILED -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MahiRedPrimary,
                    modifier = Modifier.size(20.dp)
                )
                StepStatus.CANCELLED -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MahiTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                else -> Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MahiTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    color = MahiTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = record.message,
                    color = MahiTextSecondary,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${record.durationMs}ms",
                color = MahiTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun CurrentStepItem(step: com.example.domain.models.RecipeStep) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222530)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MahiRedPrimary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = MahiRedPrimary,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.name,
                    color = MahiTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = step.description.ifBlank { "Executing native diagnosis..." },
                    color = MahiRedPrimary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun UserActionPromptSection(
    event: ExecutionEvent.PromptUserAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Icon and Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MahiWarningDim,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (event.type == UserActionType.GUIDE) Icons.Default.Info else Icons.Default.Warning,
                        contentDescription = null,
                        tint = MahiWarningAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = event.title,
                color = MahiTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = event.message,
            color = MahiTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        // If step-by-step guidance exists
        if (event.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                event.steps.forEachIndexed { i, stepDesc ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF13141B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252834)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MahiRedPrimary,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${i + 1}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stepDesc,
                                color = MahiTextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Keep both actions easy to tap and readable on narrow phones.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 340.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("user_action_cancel")
                    ) {
                        Text(
                            text = "Skip / Cancel",
                            color = MahiTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MahiRedPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("user_action_confirm")
                    ) {
                        Text(
                            text = if (event.type == UserActionType.GUIDE) "I've Done This" else "Allow & Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("user_action_cancel")
                    ) {
                        Text("Skip / Cancel", color = MahiTextSecondary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MahiRedPrimary),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("user_action_confirm")
                    ) {
                        Text(
                            text = if (event.type == UserActionType.GUIDE) "I've Done This" else "Allow & Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinishedReportSection(
    report: com.example.domain.models.RepairRunReport,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Result Banner
        val (bannerColor, bannerBg, resultText, icon) = when (report.result) {
            RepairRunResult.SUCCESS -> Quad(
                MahiSuccessGreen,
                MahiSuccessDim,
                "REPAIR COMPLETED",
                Icons.Default.CheckCircle
            )
            RepairRunResult.PARTIAL -> Quad(
                MahiWarningAmber,
                MahiWarningDim,
                "PARTIAL RESULTS",
                Icons.Default.Warning
            )
            RepairRunResult.FAILED -> Quad(
                MahiRedPrimary,
                MahiRedDim,
                "REPAIR NOT COMPLETED",
                Icons.Default.Error
            )
            RepairRunResult.CANCELLED -> Quad(
                MahiTextMuted,
                Color(0xFF22242E),
                "REPAIR CANCELLED",
                Icons.Default.Close
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bannerBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = bannerColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = resultText,
                        color = bannerColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${report.toolName} (v${report.toolVersion})",
                        color = MahiTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Final Diagnosis
        Text(
            text = "Repair result",
            color = MahiTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = report.finalDiagnosis,
            color = MahiTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Steps execution breakdown
        Text(
            text = "Step Results (${report.stepRecords.size})",
            color = MahiTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.stepRecords.forEach { record ->
                StepRecordItem(record = record)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = MahiRedPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "DONE",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
