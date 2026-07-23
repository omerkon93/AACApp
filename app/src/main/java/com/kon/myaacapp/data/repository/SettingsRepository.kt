package com.kon.myaacapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kon.myaacapp.domain.service.Gender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Kept at the top level as per DataStore best practices to guarantee a single instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context
) {
    private companion object {
        const val FACTORY_GRID_COLUMNS = 3
        const val FACTORY_GRID_ROWS = 5

        const val FACTORY_GRID_TILE_SCALE = 1.35f
        const val FACTORY_GRID_TILE_CONTAINER_SCALE = 0.95f

        const val FACTORY_BAR_TILE_IMAGE_SCALE = 1.0f
        const val FACTORY_BAR_TILE_TITLE_SCALE = 1.0f

        const val FACTORY_ACTION_BUTTON_SCALE = 1.0f

        const val FACTORY_SHOW_SENTENCE_BAR = false
        const val FACTORY_SHOW_BACK_BUTTON = true
        const val FACTORY_SHOW_BACKSPACE_BUTTON = true
        const val FACTORY_SHOW_SPEAK_BUTTON = true
        const val FACTORY_HOME_IN_ACTION_BAR = true
    }

    private object PreferencesKeys {
        val SPEAK_ON_TILE_PRESS = booleanPreferencesKey("speak_on_tile_press")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        // 👉 NEW LAYOUT SETTINGS KEYS
        val GRID_TILE_SCALE = floatPreferencesKey("grid_tile_scale")
        val GRID_TILE_CONTAINER_SCALE = floatPreferencesKey("grid_tile_container_scale")
        val BAR_TILE_IMAGE_SCALE = floatPreferencesKey("bar_tile_image_scale")
        val BAR_TILE_TITLE_SCALE = floatPreferencesKey("bar_tile_title_scale")
        val ACTION_BUTTON_SCALE = floatPreferencesKey("action_button_scale")
        val SHOW_SENTENCE_BAR = booleanPreferencesKey("show_sentence_bar")
        val SHOW_BACK_BUTTON = booleanPreferencesKey("show_back_button")
        val SHOW_BACKSPACE_BUTTON = booleanPreferencesKey("show_backspace_button")
        val SHOW_SPEAK_BUTTON = booleanPreferencesKey("show_speak_button")
        val HOME_IN_ACTION_BAR = booleanPreferencesKey("home_in_action_bar")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val GRID_ROWS = intPreferencesKey("grid_rows")

        // Saved user-default layout settings
        val DEFAULT_GRID_COLUMNS =
            intPreferencesKey("default_grid_columns")

        val DEFAULT_GRID_ROWS =
            intPreferencesKey("default_grid_rows")

        val DEFAULT_GRID_TILE_SCALE =
            floatPreferencesKey("default_grid_tile_scale")

        val DEFAULT_GRID_TILE_CONTAINER_SCALE =
            floatPreferencesKey("default_grid_tile_container_scale")

        val DEFAULT_BAR_TILE_IMAGE_SCALE =
            floatPreferencesKey("default_bar_tile_image_scale")

        val DEFAULT_BAR_TILE_TITLE_SCALE =
            floatPreferencesKey("default_bar_tile_title_scale")

        val DEFAULT_ACTION_BUTTON_SCALE =
            floatPreferencesKey("default_action_button_scale")

        val DEFAULT_SHOW_SENTENCE_BAR =
            booleanPreferencesKey("default_show_sentence_bar")

        val DEFAULT_SHOW_BACK_BUTTON =
            booleanPreferencesKey("default_show_back_button")

        val DEFAULT_SHOW_BACKSPACE_BUTTON =
            booleanPreferencesKey("default_show_backspace_button")

        val DEFAULT_SHOW_SPEAK_BUTTON =
            booleanPreferencesKey("default_show_speak_button")

        val DEFAULT_HOME_IN_ACTION_BAR =
            booleanPreferencesKey("default_home_in_action_bar")
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

    // 👉 NEW LAYOUT SETTINGS FLOWS

    val gridTileContainerScaleFlow: Flow<Float> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE]
                ?: FACTORY_GRID_TILE_CONTAINER_SCALE
        }
        .distinctUntilChanged()

    val gridTileScaleFlow: Flow<Float> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.GRID_TILE_SCALE]
                ?: FACTORY_GRID_TILE_SCALE
        }
        .distinctUntilChanged()

    val actionButtonScaleFlow: Flow<Float> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.ACTION_BUTTON_SCALE]
                ?: FACTORY_ACTION_BUTTON_SCALE
        }
        .distinctUntilChanged()

    val showSentenceBarFlow: Flow<Boolean> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_SENTENCE_BAR]
                ?: FACTORY_SHOW_SENTENCE_BAR
        }
        .distinctUntilChanged()

    val barTileImageScaleFlow: Flow<Float> =
        basePreferencesFlow
            .map { preferences ->
                preferences[
                    PreferencesKeys.BAR_TILE_IMAGE_SCALE
                ] ?: FACTORY_BAR_TILE_IMAGE_SCALE
            }
            .distinctUntilChanged()

    val barTileTitleScaleFlow: Flow<Float> =
        basePreferencesFlow
            .map { preferences ->
                preferences[
                    PreferencesKeys.BAR_TILE_TITLE_SCALE
                ] ?: FACTORY_BAR_TILE_TITLE_SCALE
            }
            .distinctUntilChanged()

    val showBackButtonFlow: Flow<Boolean> =
        basePreferencesFlow
            .map { preferences ->
                preferences[
                    PreferencesKeys.SHOW_BACK_BUTTON
                ] ?: FACTORY_SHOW_BACK_BUTTON
            }
            .distinctUntilChanged()

    val showBackspaceButtonFlow: Flow<Boolean> =
        basePreferencesFlow
            .map { preferences ->
                preferences[
                    PreferencesKeys.SHOW_BACKSPACE_BUTTON
                ] ?: FACTORY_SHOW_BACKSPACE_BUTTON
            }
            .distinctUntilChanged()

    val showSpeakButtonFlow: Flow<Boolean> =
        basePreferencesFlow
            .map { preferences ->
                preferences[
                    PreferencesKeys.SHOW_SPEAK_BUTTON
                ] ?: FACTORY_SHOW_SPEAK_BUTTON
            }
            .distinctUntilChanged()

    val homeInActionBarFlow: Flow<Boolean> =
        basePreferencesFlow
            .map { preferences ->
                preferences[
                    PreferencesKeys.HOME_IN_ACTION_BAR
                ] ?: FACTORY_HOME_IN_ACTION_BAR
            }
            .distinctUntilChanged()

    val gridColumnsFlow: Flow<Int> =
        basePreferencesFlow
            .map { preferences ->
                preferences[PreferencesKeys.GRID_COLUMNS]
                    ?: FACTORY_GRID_COLUMNS
            }
            .distinctUntilChanged()

    val gridRowsFlow: Flow<Int> =
        basePreferencesFlow
            .map { preferences ->
                preferences[PreferencesKeys.GRID_ROWS]
                    ?: FACTORY_GRID_ROWS
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

    // 👉 NEW LAYOUT SETTINGS UPDATE FUNCTIONS
    suspend fun updateGridTileScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRID_TILE_SCALE] = scale
        }
    }

    suspend fun updateGridTileContainerScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE] = scale
        }
    }

    suspend fun updateBarTileImageScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE] = scale
        }
    }

    suspend fun updateBarTileTitleScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE] = scale
        }
    }

    suspend fun updateActionButtonScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTION_BUTTON_SCALE] = scale
        }
    }

    suspend fun updateShowSentenceBar(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SENTENCE_BAR] = show
        }
    }

    suspend fun updateShowBackButton(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_BACK_BUTTON] = show }
    }
    suspend fun updateShowBackspaceButton(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_BACKSPACE_BUTTON] = show }
    }
    suspend fun updateShowSpeakButton(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_SPEAK_BUTTON] = show }
    }
    suspend fun updateHomeInActionBar(inActionBar: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_IN_ACTION_BAR] = inActionBar }
    }

    suspend fun updateGridColumns(columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRID_COLUMNS] = columns
        }
    }

    suspend fun updateGridRows(rows: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRID_ROWS] = rows
        }
    }

    suspend fun saveCurrentLayoutAsDefault() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_GRID_COLUMNS] =
                preferences[PreferencesKeys.GRID_COLUMNS]
                    ?: FACTORY_GRID_COLUMNS

            preferences[PreferencesKeys.DEFAULT_GRID_ROWS] =
                preferences[PreferencesKeys.GRID_ROWS]
                    ?: FACTORY_GRID_ROWS

            preferences[PreferencesKeys.DEFAULT_GRID_TILE_SCALE] =
                preferences[PreferencesKeys.GRID_TILE_SCALE]
                    ?: FACTORY_GRID_TILE_SCALE

            preferences[PreferencesKeys.DEFAULT_GRID_TILE_CONTAINER_SCALE] =
                preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE]
                    ?: FACTORY_GRID_TILE_CONTAINER_SCALE

            preferences[PreferencesKeys.DEFAULT_BAR_TILE_IMAGE_SCALE] =
                preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE]
                    ?: FACTORY_BAR_TILE_IMAGE_SCALE

            preferences[PreferencesKeys.DEFAULT_BAR_TILE_TITLE_SCALE] =
                preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE]
                    ?: FACTORY_BAR_TILE_TITLE_SCALE

            preferences[PreferencesKeys.DEFAULT_ACTION_BUTTON_SCALE] =
                preferences[PreferencesKeys.ACTION_BUTTON_SCALE]
                    ?: FACTORY_ACTION_BUTTON_SCALE

            preferences[PreferencesKeys.DEFAULT_SHOW_SENTENCE_BAR] =
                preferences[PreferencesKeys.SHOW_SENTENCE_BAR]
                    ?: FACTORY_SHOW_SENTENCE_BAR

            preferences[PreferencesKeys.DEFAULT_SHOW_BACK_BUTTON] =
                preferences[PreferencesKeys.SHOW_BACK_BUTTON]
                    ?: FACTORY_SHOW_BACK_BUTTON

            preferences[PreferencesKeys.DEFAULT_SHOW_BACKSPACE_BUTTON] =
                preferences[PreferencesKeys.SHOW_BACKSPACE_BUTTON]
                    ?: FACTORY_SHOW_BACKSPACE_BUTTON

            preferences[PreferencesKeys.DEFAULT_SHOW_SPEAK_BUTTON] =
                preferences[PreferencesKeys.SHOW_SPEAK_BUTTON]
                    ?: FACTORY_SHOW_SPEAK_BUTTON

            preferences[PreferencesKeys.DEFAULT_HOME_IN_ACTION_BAR] =
                preferences[PreferencesKeys.HOME_IN_ACTION_BAR]
                    ?: FACTORY_HOME_IN_ACTION_BAR
        }
    }

    suspend fun restoreDefaultLayoutSettings() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRID_COLUMNS] =
                preferences[PreferencesKeys.DEFAULT_GRID_COLUMNS]
                    ?: FACTORY_GRID_COLUMNS

            preferences[PreferencesKeys.GRID_ROWS] =
                preferences[PreferencesKeys.DEFAULT_GRID_ROWS]
                    ?: FACTORY_GRID_ROWS

            preferences[PreferencesKeys.GRID_TILE_SCALE] =
                preferences[PreferencesKeys.DEFAULT_GRID_TILE_SCALE]
                    ?: FACTORY_GRID_TILE_SCALE

            preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE] =
                preferences[
                    PreferencesKeys.DEFAULT_GRID_TILE_CONTAINER_SCALE
                ] ?: FACTORY_GRID_TILE_CONTAINER_SCALE

            preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE] =
                preferences[PreferencesKeys.DEFAULT_BAR_TILE_IMAGE_SCALE]
                    ?: FACTORY_BAR_TILE_IMAGE_SCALE

            preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE] =
                preferences[PreferencesKeys.DEFAULT_BAR_TILE_TITLE_SCALE]
                    ?: FACTORY_BAR_TILE_TITLE_SCALE

            preferences[PreferencesKeys.ACTION_BUTTON_SCALE] =
                preferences[PreferencesKeys.DEFAULT_ACTION_BUTTON_SCALE]
                    ?: FACTORY_ACTION_BUTTON_SCALE

            preferences[PreferencesKeys.SHOW_SENTENCE_BAR] =
                preferences[PreferencesKeys.DEFAULT_SHOW_SENTENCE_BAR]
                    ?: FACTORY_SHOW_SENTENCE_BAR

            preferences[PreferencesKeys.SHOW_BACK_BUTTON] =
                preferences[PreferencesKeys.DEFAULT_SHOW_BACK_BUTTON]
                    ?: FACTORY_SHOW_BACK_BUTTON

            preferences[PreferencesKeys.SHOW_BACKSPACE_BUTTON] =
                preferences[PreferencesKeys.DEFAULT_SHOW_BACKSPACE_BUTTON]
                    ?: FACTORY_SHOW_BACKSPACE_BUTTON

            preferences[PreferencesKeys.SHOW_SPEAK_BUTTON] =
                preferences[PreferencesKeys.DEFAULT_SHOW_SPEAK_BUTTON]
                    ?: FACTORY_SHOW_SPEAK_BUTTON

            preferences[PreferencesKeys.HOME_IN_ACTION_BAR] =
                preferences[PreferencesKeys.DEFAULT_HOME_IN_ACTION_BAR]
                    ?: FACTORY_HOME_IN_ACTION_BAR
        }
    }
}