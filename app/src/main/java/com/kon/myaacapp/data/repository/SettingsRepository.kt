package com.kon.myaacapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kon.myaacapp.domain.service.Gender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Kept at the top level as per DataStore best practices to guarantee a single instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val SPEAK_ON_TILE_PRESS = booleanPreferencesKey("speak_on_tile_press")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }

    // OPTIMIZATION: Unified upstream flow.
    // We attach the disk-read error handling exactly once, rather than re-allocating
    // it for every single setting property.
    private val basePreferencesFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val activeProfileIdFlow: Flow<String?> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROFILE_ID]
        }
        // OPTIMIZATION: Firewalls the flow. If the user changes 'Speak on Press',
        // this flow will ignore the DataStore file update and NOT emit a duplicate profile ID.
        .distinctUntilChanged()

    val speakOnTilePressFlow: Flow<Boolean> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] ?: true
        }
        .distinctUntilChanged()

    val userGenderFlow: Flow<Gender> = basePreferencesFlow
        .map { preferences ->
            val genderStr = preferences[PreferencesKeys.USER_GENDER]
            if (genderStr != null) {
                try {
                    Gender.valueOf(genderStr)
                } catch (_: Exception) {
                    Gender.MALE
                }
            } else {
                Gender.MALE
            }
        }
        .distinctUntilChanged()

    val languageCodeFlow: Flow<String> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.LANGUAGE_CODE] ?: "he"
        }
        .distinctUntilChanged()

    suspend fun updateActiveProfileId(profileId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = profileId
        }
    }

    suspend fun updateSpeakOnTilePress(speak: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] = speak
        }
    }

    suspend fun updateUserGender(gender: Gender) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_GENDER] = gender.name
        }
    }

    suspend fun updateLanguageCode(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE_CODE] = languageCode
        }
    }
}