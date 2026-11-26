package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.local.UserSession
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import com.example.data.remote.InvalidAuthSessionException
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.CancellationException
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

    /**
     * Establish no-prompt public app access. A valid legacy Member session is kept
     * for compatibility; otherwise the app silently creates an anonymous Supabase
     * Auth user and stores its tokens encrypted by Android Keystore.
     */
    suspend fun ensurePublicSession(): Result<UserSession> = refreshMutex.withLock {
        if (!supabaseClient.isConfigured()) {
            return@withLock Result.failure(IllegalStateException("The public repair service is not configured."))
        }

        val current = preferencesManager.sessionFlow.first()
        if (current.isAuthenticated && current.role == "member") {
            if (current.refreshToken.isNotBlank() &&
                current.expiresAtMillis > System.currentTimeMillis() + 60_000L
            ) {
                return@withLock Result.success(current)
            }

            if (current.refreshToken.isNotBlank()) {
                val refreshed = supabaseClient.refreshSession(current.refreshToken)
                if (refreshed.isSuccess) {
                    val response = refreshed.getOrThrow()
                    val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
                        ?: return@withLock Result.failure(IllegalStateException("Session refresh omitted the access token."))
                    val refreshToken = response.refreshToken?.takeIf { it.isNotBlank() }
                        ?: return@withLock Result.failure(IllegalStateException("Session refresh omitted the refresh token."))
                    val role = response.user?.appMetadata?.get("role")?.takeIf { it.isNotBlank() } ?: current.role
                    if (role == "member") {
                        val expiresAt = System.currentTimeMillis() + ((response.expiresIn ?: 0L) * 1000L)
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
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Result.failure(IllegalStateException("Could not securely persist the session.", error))
                        }
                    }
                } else {
                    val cause = refreshed.exceptionOrNull()
                    if (cause !is InvalidAuthSessionException) {
                        return@withLock Result.failure(cause ?: IllegalStateException("Session refresh failed."))
                    }
                }
            }
        }

        if (current.isAuthenticated) preferencesManager.clearSession()

        val anonymousResult = supabaseClient.signInAnonymously()
        if (anonymousResult.isFailure) {
            return@withLock Result.failure(
                anonymousResult.exceptionOrNull()
                    ?: IllegalStateException("Could not create public app access.")
            )
        }

        val response = anonymousResult.getOrThrow()
        val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
            ?: return@withLock Result.failure(IllegalStateException("Public access did not return an access token."))
        val refreshToken = response.refreshToken?.takeIf { it.isNotBlank() }
            ?: return@withLock Result.failure(IllegalStateException("Public access did not return a refresh token."))
        val user = response.user
            ?: return@withLock Result.failure(IllegalStateException("Public access did not return a user identity."))
        val anonymous = user.isAnonymous ?: accessToken.isAnonymousJwt()
        if (!anonymous) {
            return@withLock Result.failure(IllegalStateException("Supabase returned a non-anonymous identity for public access."))
        }
        val role = user.appMetadata?.get("role")?.takeIf { it.isNotBlank() } ?: "member"
        if (role != "member") {
            return@withLock Result.failure(IllegalStateException("This identity is not authorized for public repair access."))
        }

        val identity = "anonymous:${user.id}"
        val expiresAt = System.currentTimeMillis() + ((response.expiresIn ?: 0L) * 1000L)
        return@withLock try {
            preferencesManager.saveSession(
                email = identity,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAt,
                role = role
            )
            Result.success(
                UserSession(
                    email = identity,
                    token = accessToken,
                    role = role,
                    isAuthenticated = true,
                    refreshToken = refreshToken,
                    expiresAtMillis = expiresAt
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not securely store public app access.", error))
        }
    }

    private fun String.isAnonymousJwt(): Boolean {
        return runCatching {
            val parts = split('.')
            require(parts.size == 3)
            val decoded = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            JSONObject(String(decoded, StandardCharsets.UTF_8)).optBoolean("is_anonymous", false)
        }.getOrDefault(false)
    }

    suspend fun signInWithMemberCredentials(memberCode: String, pin: String): Result<UserSession> = refreshMutex.withLock {
        val cleanMemberCode = memberCode.trim().uppercase()
        if (!Regex("^MR-[A-HJ-NP-Z2-9]{8}$").matches(cleanMemberCode)) {
            return Result.failure(IllegalArgumentException("Enter the Member ID provided by the Owner."))
        }
        if (!Regex("^\\d{12}$").matches(pin)) {
            return Result.failure(IllegalArgumentException("Enter the complete 12-digit PIN."))
        }
        if (!supabaseClient.isConfigured()) {
            return Result.failure(IllegalStateException("Repair service is temporarily unavailable."))
        }
        val result = supabaseClient.signInWithMemberCredentials(cleanMemberCode, pin)
        if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: IllegalStateException("Member sign-in failed."))

        val response = result.getOrThrow()
        val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Authentication response did not include an access token."))
        val refreshToken = response.refreshToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Authentication response did not include a refresh token."))
        val user = response.user ?: return Result.failure(IllegalStateException("Authentication response did not include a Member identity."))
        val role = user.appMetadata?.get("role")?.takeIf { it.isNotBlank() } ?: "member"
        if (role != "member") return Result.failure(IllegalStateException("This credential is not authorized for Member access."))
        val sessionIdentity = user.email?.takeIf { it.isNotBlank() } ?: cleanMemberCode
        val expiresAt = System.currentTimeMillis() + ((response.expiresIn ?: 0L) * 1000L)

        return try {
            preferencesManager.saveSession(email = sessionIdentity, accessToken = accessToken, refreshToken = refreshToken, expiresAtMillis = expiresAt, role = role)
            Result.success(UserSession(email = sessionIdentity, token = accessToken, role = role, isAuthenticated = true, refreshToken = refreshToken, expiresAtMillis = expiresAt))
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not securely persist the refreshed session.", error))
        }
    }

    /** Clear local credentials after definitive server-side revocation without a logout request. */
    suspend fun invalidateLocalSession() = refreshMutex.withLock {
        preferencesManager.clearSession()
    }

    suspend fun signOut() = refreshMutex.withLock {
        val current = preferencesManager.sessionFlow.first()
        try {
            if (current.token.isNotBlank()) supabaseClient.signOut(current.token)
        } finally {
            preferencesManager.clearSession()
        }
    }
}
