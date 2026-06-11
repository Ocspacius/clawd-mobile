package com.clawd.mobile.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clawd.mobile.data.model.ConnectionConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionHistoryEntry(
    val host: String,
    val port: Int,
    val token: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class ConnectionPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    companion object {
        private val KEY_HISTORY = stringPreferencesKey("connection_history")
        private const val MAX_HISTORY = 5
    }

    val history: Flow<List<ConnectionHistoryEntry>> = dataStore.data.map { prefs ->
        val json = prefs[KEY_HISTORY] ?: "[]"
        try {
            val type = object : TypeToken<List<ConnectionHistoryEntry>>() {}.type
            val list: List<ConnectionHistoryEntry> = gson.fromJson(json, type)
            // Gson can inject null into non-null Kotlin fields via reflection;
            // filter out any corrupted entries to prevent NPE on access.
            list.filter { it.host != null && it.token != null }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveToHistory(config: ConnectionConfig) {
        val entry = ConnectionHistoryEntry(
            host = config.host,
            port = config.port,
            token = config.token
        )
        dataStore.edit { prefs ->
            val existing = loadHistory(prefs)
            val filtered = existing.filter { it.host != config.host || it.port != config.port }
            val updated = listOf(entry) + filtered
            prefs[KEY_HISTORY] = gson.toJson(updated.take(MAX_HISTORY))
        }
    }

    suspend fun deleteHistory(index: Int) {
        dataStore.edit { prefs ->
            val existing = loadHistory(prefs).toMutableList()
            if (index in existing.indices) {
                existing.removeAt(index)
                prefs[KEY_HISTORY] = gson.toJson(existing)
            }
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }

    private fun loadHistory(prefs: Preferences): List<ConnectionHistoryEntry> {
        val json = prefs[KEY_HISTORY] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ConnectionHistoryEntry>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
