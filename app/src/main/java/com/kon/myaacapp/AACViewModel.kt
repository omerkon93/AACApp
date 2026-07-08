package com.kon.myaacapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.io.File

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

        viewModelScope.launch {
            // 1. Run the legacy SQLite migration (if an old DB still exists on the device)
            repository.completeLegacyMigration()

            // 2. The Single-Source Auto-Extractor
            val firstBootFlag = File(application.filesDir, "first_boot_complete.flag")
            if (!firstBootFlag.exists()) {
                // Extract the unified database from assets to the writable hard drive
                val success = backupService.importFromAssets("initial_data.zip")
                if (success) {
                    firstBootFlag.createNewFile() // Drop the flag so it never overwrites user data again
                    profileRepository.reload()
                    // Assuming your repository has a reload or refresh function to read the new local files:
                    repository.loadAllDefinitions()
                }
            }
        }
    }

    val profiles: StateFlow<List<UserProfile>> = profileRepository.profiles
    val activeProfile: StateFlow<UserProfile?> = profileRepository.activeProfile

    init {
        viewModelScope.launch {
            activeProfile.collect { profile ->
                Log.d("Debug_AAC", "Active Profile ID: ${profile?.profileId}")
            }
        }
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            profileRepository.createProfile(name)
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.switchProfile(profileId)
            resetToHome()
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
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
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val languageCode: StateFlow<String> = settingsRepository.languageCodeFlow
        .map { lang -> LocaleHelper.normalize(lang) }
        .onEach { lang -> ttsHelper.setLanguage(lang) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "he")

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
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTiles: StateFlow<List<CombinedTile>> = languageCode
        .flatMapLatest { lang -> repository.getAllDefinitionsAsCombinedTiles(lang) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories: StateFlow<List<CombinedTile>> = allTiles
        .map { list -> list.filter { it.isCategory } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSpeakOnTilePress(speak: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSpeakOnTilePress(speak)
        }
    }

    val languageDownloadStatus = languageDownloadHelper.downloadStatus

    suspend fun updateLanguageCode(lang: String) {
        settingsRepository.updateLanguageCode(lang)
        resetToHome()
    }

    fun downloadAndSetLanguage(lang: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            languageDownloadHelper.downloadLanguage(lang) { success ->
                if (success) {
                    viewModelScope.launch {
                        updateLanguageCode(lang)
                        onComplete(true)
                    }
                } else {
                    onComplete(false)
                }
            }
        }
    }

    fun updateUserGender(gender: Gender) {
        tileService.setUserGender(gender, viewModelScope)
    }

    private val _recordingTileId = MutableStateFlow<String?>(null)
    val recordingTileId: StateFlow<String?> = _recordingTileId.asStateFlow()

    fun startQuickRecording(tileId: String) {
        _recordingTileId.value = tileId
        audioService.startRecording(tileId, languageCode.value)
    }

    fun stopQuickRecording(tileId: String) {
        viewModelScope.launch {
            audioService.stopRecording()
            _recordingTileId.value = null
            // The path is standardized in AudioRecordingService: "audio_$tileId.wav"
            val outputDir = File(getApplication<Application>().filesDir, "audio_tiles/${languageCode.value}")
            val outputFile = File(outputDir, "audio_$tileId.wav")
            if (outputFile.exists()) {
                updateTileAudioUri(tileId, outputFile.absolutePath)
            }
        }
    }

    private val _selectedSentence = MutableStateFlow<List<CombinedTile>>(emptyList())
    val selectedSentence: StateFlow<List<CombinedTile>> = _selectedSentence.asStateFlow()

    private val _importExportStatus = MutableStateFlow<String?>(null)
    val importExportStatus: StateFlow<String?> = _importExportStatus.asStateFlow()

    fun resetToDefault(context: android.content.Context) {
        viewModelScope.launch {
            _importExportStatus.value = context.getString(R.string.resetting)
            val success = backupService.importFromAssets("initial_data.zip")
            if (success) {
                profileRepository.reload()
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
                // FOLDER: Navigate to the linked category, OR fallback to its own ID
                val targetId = tile.definition.defaultLinkedCategoryId ?: tile.definition.id
                onNavigateToCategory(targetId)
            }
            TileType.CONNECTOR -> {
                // CONNECTOR: Add to sentence, speak, AND navigate.
                addTileToSentence(tile)
                speakTile(tile)
                tile.definition.defaultLinkedCategoryId?.let { onNavigateToCategory(it) }
            }
            TileType.QUICK_FIRE -> {
                // QUICK FIRE: Speak immediately, do NOT add to sentence, do not navigate.
                speakTile(tile)
            }
            else -> {
                // BASIC (Fallback): Add to sentence.
                addTileToSentence(tile)
            }
        }

        // Always log the click for statistics
        viewModelScope.launch {
            repository.incrementClickCount(tile.definition.id, tile.layoutState.parentId, languageCode.value)
        }
    }

    private fun speakTile(tile: CombinedTile) {
        val textToSpeak = if (userGender.value == Gender.FEMALE) {
            tile.definition.ttsTextFeminine ?: tile.definition.ttsText ?: tile.definition.label
        } else {
            tile.definition.ttsText ?: tile.definition.label
        }

        val audioUri = tile.definition.audioUri
        if (audioUri != null) {
            audioService.playRecording(audioUri)
        } else {
            ttsHelper.speak(textToSpeak)
        }
    }

    fun addTileToSentence(tile: CombinedTile) {
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
                id = if (id.isNullOrBlank()) java.util.UUID.randomUUID().toString() else id,
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
            // Also update layout state if parentId exists
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
            // Remove from all layouts if deleted from dictionary
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

    fun updateTileAudioUri(tileId: String, audioUri: String?) {
        viewModelScope.launch {
            repository.getTileById(tileId, languageCode.value)?.let { tile ->
                repository.updateTile(tile.copy(audioUri = audioUri))
            }
        }
    }

    fun exportDatabase(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            val success = backupService.exportDatabase(contentResolver, uri)
            _importExportStatus.value = if (success) {
                getApplication<Application>().getString(R.string.export_success)
            } else {
                getApplication<Application>().getString(R.string.export_failed)
            }
        }
    }

    fun importDatabase(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            val success = backupService.importDatabase(contentResolver, uri)
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

    // --- Admin Audit Logic ---

    private val _adminSearchQuery = MutableStateFlow("")
    val adminSearchQuery = _adminSearchQuery.asStateFlow()

    private val _adminAuditFilter = MutableStateFlow(AdminAuditFilter.ALL)
    val adminAuditFilter = _adminAuditFilter.asStateFlow()

    fun setAdminSearchQuery(query: String) {
        _adminSearchQuery.value = query
    }

    fun setAdminAuditFilter(filter: AdminAuditFilter) {
        _adminAuditFilter.value = filter
    }

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
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Advanced Analytics Logic ---

    private val _selectedTimeFilter = MutableStateFlow(AnalyticsTimeFilter.ALL_TIME)
    val selectedTimeFilter: StateFlow<AnalyticsTimeFilter> = _selectedTimeFilter.asStateFlow()

    fun setTimeFilter(filter: AnalyticsTimeFilter) {
        _selectedTimeFilter.value = filter
    }

    fun resetStatistics(context: android.content.Context) {
        viewModelScope.launch {
            repository.clearAllStatistics()
            _importExportStatus.value = context.getString(R.string.stats_reset_success)
        }
    }

    fun removeAllAudio(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
    val filteredClickEvents: Flow<List<TileClickEvent>> = _selectedTimeFilter.flatMapLatest { filter ->
        val now = System.currentTimeMillis()
        val startTime = when (filter) {
            AnalyticsTimeFilter.DAILY -> getStartOfDay()
            AnalyticsTimeFilter.WEEKLY -> getStartOfWeek()
            AnalyticsTimeFilter.MONTHLY -> getStartOfMonth()
            AnalyticsTimeFilter.YEARLY -> getStartOfYear()
            AnalyticsTimeFilter.ALL_TIME -> 0L
        }
        repository.getClickEventsBetween(startTime, now)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
}