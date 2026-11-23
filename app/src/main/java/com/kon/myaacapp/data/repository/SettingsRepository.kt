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
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.domain.service.Gender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data class ExportedBoardSettings(
    val activeProfileId: String? = null,
    val gridColumns: Int = 3,
    val gridRows: Int = 5,
    val gridTileScale: Float = 1.0f,
    val gridTileContainerScale: Float = 1.0f,
    val barTileImageScale: Float = 1.0f,
    val barTileTitleScale: Float = 1.0f,
    val actionButtonScale: Float = 1.0f,
    val showSentenceBar: Boolean = false,
    val showBackButton: Boolean = true,
    val showBackspaceButton: Boolean = true,
    val showSpeakButton: Boolean = true,
    val homeInActionBar: Boolean = true,
    val speakOnTilePress: Boolean = true,
    val tilesUseTts: Boolean = true,
    val userGender: String = Gender.MALE.name,
    val languageCode: String = "he",
    val boardLanguageCode: String? = null,
) {
    fun resolvedBoardLanguageCode(): String =
        LocaleHelper.normalize(boardLanguageCode ?: languageCode)
}

@Serializable
data class BoardSettingsSnapshot(
    val activeProfileId: String? = null,
    val gridColumns: Int? = null,
    val gridRows: Int? = null,
    val gridTileScale: Float? = null,
    val gridTileContainerScale: Float? = null,
    val barTileImageScale: Float? = null,
    val barTileTitleScale: Float? = null,
    val actionButtonScale: Float? = null,
    val showSentenceBar: Boolean? = null,
    val showBackButton: Boolean? = null,
    val showBackspaceButton: Boolean? = null,
    val showSpeakButton: Boolean? = null,
    val homeInActionBar: Boolean? = null,
    val speakOnTilePress: Boolean? = null,
    val tilesUseTts: Boolean? = null,
    val userGender: String? = null,
    val languageCode: String? = null,
    val interfaceLanguageCode: String? = null,
    val languageSeparationMigrated: Boolean? = null,
)

