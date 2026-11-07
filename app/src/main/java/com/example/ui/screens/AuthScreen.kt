package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainUiState
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiCardStroke
import com.example.ui.theme.MahiCardSurface
import com.example.ui.theme.MahiAccent
import com.example.ui.theme.MahiAccentDim
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary

@Composable
fun AuthScreen(
    state: MainUiState,
    onRequestOtp: (String) -> Unit,
    onVerifyOtp: (String) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MahiBlack)
            .padding(24.dp)
            .testTag("auth_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo
            Surface(
                shape = CircleShape,
                color = Color(0xFF161820),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MahiAccent.copy(alpha = 0.6f)),
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                        contentDescription = "Mahi Repair Logo",
                        modifier = Modifier.size(70.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Mahi Repair",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MahiTextPrimary,
                    fontSize = 28.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Private repair access",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MahiAccent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1B1D25),
                border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MahiTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Private Toolkit for Invited Family & Friends",
                        color = MahiTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Auth Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    if (state.otpSentForEmail == null) {
                        // Email input state
                        Text(
                            text = "Sign In with Invitation",
                            color = MahiTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter the email address invited by the Owner to receive a one-time access code.",
                            color = MahiTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Invited Email Address") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MahiTextSecondary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MahiTextPrimary,
                                unfocusedTextColor = MahiTextPrimary,
                                focusedBorderColor = MahiAccent,
                                unfocusedBorderColor = MahiCardStroke,
                                focusedContainerColor = Color(0xFF13141B),
                                unfocusedContainerColor = Color(0xFF13141B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input")
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { onRequestOtp(emailInput) },
                            enabled = emailInput.isNotBlank() && !state.isAuthLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MahiAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("send_otp_button")
                        ) {
                            if (state.isAuthLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = "SEND ACCESS CODE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    } else {
                        // OTP verification state
                        Text(
                            text = "Enter 6-Digit Code",
                            color = MahiTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "We sent an access code to ${state.otpSentForEmail}.",
                            color = MahiTextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { if (it.length <= 6) otpInput = it },
                            label = { Text("6-Digit Code") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = MahiTextSecondary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MahiTextPrimary,
                                unfocusedTextColor = MahiTextPrimary,
                                focusedBorderColor = MahiAccent,
                                unfocusedBorderColor = MahiCardStroke,
                                focusedContainerColor = Color(0xFF13141B),
                                unfocusedContainerColor = Color(0xFF13141B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_input")
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { onVerifyOtp(otpInput) },
                            enabled = otpInput.length == 6 && !state.isAuthLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MahiAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("verify_otp_button")
                        ) {
                            if (state.isAuthLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = "VERIFY & CONTINUE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { onRequestOtp(state.otpSentForEmail ?: emailInput) },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MahiCardStroke),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resend Code", color = MahiTextSecondary, fontSize = 12.sp)
                        }
                    }

                    // Status / error notification
                    AnimatedVisibility(visible = state.authStatusMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.authStatusMessage ?: "",
                            color = if (listOf("error", "failed", "unable", "denied", "expired").any { state.authStatusMessage?.contains(it, ignoreCase = true) == true }) MahiRedPrimary else MahiTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

        }
    }
}
