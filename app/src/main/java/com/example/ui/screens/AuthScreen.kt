package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainUiState
import com.example.ui.theme.MahiAccent
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiCardStroke
import com.example.ui.theme.MahiCardSurface
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary

@Composable
fun AuthScreen(state: MainUiState, onSignIn: (String, String) -> Unit) {
    var memberId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(MahiBlack).padding(24.dp).testTag("auth_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape, color = Color(0xFF161820),
                border = BorderStroke(1.5.dp, MahiAccent.copy(alpha = 0.6f)),
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = R.drawable.ic_launcher_logo), contentDescription = "Mahi Repair Logo", modifier = Modifier.size(70.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Mahi Repair", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MahiTextPrimary, fontSize = 28.sp))
            Spacer(Modifier.height(6.dp))
            Text("Private repair access", style = MaterialTheme.typography.bodyMedium.copy(color = MahiAccent, fontWeight = FontWeight.Medium, fontSize = 14.sp))
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF1B1D25), border = BorderStroke(1.dp, MahiCardStroke)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MahiTextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Private Toolkit for Invited Family & Friends", color = MahiTextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MahiCardSurface), border = BorderStroke(1.dp, MahiCardStroke), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Member Sign In", color = MahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Enter the Member ID and PIN provided by the Owner.", color = MahiTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = memberId,
                        onValueChange = { memberId = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(11) },
                        label = { Text("Member ID") }, leadingIcon = { Icon(Icons.Default.Key, null, tint = MahiTextSecondary) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MahiTextPrimary, unfocusedTextColor = MahiTextPrimary, focusedBorderColor = MahiAccent, unfocusedBorderColor = MahiCardStroke, focusedContainerColor = Color(0xFF13141B), unfocusedContainerColor = Color(0xFF13141B)),
                        modifier = Modifier.fillMaxWidth().testTag("member_id_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                        label = { Text("12-Digit PIN") }, leadingIcon = { Icon(Icons.Default.Security, null, tint = MahiTextSecondary) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MahiTextPrimary, unfocusedTextColor = MahiTextPrimary, focusedBorderColor = MahiAccent, unfocusedBorderColor = MahiCardStroke, focusedContainerColor = Color(0xFF13141B), unfocusedContainerColor = Color(0xFF13141B)),
                        modifier = Modifier.fillMaxWidth().testTag("member_pin_input")
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { onSignIn(memberId.trim(), pin) },
                        enabled = Regex("^MR-[A-HJ-NP-Z2-9]{8}$").matches(memberId) && pin.length == 12 && !state.isAuthLoading,
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MahiAccent),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("member_sign_in_button")
                    ) {
                        if (state.isAuthLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("SIGN IN SECURELY", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp)
                    }
                    AnimatedVisibility(visible = state.authStatusMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.authStatusMessage ?: "",
                            color = if (listOf("error", "failed", "unable", "denied", "expired", "incorrect").any { state.authStatusMessage?.contains(it, ignoreCase = true) == true }) MahiRedPrimary else MahiTextSecondary,
                            fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("No email address or verification code required.", color = MahiTextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
