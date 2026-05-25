package ru.ffl24.zakaz.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authSessionDataStore by preferencesDataStore(name = "auth_session")

class AuthSessionManager(context: Context) {
    private val dataStore = context.applicationContext.authSessionDataStore

    fun getSession(): Flow<AuthSession?> {
        return dataStore.data.map { preferences ->
            val accessToken = preferences[KEY_ACCESS_TOKEN]
            val driverId = preferences[KEY_DRIVER_ID]
            val driverName = preferences[KEY_DRIVER_NAME]

            if (accessToken.isNullOrBlank() || driverId == null || driverName.isNullOrBlank()) {
                null
            } else {
                AuthSession(
                    accessToken = accessToken,
                    driverId = driverId,
                    driverName = driverName
                )
            }
        }
    }

    suspend fun saveSession(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = session.accessToken
            preferences[KEY_DRIVER_ID] = session.driverId
            preferences[KEY_DRIVER_NAME] = session.driverName
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_DRIVER_ID)
            preferences.remove(KEY_DRIVER_NAME)
        }
    }

    fun isLoggedIn(): Flow<Boolean> {
        return getSession().map { session -> session != null }
    }

    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_DRIVER_ID = intPreferencesKey("driver_id")
        val KEY_DRIVER_NAME = stringPreferencesKey("driver_name")
    }
}
