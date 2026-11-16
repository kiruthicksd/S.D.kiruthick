package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_ai_map_prefs")

data class AppSettings(
  val isDarkTheme: Boolean = true,
  val esp32Address: String = "",
  val aiTone: String = "Balanced",
  val autoSuggestCaptions: Boolean = true,
  val systemNotificationsEnabled: Boolean = false,
  val defaultSharePublic: Boolean = false,
)

class SettingsRepository(private val context: Context) {

  private object Keys {
    val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    val ESP32_ADDRESS = stringPreferencesKey("esp32_address")
    val AI_TONE = stringPreferencesKey("ai_tone")
    val AUTO_SUGGEST_CAPTIONS = booleanPreferencesKey("auto_suggest_captions")
    val SYSTEM_NOTIFICATIONS = booleanPreferencesKey("system_notifications")
    val DEFAULT_SHARE_PUBLIC = booleanPreferencesKey("default_share_public")
  }

  val settingsFlow: Flow<AppSettings> =
    context.dataStore.data.map { prefs ->
      AppSettings(
        isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
        esp32Address = prefs[Keys.ESP32_ADDRESS] ?: "",
        aiTone = prefs[Keys.AI_TONE] ?: "Balanced",
        autoSuggestCaptions = prefs[Keys.AUTO_SUGGEST_CAPTIONS] ?: true,
        systemNotificationsEnabled = prefs[Keys.SYSTEM_NOTIFICATIONS] ?: false,
        defaultSharePublic = prefs[Keys.DEFAULT_SHARE_PUBLIC] ?: false,
      )
    }

  suspend fun setDarkTheme(enabled: Boolean) {
    context.dataStore.edit { it[Keys.IS_DARK_THEME] = enabled }
  }

  suspend fun setEsp32Address(address: String) {
    context.dataStore.edit { it[Keys.ESP32_ADDRESS] = address.trim() }
  }

  suspend fun setAiTone(tone: String) {
    context.dataStore.edit { it[Keys.AI_TONE] = tone }
  }

  suspend fun setAutoSuggestCaptions(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_SUGGEST_CAPTIONS] = enabled }
  }

  suspend fun setSystemNotificationsEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.SYSTEM_NOTIFICATIONS] = enabled }
  }

  suspend fun setDefaultSharePublic(enabled: Boolean) {
    context.dataStore.edit { it[Keys.DEFAULT_SHARE_PUBLIC] = enabled }
  }
}
