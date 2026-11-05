package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mahi_repair_prefs")

data class UserSession(
    val email: String,
    val token: String,
    val role: String,
    val isAuthenticated: Boolean,
    val refreshToken: String = "",
    val expiresAtMillis: Long = 0L
)

/**
 * Encrypts Supabase credentials using a non-exportable AES key held by Android Keystore.
 * Only authenticated ciphertext is persisted in DataStore.
 */
private object SessionTokenCipher {
    private const val KEY_ALIAS = "mahi_repair_session_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encrypt(value: String): String {
        require(value.isNotBlank()) { "Cannot encrypt an empty session token" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        require(encoded.isNotBlank()) { "Encrypted session token is missing" }
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES) { "Encrypted session token is malformed" }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }
}

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        // Legacy plaintext key: read only to delete it, never to restore a session.
        private val KEY_LEGACY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_ACCESS_TOKEN_ENCRYPTED = stringPreferencesKey("auth_access_token_encrypted")
        private val KEY_REFRESH_TOKEN_ENCRYPTED = stringPreferencesKey("auth_refresh_token_encrypted")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_SESSION_EXPIRES_AT = longPreferencesKey("session_expires_at")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        private val KEY_DEVICE_ID = stringPreferencesKey("registered_device_id")
    }

    val deviceIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveDeviceId(deviceId: String) {
        require(deviceId.isNotBlank()) { "Server-issued device ID is required" }
        context.dataStore.edit { prefs -> prefs[KEY_DEVICE_ID] = deviceId.trim() }
    }

    val sessionFlow: Flow<UserSession> = context.dataStore.data.map { prefs ->
        val email = prefs[KEY_USER_EMAIL] ?: ""
        val accessToken = prefs[KEY_ACCESS_TOKEN_ENCRYPTED]?.let { encrypted ->
            runCatching { SessionTokenCipher.decrypt(encrypted) }.getOrDefault("")
        }.orEmpty()
        val refreshToken = prefs[KEY_REFRESH_TOKEN_ENCRYPTED]?.let { encrypted ->
            runCatching { SessionTokenCipher.decrypt(encrypted) }.getOrDefault("")
        }.orEmpty()
        val role = prefs[KEY_USER_ROLE] ?: "member"
        val expiresAt = prefs[KEY_SESSION_EXPIRES_AT] ?: 0L
        UserSession(
            email = email,
            token = accessToken,
            role = role,
            isAuthenticated = email.isNotBlank() && accessToken.isNotBlank(),
            refreshToken = refreshToken,
            expiresAtMillis = expiresAt
        )
    }

    val lastSyncFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC] ?: 0L
    }

    suspend fun saveSession(
        email: String,
        accessToken: String,
        refreshToken: String,
        expiresAtMillis: Long,
        role: String = "member"
    ) {
        require(email.isNotBlank()) { "Email is required for a session" }
        val encryptedAccessToken = SessionTokenCipher.encrypt(accessToken)
        val encryptedRefreshToken = SessionTokenCipher.encrypt(refreshToken)
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = email.trim()
            prefs[KEY_ACCESS_TOKEN_ENCRYPTED] = encryptedAccessToken
            prefs[KEY_REFRESH_TOKEN_ENCRYPTED] = encryptedRefreshToken
            prefs[KEY_SESSION_EXPIRES_AT] = expiresAtMillis
            prefs[KEY_USER_ROLE] = role.trim().ifBlank { "member" }
            prefs.remove(KEY_LEGACY_AUTH_TOKEN)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_ACCESS_TOKEN_ENCRYPTED)
            prefs.remove(KEY_REFRESH_TOKEN_ENCRYPTED)
            prefs.remove(KEY_SESSION_EXPIRES_AT)
            prefs.remove(KEY_USER_ROLE)
            prefs.remove(KEY_LEGACY_AUTH_TOKEN)
        }
    }

    suspend fun clearLegacyPlaintextSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LEGACY_AUTH_TOKEN)
        }
    }

    suspend fun updateLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_SYNC] = timestamp }
    }
}
