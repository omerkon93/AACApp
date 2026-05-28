package com.kon.myaacapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val SPEAK_ON_TILE_PRESS = booleanPreferencesKey("speak_on_tile_press")
    }

    val speakOnTilePressFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] ?: true
        }

    suspend fun updateSpeakOnTilePress(speak: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] = speak
        }
    }
}
