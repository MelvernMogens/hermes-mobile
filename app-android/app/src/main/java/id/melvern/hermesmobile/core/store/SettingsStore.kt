package id.melvern.hermesmobile.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by androidx.datastore.preferences.preferencesDataStore(name = "settings")

@Serializable
data class ConnectionSettings(
    /** Base URL, mis. https://melverns-macbook-pro.taila32ead.ts.net (tanpa port). */
    val baseUrl: String = "",
    /** Username basic auth. */
    val username: String = "",
    /** Password basic auth — di DataStore (private app storage). */
    val password: String = "",
    /** M4: profile aktif — dipakai semua RPC yang support params.profile. */
    val profile: String = "default",
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

object SettingsStore {
    private val KEY_JSON = stringPreferencesKey("connection_json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(context: Context): ConnectionSettings = try {
        val raw = context.dataStore.data.first()[KEY_JSON] ?: return ConnectionSettings()
        json.decodeFromString(ConnectionSettings.serializer(), raw)
    } catch (_: Throwable) { ConnectionSettings() }

    suspend fun save(context: Context, value: ConnectionSettings) {
        context.dataStore.edit { it[KEY_JSON] = json.encodeToString(ConnectionSettings.serializer(), value) }
    }
}
