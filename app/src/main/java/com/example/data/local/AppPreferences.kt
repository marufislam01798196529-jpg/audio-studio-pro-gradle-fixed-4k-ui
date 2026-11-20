package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ReasoningLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.xthrexDataStore by preferencesDataStore(name = "xthrex_settings")

data class UserSettings(
    val darkTheme: Boolean = true,
    val lowMemoryMode: Boolean = false,
    val autoLowMemoryApplied: Boolean = false,
    val editorFontSizeSp: Int = 13,
    val editorLightTheme: Boolean = false,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val notificationsEnabled: Boolean = false,
    val remoteBackendUrl: String = "",
    val githubUsername: String = "",
    val parallelAgentsEnabled: Boolean = false,
    val requireApprovalForDeletes: Boolean = true,
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LOW_MEMORY_MODE = booleanPreferencesKey("low_memory_mode")
        val AUTO_LOW_MEM_APPLIED = booleanPreferencesKey("auto_low_mem_applied")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_LIGHT_THEME = booleanPreferencesKey("editor_light_theme")
        val REASONING_LEVEL = stringPreferencesKey("reasoning_level")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMOTE_BACKEND_URL = stringPreferencesKey("remote_backend_url")
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val PARALLEL_AGENTS = booleanPreferencesKey("parallel_agents")
        val REQUIRE_DELETE_APPROVAL = booleanPreferencesKey("require_delete_approval")
    }

    val settingsFlow: Flow<UserSettings> = context.xthrexDataStore.data.map { prefs ->
        UserSettings(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            lowMemoryMode = prefs[Keys.LOW_MEMORY_MODE] ?: false,
            autoLowMemoryApplied = prefs[Keys.AUTO_LOW_MEM_APPLIED] ?: false,
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 13,
            editorLightTheme = prefs[Keys.EDITOR_LIGHT_THEME] ?: false,
            reasoningLevel = runCatching {
                ReasoningLevel.valueOf(prefs[Keys.REASONING_LEVEL] ?: ReasoningLevel.MEDIUM.name)
            }.getOrDefault(ReasoningLevel.MEDIUM),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            remoteBackendUrl = prefs[Keys.REMOTE_BACKEND_URL] ?: "",
            githubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
            parallelAgentsEnabled = prefs[Keys.PARALLEL_AGENTS] ?: false,
            requireApprovalForDeletes = prefs[Keys.REQUIRE_DELETE_APPROVAL] ?: true,
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.xthrexDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setLowMemoryMode(enabled: Boolean) {
        context.xthrexDataStore.edit {
            it[Keys.LOW_MEMORY_MODE] = enabled
            it[Keys.AUTO_LOW_MEM_APPLIED] = true
        }
    }

    suspend fun setEditorFontSize(sizeSp: Int) {
        context.xthrexDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(10, 22) }
    }

    suspend fun setEditorLightTheme(light: Boolean) {
        context.xthrexDataStore.edit { it[Keys.EDITOR_LIGHT_THEME] = light }
    }

    suspend fun setReasoningLevel(level: ReasoningLevel) {
        context.xthrexDataStore.edit { it[Keys.REASONING_LEVEL] = level.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.xthrexDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setRemoteBackendUrl(url: String) {
        context.xthrexDataStore.edit { it[Keys.REMOTE_BACKEND_URL] = url.trim().trimEnd('/') }
    }

    suspend fun setGithubUsername(username: String) {
        context.xthrexDataStore.edit { it[Keys.GITHUB_USERNAME] = username.trim() }
    }

    suspend fun setParallelAgentsEnabled(enabled: Boolean) {
        context.xthrexDataStore.edit { it[Keys.PARALLEL_AGENTS] = enabled }
    }

    suspend fun setRequireApprovalForDeletes(required: Boolean) {
        context.xthrexDataStore.edit { it[Keys.REQUIRE_DELETE_APPROVAL] = required }
    }
}
