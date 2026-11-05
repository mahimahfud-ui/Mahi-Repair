package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mahi_repair_prefs")

data class UserSession(
    val email: String,
    val token: String,
    val role: String,
    val isAuthenticated: Boolean
)

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        private val KEY_CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
        private val KEY_CUSTOM_SUPABASE_ANON_KEY = stringPreferencesKey("custom_supabase_anon_key")
        private val KEY_USE_LOCAL_TOOLKIT = booleanPreferencesKey("use_local_toolkit")
        private val KEY_DEVICE_ID = stringPreferencesKey("registered_device_id")
    }

    val deviceIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId.trim()
        }
    }

    val sessionFlow: Flow<UserSession> = context.dataStore.data.map { prefs ->
        val email = prefs[KEY_USER_EMAIL] ?: ""
        val token = prefs[KEY_AUTH_TOKEN] ?: ""
        val role = prefs[KEY_USER_ROLE] ?: "friend"
        val isAuth = email.isNotBlank() && token.isNotBlank()
        UserSession(email, token, role, isAuth)
    }

    val lastSyncFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC] ?: 0L
    }

    val customBackendFlow: Flow<Pair<String?, String?>> = context.dataStore.data.map { prefs ->
        Pair(prefs[KEY_CUSTOM_SUPABASE_URL], prefs[KEY_CUSTOM_SUPABASE_ANON_KEY])
    }

    val useLocalToolkitFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_LOCAL_TOOLKIT] ?: true
    }

    suspend fun saveSession(email: String, token: String, role: String = "member") {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = email.trim()
            prefs[KEY_AUTH_TOKEN] = token.trim()
            prefs[KEY_USER_ROLE] = role.trim()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_USER_ROLE)
        }
    }

    suspend fun updateLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = timestamp
        }
    }

    suspend fun saveCustomBackend(url: String, anonKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SUPABASE_URL] = url.trim()
            prefs[KEY_CUSTOM_SUPABASE_ANON_KEY] = anonKey.trim()
        }
    }

    suspend fun setUseLocalToolkit(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_LOCAL_TOOLKIT] = enabled
        }
    }
}
