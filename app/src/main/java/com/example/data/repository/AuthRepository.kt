package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.local.UserSession
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val preferencesManager: PreferencesManager,
    private val supabaseClient: SupabaseClient
) {

    val sessionFlow: Flow<UserSession> = preferencesManager.sessionFlow

    suspend fun getSession(): UserSession = preferencesManager.sessionFlow.first()

    suspend fun requestEmailOtp(email: String): Result<String> {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid invited email address."))
        }

        if (supabaseClient.isConfigured()) {
            val result = supabaseClient.requestEmailOtp(cleanEmail)
            return if (result.isSuccess) {
                Result.success("Access code sent to $cleanEmail. Check your inbox.")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to send code"
                Result.failure(Exception("Supabase Auth error: $err. Ensure the Owner has invited this email."))
            }
        } else {
            return Result.failure(IllegalStateException("Repair service is not configured. Please try again later."))
        }
    }

    suspend fun verifyEmailOtp(email: String, otpToken: String): Result<UserSession> {
        val cleanEmail = email.trim().lowercase()
        val cleanToken = otpToken.trim()

        if (cleanToken.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter the complete verification code."))
        }

        if (supabaseClient.isConfigured()) {
            val result = supabaseClient.verifyEmailOtp(cleanEmail, cleanToken)
            return if (result.isSuccess) {
                val session = result.getOrThrow()
                val token = session.accessToken ?: "valid_token_${System.currentTimeMillis()}"
                val role = session.user?.appMetadata?.get("role")
                    ?: session.user?.userMetadata?.get("role")
                    ?: "family_member"

                preferencesManager.saveSession(cleanEmail, token, role)
                Result.success(UserSession(cleanEmail, token, role, true))
            } else {
                val err = result.exceptionOrNull()?.message ?: "Invalid code"
                Result.failure(Exception("Verification failed: $err"))
            }
        } else {
            return Result.failure(IllegalStateException("Repair service is not configured. Please try again later."))
        }
    }

    suspend fun signOut() {
        preferencesManager.clearSession()
    }
}
