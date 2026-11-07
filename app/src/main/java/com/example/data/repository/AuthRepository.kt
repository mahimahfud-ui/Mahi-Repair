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
    private companion object {
        // Shared across app and WorkManager repository instances within the process.
        val refreshMutex = Mutex()
    }

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
        val role = response.user?.appMetadata?.role?.takeIf { it.isNotBlank() }
            ?: (response.user?.userMetadata?.get("role") as? String)?.takeIf { it.isNotBlank() }
        if (role != "member") {
            return Result.failure(IllegalStateException("This account is not an active invited member for the Android app."))
        }

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
     * Verify an invited user's one-time Supabase token hash and persist credentials
     * only after the Auth endpoint confirms successful verification.
     */
    suspend fun verifyInvitationToken(tokenHash: String): Result<UserSession> {
        val cleanHash = tokenHash.trim()
        if (!Regex("^[A-Za-z0-9_-]{16,512}$").matches(cleanHash)) {
            return Result.failure(IllegalArgumentException("This invitation link is invalid. Enter your invited email to request a new code."))
        }
        if (!supabaseClient.isConfigured()) {
            return Result.failure(IllegalStateException("Repair service is temporarily unavailable. Please try again later."))
        }

        val verification = supabaseClient.verifyInvitationToken(cleanHash)
        if (verification.isFailure) {
            return Result.failure(
                IllegalStateException(
                    "This invitation link is invalid, expired, or already used. Enter your invited email to request a new code.",
                    verification.exceptionOrNull()
                )
            )
        }

        val response = verification.getOrThrow()
        val email = response.user?.email?.trim()?.lowercase()
            ?.takeIf { android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() }
            ?: return Result.failure(IllegalStateException("The verified invitation did not include a valid account email."))
        val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Invitation verification did not return an access token."))
        val refreshToken = response.refreshToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Invitation verification did not return a refresh token."))
        val expiresIn = response.expiresIn?.takeIf { it > 0L }
            ?: return Result.failure(IllegalStateException("Invitation verification returned an invalid session lifetime."))
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        val role = response.user?.appMetadata?.role?.takeIf { it.isNotBlank() }
            ?: (response.user?.userMetadata?.get("role") as? String)?.takeIf { it.isNotBlank() }
        if (role != "member") {
            return Result.failure(IllegalStateException("This invitation is not a member invitation for the Android app."))
        }

        return try {
            preferencesManager.saveSession(
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAt,
                role = role
            )
            Result.success(
                UserSession(
                    email = email,
                    token = accessToken,
                    role = role,
                    isAuthenticated = true,
                    refreshToken = refreshToken,
                    expiresAtMillis = expiresAt
                )
            )
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not securely store the invitation session on this device.", error))
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
        val role = response.user?.appMetadata?.role?.takeIf { it.isNotBlank() } ?: current.role

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
            if (current.token.isNotBlank()) {
                supabaseClient.signOut(current.token).getOrThrow()
            }
        } finally {
            // Always remove the local encrypted session even when server revocation fails.
            preferencesManager.clearSession()
        }
    }
}
