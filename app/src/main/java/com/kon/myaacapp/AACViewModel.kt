package com.kon.myaacapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AnalyticsTimeFilter {
    DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME
}

@OptIn(ExperimentalCoroutinesApi::class)
class AACViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AACRepository
    private val settingsRepository: SettingsRepository
    private val ttsHelper: TextToSpeechHelper
    
    val tileService: AACTileService
    val audioService: AudioRecordingService
    val backupService: BackupService

    init {
        val database = AACDatabase.getDatabase(application)
        repository = AACRepository(database.aacTileDao())
        settingsRepository = SettingsRepository(application)
        ttsHelper = TextToSpeechHelper(application)
        
        tileService = AACTileService(repository, settingsRepository, viewModelScope)
        audioService = AudioRecordingService(application)
        backupService = BackupService(application, repository)
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

    fun navigateUp() {
        navigateBack()
    }

    val speakOnTilePress: StateFlow<Boolean> = settingsRepository.speakOnTilePressFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val languageCode: StateFlow<String> = settingsRepository.languageCodeFlow
        .onEach { lang -> ttsHelper.setLanguage(lang) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "he")

    val userGender: StateFlow<Gender> = tileService.userGender

    fun getTilesByParentId(parentId: String?): Flow<List<AACTile>> {
        return repository.getTilesByParentId(parentId, languageCode.value)
    }

    val currentTiles: StateFlow<List<AACTile>> = combine(_currentParentId, languageCode) { parentId, lang ->
            parentId to lang
        }
        .flatMapLatest { (parentId, lang) ->
            repository.getTilesByParentId(parentId, lang)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTiles: StateFlow<List<AACTile>> = languageCode
        .flatMapLatest { lang -> repository.getAllTiles(lang) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories: StateFlow<List<AACTile>> = languageCode
        .flatMapLatest { lang -> repository.getAllCategories(lang) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSpeakOnTilePress(speak: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSpeakOnTilePress(speak)
        }
    }

    suspend fun updateLanguageCode(lang: String) {
        settingsRepository.updateLanguageCode(lang)
    }

    fun updateUserGender(gender: Gender) {
        tileService.setUserGender(gender, viewModelScope)
    }

    private val _selectedSentence = MutableStateFlow<List<AACTile>>(emptyList())
    val selectedSentence: StateFlow<List<AACTile>> = _selectedSentence.asStateFlow()

    private val _importExportStatus = MutableStateFlow<String?>(null)
    val importExportStatus: StateFlow<String?> = _importExportStatus.asStateFlow()

    fun resetToDefault(context: android.content.Context) {
        viewModelScope.launch {
            _importExportStatus.value = context.getString(R.string.resetting)
            val success = backupService.importFromAssets("initial_data.zip")
            _importExportStatus.value = if (success) {
                context.getString(R.string.reset_success)
            } else {
                context.getString(R.string.reset_failed)
            }
        }
    }

    fun selectTile(tile: AACTile, onNavigateToCategory: (String) -> Unit) {
        viewModelScope.launch {
            val (shouldAdd, navigateId) = tileService.handleTilePress(tile)
            
            if (shouldAdd) {
                addTileToSentence(tile)
                repository.incrementClickCount(tile.id)
            }
            
            val shouldSpeak = if (tile.isQuickFire) {
                true // ALWAYS speak quickfire
            } else {
                // If it was added to sentence (or is a category that doesn't add),
                // only speak if setting is ON.
                speakOnTilePress.value
            }

            if (shouldSpeak) {
                if (tile.audioUri != null) {
                    audioService.playRecording(tile.audioUri)
                } else {
                    val speechText = tileService.getTTSText(tile)
                    ttsHelper.speak(speechText)
                }
            }

            navigateId?.let { onNavigateToCategory(it) }
        }
    }

    fun addTileToSentence(tile: AACTile) {
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
        id: String? = null, // <--- 1. ADD THIS NEW PARAMETER
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
    ) {
        viewModelScope.launch {
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
                cellIndex = cellIndex,
                languageCode = languageCode.value
            )
            repository.insertTile(newTile)
        }
    }

    fun updateTile(tile: AACTile) {
        viewModelScope.launch {
            repository.updateTile(tile)
        }
    }

    fun deleteTile(tile: AACTile) {
        viewModelScope.launch {
            tile.audioUri?.let { audioService.deleteRecording(it) }
            repository.deleteTile(tile)
        }
    }

    fun updateTileAudioUri(tileId: String, audioUri: String?) {
        viewModelScope.launch {
            repository.getTileById(tileId)?.let { tile ->
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
        ttsHelper.shutdown()
    }
}
