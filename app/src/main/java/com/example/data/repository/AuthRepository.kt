package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.local.UserSession
import com.example.data.remote.InvalidAuthSessionException
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepository(
    private val preferencesManager: PreferencesManager,
    private val supabaseClient: SupabaseClient
) {
    private val refreshMutex = Mutex()

    val sessionFlow: Flow<UserSession> = preferencesManager.sessionFlow

    suspend fun getSession(): UserSession = preferencesManager.sessionFlow.first()

    suspend fun requestEmailOtp(email: String): Result<String> {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid invited email address."))
        }
        if (!supabaseClient.isConfigured()) {
            return Result.failure(IllegalStateException("Repair service is temporarily unavailable. Please try again later."))
        }

        val result = supabaseClient.requestEmailOtp(cleanEmail)
        return if (result.isSuccess) {
            Result.success("Access code sent to $cleanEmail. Check your inbox.")
        } else {
            val error = result.exceptionOrNull()?.message ?: "Failed to send code"
            Result.failure(IllegalStateException("Unable to send the access code. Check the address and try again.", result.exceptionOrNull()))
        }
    }

    suspend fun verifyEmailOtp(email: String, otpToken: String): Result<UserSession> {
        val cleanEmail = email.trim().lowercase()
        val cleanToken = otpToken.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid invited email address."))
        }
        if (cleanToken.length != 6 || cleanToken.any { !it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Enter the complete 6-digit access code."))
        }
        if (!supabaseClient.isConfigured()) {
            return Result.failure(IllegalStateException("Repair service is temporarily unavailable. Please try again later."))
        }

        val result = supabaseClient.verifyEmailOtp(cleanEmail, cleanToken)
        if (result.isFailure) {
            val cause = result.exceptionOrNull()
            return Result.failure(IllegalStateException("Verification failed. Check the code and try again.", cause))
        }

        val response = result.getOrThrow()
        val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Authentication response did not include an access token."))
        val refreshToken = response.refreshToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Authentication response did not include a refresh token."))
        val expiresAt = System.currentTimeMillis() + ((response.expiresIn ?: 0L) * 1000L)
        val role = response.user?.appMetadata?.get("role")?.takeIf { it.isNotBlank() } ?: "member"

        return try {
            preferencesManager.saveSession(
                email = cleanEmail,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAt,
                role = role
            )
            Result.success(
                UserSession(
                    email = cleanEmail,
                    token = accessToken,
                    role = role,
                    isAuthenticated = true,
                    refreshToken = refreshToken,
                    expiresAtMillis = expiresAt
                )
            )
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not securely store the sign-in session on this device.", error))
        }
    }

    /**
     * Return a usable session and serialize refreshes so concurrent sync/repair requests
     * never race each other while Supabase rotates refresh tokens.
     */
    suspend fun getValidSession(): Result<UserSession> = refreshMutex.withLock {
        val current = preferencesManager.sessionFlow.first()
        if (!current.isAuthenticated) {
            return@withLock Result.failure(IllegalStateException("Please sign in to continue."))
        }
        if (current.refreshToken.isBlank()) {
            preferencesManager.clearSession()
            return@withLock Result.failure(InvalidAuthSessionException("Your session needs a fresh sign-in."))
        }
        if (current.expiresAtMillis > System.currentTimeMillis() + 60_000L) {
            return@withLock Result.success(current)
        }

        val refreshed = supabaseClient.refreshSession(current.refreshToken)
        if (refreshed.isFailure) {
            val cause = refreshed.exceptionOrNull()
            if (cause is InvalidAuthSessionException) preferencesManager.clearSession()
            return@withLock Result.failure(cause ?: IllegalStateException("Session refresh failed."))
        }

        val response = refreshed.getOrThrow()
        val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
            ?: return@withLock Result.failure(IllegalStateException("Refresh response did not include an access token."))
        val refreshToken = response.refreshToken?.takeIf { it.isNotBlank() }
            ?: return@withLock Result.failure(IllegalStateException("Refresh response did not include a refresh token."))
        val expiresAt = System.currentTimeMillis() + ((response.expiresIn ?: 0L) * 1000L)
        val role = response.user?.appMetadata?.get("role")?.takeIf { it.isNotBlank() } ?: current.role

        return@withLock try {
            preferencesManager.saveSession(
                email = current.email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAt,
                role = role
            )
            Result.success(
                UserSession(
                    email = current.email,
                    token = accessToken,
                    role = role,
                    isAuthenticated = true,
                    refreshToken = refreshToken,
                    expiresAtMillis = expiresAt
                )
            )
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not securely persist the refreshed session.", error))
        }
    }

    suspend fun signOut() {
        val current = preferencesManager.sessionFlow.first()
        try {
            if (current.token.isNotBlank()) supabaseClient.signOut(current.token)
        } finally {
            preferencesManager.clearSession()
            preferencesManager.clearDeviceId()
        }
    }
}
