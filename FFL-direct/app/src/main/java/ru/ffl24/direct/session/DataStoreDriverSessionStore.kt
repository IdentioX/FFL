package ru.ffl24.direct.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import ru.ffl24.direct.domain.DriverSession

private val Context.directDriverSession by preferencesDataStore(name = "ffl_direct_session_v1")

/** Separate app/file from the legacy JWT store; only version, id and name are persisted. */
class DataStoreDriverSessionStore(context: Context, private val profileScope: String = "") : DriverSessionStore {
    private val store = context.applicationContext.directDriverSession

    override suspend fun read(): DriverSession? {
        val values = store.data.first()
        val id = values[DriverId]
        val name = values[DisplayName]
        if (values[Profile].orEmpty() != profileScope) return null
        if (values[Version] != 1 || id == null || id <= 0 || name.isNullOrEmpty()) {
            if (values.asMap().isNotEmpty()) clear()
            return null
        }
        return DriverSession(id, name)
    }

    override suspend fun save(session: DriverSession) {
        store.edit { values ->
            values.clear()
            values[Profile] = profileScope
            values[Version] = session.version
            values[DriverId] = session.driverId
            values[DisplayName] = session.displayName
        }
    }

    override suspend fun clear() { store.edit { if(it[Profile].orEmpty() == profileScope) it.clear() } }

    private companion object {
        val Profile = stringPreferencesKey("connection_profile")
        val Version = intPreferencesKey("version")
        val DriverId = longPreferencesKey("driver_id")
        val DisplayName = stringPreferencesKey("display_name")
    }
}