// Kept at the top level as per DataStore best practices to guarantee a single instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context,
    private val customDataStore: DataStore<Preferences>? = null,
) {
    private val store: DataStore<Preferences>
        get() = customDataStore ?: context.dataStore
    private companion object {
        const val FACTORY_GRID_COLUMNS = 3
        const val FACTORY_GRID_ROWS = 5

        const val FACTORY_GRID_TILE_SCALE = 1.0f
        const val FACTORY_GRID_TILE_CONTAINER_SCALE = 1.0f

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
        val TILES_USE_TTS = booleanPreferencesKey("tiles_use_tts")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val INTERFACE_LANGUAGE_CODE = stringPreferencesKey("interface_language_code")
        val LANGUAGE_SEPARATION_MIGRATED = booleanPreferencesKey("language_separation_migrated")
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
    private val basePreferencesFlow: Flow<Preferences> = store.data
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

    val tilesUseTtsFlow: Flow<Boolean> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.TILES_USE_TTS] ?: true
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

    val interfaceLanguageCodeFlow: Flow<String> = basePreferencesFlow
        .map { preferences ->
            LocaleHelper.normalize(
                preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE]
                    ?: preferences[PreferencesKeys.LANGUAGE_CODE]
                    ?: "he",
            )
        }
        .distinctUntilChanged()

    val isLanguageSeparationMigratedFlow: Flow<Boolean> = basePreferencesFlow
        .map { preferences ->
            preferences[PreferencesKeys.LANGUAGE_SEPARATION_MIGRATED] ?: false
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
        store.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = profileId
        }
    }

    suspend fun updateSpeakOnTilePress(speak: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] = speak
        }
    }

    suspend fun updateTilesUseTts(value: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.TILES_USE_TTS] = value
        }
    }

    suspend fun updateUserGender(gender: Gender) {
        store.edit { preferences ->
            preferences[PreferencesKeys.USER_GENDER] = gender.name
        }
    }

    suspend fun updateLanguageCode(languageCode: String) {
        store.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE_CODE] = languageCode
        }
    }

    suspend fun updateInterfaceLanguageCode(languageCode: String) {
        val normalized = LocaleHelper.normalize(languageCode)
        store.edit { preferences ->
            preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE] = normalized
            preferences[PreferencesKeys.LANGUAGE_SEPARATION_MIGRATED] = true
        }
    }

    suspend fun hasExplicitInterfaceLanguagePreference(): Boolean {
        val preferences = basePreferencesFlow.first()
        return preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE] != null
    }

    suspend fun hasExplicitLegacyLanguagePreference(): Boolean {
        val preferences = basePreferencesFlow.first()
        return preferences[PreferencesKeys.LANGUAGE_CODE] != null
    }

    suspend fun readLegacyLanguageCodeForMigration(): String {
        val preferences = basePreferencesFlow.first()
        return LocaleHelper.normalize(preferences[PreferencesKeys.LANGUAGE_CODE] ?: "he")
    }

    suspend fun markLanguageSeparationMigrated(interfaceLanguageCode: String) {
        val normalized = LocaleHelper.normalize(interfaceLanguageCode)
        store.edit { preferences ->
            if (preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE] == null) {
                preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE] = normalized
            }
            preferences[PreferencesKeys.LANGUAGE_SEPARATION_MIGRATED] = true
        }
    }

    // 👉 NEW LAYOUT SETTINGS UPDATE FUNCTIONS
    suspend fun updateGridTileScale(scale: Float) {
        store.edit { preferences ->
            preferences[PreferencesKeys.GRID_TILE_SCALE] = scale
        }
    }

    suspend fun updateGridTileContainerScale(scale: Float) {
        store.edit { preferences ->
            preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE] = scale
        }
    }

    suspend fun updateBarTileImageScale(scale: Float) {
        store.edit { preferences ->
            preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE] = scale
        }
    }

    suspend fun updateBarTileTitleScale(scale: Float) {
        store.edit { preferences ->
            preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE] = scale
        }
    }

    suspend fun updateActionButtonScale(scale: Float) {
        store.edit { preferences ->
            preferences[PreferencesKeys.ACTION_BUTTON_SCALE] = scale
        }
    }

    suspend fun updateShowSentenceBar(show: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SENTENCE_BAR] = show
        }
    }

    suspend fun updateShowBackButton(show: Boolean) {
        store.edit { it[PreferencesKeys.SHOW_BACK_BUTTON] = show }
    }
    suspend fun updateShowBackspaceButton(show: Boolean) {
        store.edit { it[PreferencesKeys.SHOW_BACKSPACE_BUTTON] = show }
    }
    suspend fun updateShowSpeakButton(show: Boolean) {
        store.edit { it[PreferencesKeys.SHOW_SPEAK_BUTTON] = show }
    }
    suspend fun updateHomeInActionBar(inActionBar: Boolean) {
        store.edit { it[PreferencesKeys.HOME_IN_ACTION_BAR] = inActionBar }
    }

    suspend fun updateGridColumns(columns: Int) {
        store.edit { preferences ->
            preferences[PreferencesKeys.GRID_COLUMNS] = columns
        }
    }

    suspend fun updateGridRows(rows: Int) {
        store.edit { preferences ->
            preferences[PreferencesKeys.GRID_ROWS] = rows
        }
    }

    suspend fun saveCurrentLayoutAsDefault() {
        store.edit { preferences ->
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
        store.edit { preferences ->
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

    suspend fun readExportedBoardSettings(
        boardLanguageCodeOverride: String? = null,
    ): ExportedBoardSettings {
        val preferences = basePreferencesFlow.first()
        val rawGender = preferences[PreferencesKeys.USER_GENDER]
        val normalizedGender = runCatching {
            if (rawGender != null) Gender.valueOf(rawGender).name else Gender.MALE.name
        }.getOrDefault(Gender.MALE.name)
        val normalizedBoardLanguage = LocaleHelper.normalize(
            boardLanguageCodeOverride
                ?: preferences[PreferencesKeys.LANGUAGE_CODE]
                ?: "he",
        )

        return ExportedBoardSettings(
            activeProfileId = preferences[PreferencesKeys.ACTIVE_PROFILE_ID],
            gridColumns = (preferences[PreferencesKeys.GRID_COLUMNS] ?: FACTORY_GRID_COLUMNS)
                .coerceIn(1, 8),
            gridRows = (preferences[PreferencesKeys.GRID_ROWS] ?: FACTORY_GRID_ROWS)
                .coerceIn(1, 10),
            gridTileScale = (preferences[PreferencesKeys.GRID_TILE_SCALE] ?: FACTORY_GRID_TILE_SCALE)
                .coerceIn(0.5f, 2f),
            gridTileContainerScale = (preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE]
                ?: FACTORY_GRID_TILE_CONTAINER_SCALE).coerceIn(0.5f, 1f),
            barTileImageScale = (preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE]
                ?: FACTORY_BAR_TILE_IMAGE_SCALE).coerceIn(0.5f, 2f),
            barTileTitleScale = (preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE]
                ?: FACTORY_BAR_TILE_TITLE_SCALE).coerceIn(0.5f, 2f),
            actionButtonScale = (preferences[PreferencesKeys.ACTION_BUTTON_SCALE]
                ?: FACTORY_ACTION_BUTTON_SCALE).coerceIn(0.5f, 2f),
            showSentenceBar = preferences[PreferencesKeys.SHOW_SENTENCE_BAR]
                ?: FACTORY_SHOW_SENTENCE_BAR,
            showBackButton = preferences[PreferencesKeys.SHOW_BACK_BUTTON]
                ?: FACTORY_SHOW_BACK_BUTTON,
            showBackspaceButton = preferences[PreferencesKeys.SHOW_BACKSPACE_BUTTON]
                ?: FACTORY_SHOW_BACKSPACE_BUTTON,
            showSpeakButton = preferences[PreferencesKeys.SHOW_SPEAK_BUTTON]
                ?: FACTORY_SHOW_SPEAK_BUTTON,
            homeInActionBar = preferences[PreferencesKeys.HOME_IN_ACTION_BAR]
                ?: FACTORY_HOME_IN_ACTION_BAR,
            speakOnTilePress = preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] ?: true,
            tilesUseTts = preferences[PreferencesKeys.TILES_USE_TTS] ?: true,
            userGender = normalizedGender,
            languageCode = normalizedBoardLanguage,
            boardLanguageCode = normalizedBoardLanguage,
        )
    }

    suspend fun applyExportedBoardSettings(
        settings: ExportedBoardSettings,
        resolvedActiveProfileId: String? = settings.activeProfileId,
    ) {
        val normalizedGender = runCatching {
            Gender.valueOf(settings.userGender).name
        }.getOrDefault(Gender.MALE.name)
        val normalizedBoardLanguage = settings.resolvedBoardLanguageCode()

        store.edit { preferences ->
            if (preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE] == null) {
                preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE] = LocaleHelper.normalize(
                    preferences[PreferencesKeys.LANGUAGE_CODE] ?: "he",
                )
            }
            preferences[PreferencesKeys.LANGUAGE_SEPARATION_MIGRATED] = true

            if (!resolvedActiveProfileId.isNullOrBlank()) {
                preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = resolvedActiveProfileId
            } else {
                preferences.remove(PreferencesKeys.ACTIVE_PROFILE_ID)
            }
            preferences[PreferencesKeys.GRID_COLUMNS] = settings.gridColumns.coerceIn(1, 8)
            preferences[PreferencesKeys.GRID_ROWS] = settings.gridRows.coerceIn(1, 10)
            preferences[PreferencesKeys.GRID_TILE_SCALE] = settings.gridTileScale.coerceIn(0.5f, 2f)
            preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE] =
                settings.gridTileContainerScale.coerceIn(0.5f, 1f)
            preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE] =
                settings.barTileImageScale.coerceIn(0.5f, 2f)
            preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE] =
                settings.barTileTitleScale.coerceIn(0.5f, 2f)
            preferences[PreferencesKeys.ACTION_BUTTON_SCALE] =
                settings.actionButtonScale.coerceIn(0.5f, 2f)
            preferences[PreferencesKeys.SHOW_SENTENCE_BAR] = settings.showSentenceBar
            preferences[PreferencesKeys.SHOW_BACK_BUTTON] = settings.showBackButton
            preferences[PreferencesKeys.SHOW_BACKSPACE_BUTTON] = settings.showBackspaceButton
            preferences[PreferencesKeys.SHOW_SPEAK_BUTTON] = settings.showSpeakButton
            preferences[PreferencesKeys.HOME_IN_ACTION_BAR] = settings.homeInActionBar
            preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS] = settings.speakOnTilePress
            preferences[PreferencesKeys.TILES_USE_TTS] = settings.tilesUseTts
            preferences[PreferencesKeys.USER_GENDER] = normalizedGender
            preferences[PreferencesKeys.LANGUAGE_CODE] = normalizedBoardLanguage
        }
    }

    suspend fun readRollbackSettingsSnapshot(): BoardSettingsSnapshot {
        val preferences = basePreferencesFlow.first()
        return BoardSettingsSnapshot(
            activeProfileId = preferences[PreferencesKeys.ACTIVE_PROFILE_ID],
            gridColumns = preferences[PreferencesKeys.GRID_COLUMNS],
            gridRows = preferences[PreferencesKeys.GRID_ROWS],
            gridTileScale = preferences[PreferencesKeys.GRID_TILE_SCALE],
            gridTileContainerScale = preferences[PreferencesKeys.GRID_TILE_CONTAINER_SCALE],
            barTileImageScale = preferences[PreferencesKeys.BAR_TILE_IMAGE_SCALE],
            barTileTitleScale = preferences[PreferencesKeys.BAR_TILE_TITLE_SCALE],
            actionButtonScale = preferences[PreferencesKeys.ACTION_BUTTON_SCALE],
            showSentenceBar = preferences[PreferencesKeys.SHOW_SENTENCE_BAR],
            showBackButton = preferences[PreferencesKeys.SHOW_BACK_BUTTON],
            showBackspaceButton = preferences[PreferencesKeys.SHOW_BACKSPACE_BUTTON],
            showSpeakButton = preferences[PreferencesKeys.SHOW_SPEAK_BUTTON],
            homeInActionBar = preferences[PreferencesKeys.HOME_IN_ACTION_BAR],
            speakOnTilePress = preferences[PreferencesKeys.SPEAK_ON_TILE_PRESS],
            tilesUseTts = preferences[PreferencesKeys.TILES_USE_TTS],
            userGender = preferences[PreferencesKeys.USER_GENDER],
            languageCode = preferences[PreferencesKeys.LANGUAGE_CODE],
            interfaceLanguageCode = preferences[PreferencesKeys.INTERFACE_LANGUAGE_CODE],
            languageSeparationMigrated = preferences[PreferencesKeys.LANGUAGE_SEPARATION_MIGRATED],
        )
    }

    suspend fun restoreSettingsSnapshot(snapshot: BoardSettingsSnapshot) {
        store.edit { preferences ->
            fun <T> setOrRemove(key: Preferences.Key<T>, value: T?) {
                if (value != null) {
                    preferences[key] = value
                } else {
                    preferences.remove(key)
                }
            }

            setOrRemove(PreferencesKeys.ACTIVE_PROFILE_ID, snapshot.activeProfileId)
            setOrRemove(PreferencesKeys.GRID_COLUMNS, snapshot.gridColumns)
            setOrRemove(PreferencesKeys.GRID_ROWS, snapshot.gridRows)
            setOrRemove(PreferencesKeys.GRID_TILE_SCALE, snapshot.gridTileScale)
            setOrRemove(PreferencesKeys.GRID_TILE_CONTAINER_SCALE, snapshot.gridTileContainerScale)
            setOrRemove(PreferencesKeys.BAR_TILE_IMAGE_SCALE, snapshot.barTileImageScale)
            setOrRemove(PreferencesKeys.BAR_TILE_TITLE_SCALE, snapshot.barTileTitleScale)
            setOrRemove(PreferencesKeys.ACTION_BUTTON_SCALE, snapshot.actionButtonScale)
            setOrRemove(PreferencesKeys.SHOW_SENTENCE_BAR, snapshot.showSentenceBar)
            setOrRemove(PreferencesKeys.SHOW_BACK_BUTTON, snapshot.showBackButton)
            setOrRemove(PreferencesKeys.SHOW_BACKSPACE_BUTTON, snapshot.showBackspaceButton)
            setOrRemove(PreferencesKeys.SHOW_SPEAK_BUTTON, snapshot.showSpeakButton)
            setOrRemove(PreferencesKeys.HOME_IN_ACTION_BAR, snapshot.homeInActionBar)
            setOrRemove(PreferencesKeys.SPEAK_ON_TILE_PRESS, snapshot.speakOnTilePress)
            setOrRemove(PreferencesKeys.TILES_USE_TTS, snapshot.tilesUseTts)
            setOrRemove(PreferencesKeys.USER_GENDER, snapshot.userGender)
            setOrRemove(PreferencesKeys.LANGUAGE_CODE, snapshot.languageCode)
            setOrRemove(PreferencesKeys.INTERFACE_LANGUAGE_CODE, snapshot.interfaceLanguageCode)
            setOrRemove(
                PreferencesKeys.LANGUAGE_SEPARATION_MIGRATED,
                snapshot.languageSeparationMigrated,
            )
        }
    }
}