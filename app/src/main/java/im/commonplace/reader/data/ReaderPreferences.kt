package im.commonplace.reader.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderPreferences(private val store: DataStore<Preferences>) {
    private val darkReading = booleanPreferencesKey("dark_reading")
    fun observeDarkReading(): Flow<Boolean> = store.data.map { it[darkReading] ?: false }
    suspend fun setDarkReading(enabled: Boolean) { store.edit { it[darkReading] = enabled } }
}
