@file:Suppress("SpellCheckingInspection")

package com.kon.myaacapp

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.core.locale.LanguageDownloadHelper
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AACTileService
import com.kon.myaacapp.domain.service.Gender
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TextToSpeechHelper
import com.kon.myaacapp.service.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

enum class AnalyticsTimeFilter {
    DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME
}

enum class AdminAuditFilter {
    ALL, MISSING_AUDIO, MISSING_TTS, MISSING_IMAGE, UNUSED, HIDDEN
}

@OptIn(ExperimentalCoroutinesApi::class)
class AACViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AACRepository
    private val settingsRepository: SettingsRepository
    private val profileRepository: ProfileRepository
    private val ttsHelper: TextToSpeechHelper
    private val languageDownloadHelper: LanguageDownloadHelper

    val tileService: AACTileService
    val audioService: AudioRecordingService
    val backupService: BackupService

    init {
        val database = AACDatabase.getDatabase(application)
        settingsRepository = SettingsRepository(application)
        profileRepository = ProfileRepository(application, settingsRepository, viewModelScope)
        repository = AACRepository(database.aacTileDao(), application, profileRepository)
        ttsHelper = TextToSpeechHelper(application)
        languageDownloadHelper = LanguageDownloadHelper(application)

        tileService = AACTileService(settingsRepository, viewModelScope)
        audioService = AudioRecordingService(application)
        backupService = BackupService(application, repository)

        // THE MASTER BOOT SEQUENCE
        viewModelScope.launch(Dispatchers.IO) {
            val firstBootFlag = File(application.filesDir, "first_boot_complete.flag")

            // 1. Extract the zip FIRST
            if (!firstBootFlag.exists()) {
                val success = backupService.importFromAssets("initial_data.zip")
                if (success) {
                    firstBootFlag.createNewFile()
                }
            }

            // 2. NOW run the legacy migration. If the zip contained an old SQLite
            // database with your manual tiles, they will be safely salvaged into JSONs now!
            repository.completeLegacyMigration()

            // 3. Load everything into memory and draw the UI
            profileRepository.reload()
            repository.reload()
        }
    }

    val profiles: StateFlow<List<UserProfile>> =
        profileRepository.profiles

    val activeProfile: StateFlow<UserProfile?> =
        profileRepository.activeProfile

    fun createProfile(
        name: String,
        creationMode: ProfileCreationMode,
    ) {
        viewModelScope.launch {
            profileRepository.createProfile(
                name = name,
                creationMode = creationMode,
            )

            resetToHome()
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.switchProfile(profileId)
            resetToHome()
        }
    }

    fun deleteProfile(
        profileId: String,
    ) {
        viewModelScope.launch {
            repository.deleteProfileAnalytics(
                profileId = profileId,
            )

            profileRepository.deleteProfile(
                profileId = profileId,
            )

            resetToHome()
        }
    }

    private val _currentParentId = MutableStateFlow<String?>(null)
    val currentParentId: StateFlow<String?> = _currentParentId.asStateFlow()

    private val navigationStack = mutableListOf<String?>()

    fun setCategory(parentId: String?) {
        if (_currentParentId.value != parentId) {
            navigationStack.add(_currentParentId.value)
            _currentParentId.value = parentId
        }
    }

    fun navigateBack() {
        if (navigationStack.isNotEmpty()) {
            _currentParentId.value = navigationStack.removeAt(navigationStack.size - 1)
        } else {
            _currentParentId.value = null
        }
    }

    fun resetToHome() {
        navigationStack.clear()
        _currentParentId.value = null
    }

    val speakOnTilePress: StateFlow<Boolean> = settingsRepository.speakOnTilePressFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val languageCode: StateFlow<String> = settingsRepository.languageCodeFlow
        .map { lang -> LocaleHelper.normalize(lang) }
        .onEach { lang -> ttsHelper.setLanguage(lang) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "he")

    val userGender: StateFlow<Gender> = tileService.userGender

    fun getTilesByParentId(parentId: String?): Flow<List<CombinedTile>> {
        return repository.getCombinedTiles(parentId, languageCode.value)
    }

    val currentTiles: StateFlow<List<CombinedTile>> = combine(_currentParentId, languageCode) { parentId, lang ->
        parentId to lang
    }
        .flatMapLatest { (parentId, lang) ->
            repository.getCombinedTiles(parentId, lang)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTiles: StateFlow<List<CombinedTile>> = languageCode
        .flatMapLatest { lang -> repository.getAllDefinitionsAsCombinedTiles(lang) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CombinedTile>> = allTiles
        .map { list -> list.filter { it.isCategory } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSpeakOnTilePress(speak: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSpeakOnTilePress(speak)
        }
    }

    val languageDownloadStatus = languageDownloadHelper.downloadStatus

    private suspend fun updateLanguageCode(
        requestedLanguage: String,
    ): Boolean {
        val normalizedLanguage =
            LocaleHelper.normalize(requestedLanguage)

        /*
         * Update the selected language first. The UI locale must not depend on
         * whether the optional tile dictionary is currently available.
         */
        settingsRepository.updateLanguageCode(
            normalizedLanguage
        )

        /*
         * Reload definitions and create the language layout when definitions
         * exist. An empty dictionary must not cancel the locale change.
         */
        val tilesPrepared = repository.prepareLanguage(
            normalizedLanguage
        )

        if (!tilesPrepared) {
            Log.w(
                "AACViewModel",
                "UI switched to $normalizedLanguage, " +
                        "but no tile definitions were found."
            )
        }

        resetToHome()

        return true
    }

    fun downloadAndSetLanguage(
        requestedLanguage: String,
        onComplete: (Boolean) -> Unit,
    ) {
        val normalizedLanguage =
            LocaleHelper.normalize(requestedLanguage)

        if (this.languageCode.value == normalizedLanguage) {
            onComplete(true)
            return
        }

        languageDownloadHelper.downloadLanguage(
            normalizedLanguage
        ) { downloadSucceeded ->
            if (!downloadSucceeded) {
                viewModelScope.launch {
                    onComplete(false)
                }

                return@downloadLanguage
            }

            viewModelScope.launch {
                updateLanguageCode(normalizedLanguage)
                onComplete(true)
            }
        }
    }

    fun updateUserGender(gender: Gender) {
        tileService.setUserGender(gender, viewModelScope)
    }

    @Suppress("unused")
    private val _recordingTileId = MutableStateFlow<String?>(null)

    @Suppress("unused")
    val recordingTileId: StateFlow<String?> = _recordingTileId.asStateFlow()

    @Suppress("unused")
    fun startQuickRecording(tileId: String) {
        _recordingTileId.value = tileId
        audioService.startRecording(tileId, languageCode.value)
    }

    @Suppress("unused")
    fun stopQuickRecording(tileId: String) {
        viewModelScope.launch {
            audioService.stopRecording()
            _recordingTileId.value = null

            withContext(Dispatchers.IO) {
                val outputDir = File(getApplication<Application>().filesDir, "audio_tiles/${languageCode.value}")
                val outputFile = File(outputDir, "audio_$tileId.wav")
                if (outputFile.exists()) {
                    updateTileAudioUri(tileId, outputFile.absolutePath)
                }
            }
        }
    }

    private val _selectedSentence = MutableStateFlow<List<CombinedTile>>(emptyList())
    val selectedSentence: StateFlow<List<CombinedTile>> = _selectedSentence.asStateFlow()

    private val _importExportStatus = MutableStateFlow<String?>(null)
    val importExportStatus: StateFlow<String?> = _importExportStatus.asStateFlow()

    fun resetToDefault(context: Context) {
        viewModelScope.launch {
            _importExportStatus.value = context.getString(R.string.resetting)
            val success = withContext(Dispatchers.IO) { backupService.importFromAssets("initial_data.zip") }
            if (success) {
                // Ensure any databases in the zip are migrated
                repository.completeLegacyMigration()
                profileRepository.reload()

                // Do NOT pass forceRepopulate = true. Respect the imported layout!
                repository.reload()
            }
            _importExportStatus.value = if (success) {
                context.getString(R.string.reset_success)
            } else {
                context.getString(R.string.reset_failed)
            }
        }
    }

    fun selectTile(tile: CombinedTile, onNavigateToCategory: (String) -> Unit) {
        val type = tile.definition.resolvedType

        when (type) {
            TileType.FOLDER -> {
                if (speakOnTilePress.value) speakTile(tile)
                val targetId = tile.definition.defaultLinkedCategoryId ?: tile.definition.id
                onNavigateToCategory(targetId)
            }
            TileType.CONNECTOR -> {
                addTileToSentence(tile)
                if (speakOnTilePress.value) speakTile(tile)
                tile.definition.defaultLinkedCategoryId?.let { onNavigateToCategory(it) }
            }
            TileType.QUICK_FIRE -> {
                speakTile(tile)
            }
            else -> {
                addTileToSentence(tile)
                if (speakOnTilePress.value) speakTile(tile)
            }
        }

        viewModelScope.launch {
            repository.incrementClickCount(tile.definition.id, tile.layoutState.parentId, languageCode.value)
        }
    }

    private fun speakTile(tile: CombinedTile) {
        val textToSpeak = if (userGender.value == Gender.FEMALE) {
            tile.definition.ttsTextFeminine?.takeIf { it.isNotBlank() }
                ?: tile.definition.ttsText.takeIf { it.isNotBlank() }
                ?: tile.definition.label
        } else {
            tile.definition.ttsText.takeIf { it.isNotBlank() }
                ?: tile.definition.label
        }

        val audioUri = tile.definition.audioUri
        if (audioUri != null) {
            audioService.playRecording(audioUri)
        } else {
            ttsHelper.speak(textToSpeak)
        }
    }

    private fun addTileToSentence(tile: CombinedTile) {
        _selectedSentence.value += tile
    }

    fun backspaceSentence() {
        val currentList = _selectedSentence.value
        if (currentList.isNotEmpty()) {
            _selectedSentence.value = currentList.dropLast(1)
        }
    }

    fun clearSentence() {
        _selectedSentence.value = emptyList()
    }

    fun speakSentence() {
        viewModelScope.launch {
            audioService.speakSentence(_selectedSentence.value, ttsHelper, tileService)
        }
    }

    fun playPreviewAudio(ttsText: String, audioUri: String?) {
        viewModelScope.launch {
            if (audioUri != null) {
                audioService.playRecording(audioUri)
            } else if (ttsText.isNotBlank()) {
                ttsHelper.speak(ttsText)
            }
        }
    }

    fun addTile(
        id: String? = null,
        label: String,
        ttsText: String,
        emoji: String?,
        imageUri: String?,
        isCategory: Boolean,
        parentId: String? = _currentParentId.value,
        backgroundColorHex: String? = null,
        partOfSpeech: String? = null,
        isQuickFire: Boolean = false,
        linkedCategoryId: String? = null,
        labelFeminine: String? = null,
        ttsTextFeminine: String? = null,
        grammaticalGender: String? = null,
        audioUri: String? = null,
        cellIndex: Int? = null,
        isHidden: Boolean = false,
    ) {
        viewModelScope.launch {
            val nextIndex = cellIndex ?: (repository.getCombinedTiles(parentId, languageCode.value).firstOrNull()?.size ?: 0)

            val newTile = AACTile(
                id = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id,
                label = label,
                ttsText = ttsText,
                emoji = emoji,
                imageUri = imageUri,
                isCategory = isCategory,
                parentId = parentId,
                backgroundColorHex = backgroundColorHex,
                partOfSpeech = partOfSpeech,
                isQuickFire = isQuickFire,
                linkedCategoryId = linkedCategoryId,
                labelFeminine = labelFeminine,
                ttsTextFeminine = ttsTextFeminine,
                grammaticalGender = grammaticalGender,
                audioUri = audioUri,
                cellIndex = nextIndex,
                isHidden = isHidden,
                languageCode = languageCode.value
            )
            repository.insertTile(newTile)
        }
    }

    fun updateTile(tile: AACTile) {
        viewModelScope.launch {
            repository.updateTile(tile)
            repository.updateTileIndex(tile.id, tile.parentId, tile.cellIndex ?: 0)
            repository.updateTileVisibility(tile.id, tile.parentId, tile.isHidden)
        }
    }

    fun swapTilePositions(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.swapTilesByIndex(_currentParentId.value, fromIndex, toIndex)
        }
    }

    fun attachTileToCategory(tileId: String, parentId: String?, cellIndex: Int?) {
        viewModelScope.launch {
            repository.attachTileToCategory(tileId, parentId, languageCode.value, cellIndex)
        }
    }

    fun removeTileFromCategory(tileId: String, parentId: String?) {
        viewModelScope.launch {
            repository.removeTileFromCategory(tileId, parentId, languageCode.value)
        }
    }

    fun deleteTile(tile: AACTile) {
        viewModelScope.launch {
            tile.audioUri?.let { audioService.deleteRecording(it) }
            repository.deleteTile(tile)
            profiles.value.forEach { profile ->
                val newLayout = profile.layout.toMutableMap()
                val keysToRemove = newLayout.keys.filter { it.endsWith("_" + tile.id) }
                if (keysToRemove.isNotEmpty()) {
                    keysToRemove.forEach { newLayout.remove(it) }
                    profileRepository.updateActiveProfile(profile.copy(layout = newLayout))
                }
            }
        }
    }

    private fun updateTileAudioUri(tileId: String, audioUri: String?) {
        viewModelScope.launch {
            repository.getTileById(tileId, languageCode.value)?.let { tile ->
                repository.updateTile(tile.copy(audioUri = audioUri))
            }
        }
    }

    @Suppress("unused")
    fun exportDatabase(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { backupService.exportDatabase(contentResolver, uri) }
            _importExportStatus.value = if (success) {
                getApplication<Application>().getString(R.string.export_success)
            } else {
                getApplication<Application>().getString(R.string.export_failed)
            }
        }
    }

    fun importDatabase(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { backupService.importDatabase(contentResolver, uri) }
            if (success) {
                profileRepository.reload()
                repository.reload()
            }
            _importExportStatus.value = if (success) {
                getApplication<Application>().getString(R.string.import_success)
            } else {
                getApplication<Application>().getString(R.string.import_failed)
            }
        }
    }

    fun clearImportExportStatus() {
        _importExportStatus.value = null
    }

    @Suppress("unused")
    private val _adminSearchQuery = MutableStateFlow("")

    @Suppress("unused")
    val adminSearchQuery = _adminSearchQuery.asStateFlow()

    @Suppress("unused")
    private val _adminAuditFilter = MutableStateFlow(AdminAuditFilter.ALL)

    @Suppress("unused")
    val adminAuditFilter = _adminAuditFilter.asStateFlow()

    @Suppress("unused")
    fun setAdminSearchQuery(query: String) {
        _adminSearchQuery.value = query
    }

    @Suppress("unused")
    fun setAdminAuditFilter(filter: AdminAuditFilter) {
        _adminAuditFilter.value = filter
    }

    @Suppress("unused")
    val filteredTilesForAdmin: StateFlow<List<CombinedTile>> = combine(
        allTiles, _adminSearchQuery, _adminAuditFilter
    ) { tiles, query, filter ->
        tiles.filter { tile ->
            val matchesQuery = if (query.isBlank()) true else {
                tile.definition.label.contains(query, ignoreCase = true) ||
                        tile.definition.ttsText.contains(query, ignoreCase = true)
            }
            val matchesFilter = when (filter) {
                AdminAuditFilter.ALL -> true
                AdminAuditFilter.MISSING_AUDIO -> tile.definition.audioUri.isNullOrBlank()
                AdminAuditFilter.MISSING_TTS -> tile.definition.ttsText.isBlank()
                AdminAuditFilter.MISSING_IMAGE -> tile.definition.imageUri.isNullOrBlank()
                AdminAuditFilter.UNUSED -> tile.layoutState.clickCount == 0
                AdminAuditFilter.HIDDEN -> tile.layoutState.isHidden
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTimeFilter = MutableStateFlow(AnalyticsTimeFilter.ALL_TIME)
    val selectedTimeFilter: StateFlow<AnalyticsTimeFilter> = _selectedTimeFilter.asStateFlow()

    fun setTimeFilter(filter: AnalyticsTimeFilter) {
        _selectedTimeFilter.value = filter
    }

    @Suppress("unused")
    fun resetStatistics(context: Context) {
        viewModelScope.launch {
            repository.clearAllStatistics()
            _importExportStatus.value = context.getString(R.string.stats_reset_success)
        }
    }

    @Suppress("unused")
    fun removeAllAudio(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val tiles = repository.getAllTilesSync()
            tiles.forEach { tile ->
                if (tile.audioUri != null) {
                    updateTile(tile.copy(audioUri = null))
                }
            }
            _importExportStatus.value = context.getString(R.string.audio_removed_success)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredClickEvents: StateFlow<List<TileClickEvent>> = _selectedTimeFilter.flatMapLatest { filter ->
        val now = System.currentTimeMillis()
        val startTime = when (filter) {
            AnalyticsTimeFilter.DAILY -> getStartOfDay()
            AnalyticsTimeFilter.WEEKLY -> getStartOfWeek()
            AnalyticsTimeFilter.MONTHLY -> getStartOfMonth()
            AnalyticsTimeFilter.YEARLY -> getStartOfYear()
            AnalyticsTimeFilter.ALL_TIME -> 0L
        }
        repository.getClickEventsBetween(startTime, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun getStartOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun getStartOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun getStartOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun getStartOfYear(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    override fun onCleared() {
        super.onCleared()
        languageDownloadHelper.unregister()
        ttsHelper.shutdown()
    }

    fun exportAndShareDatabase(context: Context, onReady: (Uri?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupsDir = File(context.cacheDir, "backups")
                if (!backupsDir.exists()) backupsDir.mkdirs()

                backupsDir.listFiles()?.forEach { it.delete() }

                val backupFile = File(backupsDir, "myaac_backup_${System.currentTimeMillis()}.zip")
                val uri = Uri.fromFile(backupFile)

                val success = backupService.exportDatabase(context.contentResolver, uri)

                if (success) {
                    val secureUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        backupFile
                    )

                    withContext(Dispatchers.Main) {
                        onReady(secureUri)
                    }
                } else {
                    withContext(Dispatchers.Main) { onReady(null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onReady(null) }
            }
        }
    }
}