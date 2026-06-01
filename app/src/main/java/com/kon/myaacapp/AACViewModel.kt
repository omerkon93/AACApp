package com.kon.myaacapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
        
        prepopulateIfEmpty()
    }

    private val _currentParentId = MutableStateFlow<String?>(null)
    val currentParentId: StateFlow<String?> = _currentParentId.asStateFlow()
    
    fun setCategory(parentId: String?) {
        _currentParentId.value = parentId
    }

    val currentTiles: StateFlow<List<AACTile>> = _currentParentId
        .flatMapLatest { parentId ->
            repository.getTilesByParentId(parentId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTiles: StateFlow<List<AACTile>> = repository.getAllTiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories: StateFlow<List<AACTile>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speakOnTilePress: StateFlow<Boolean> = settingsRepository.speakOnTilePressFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun updateSpeakOnTilePress(speak: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSpeakOnTilePress(speak)
        }
    }

    private val _selectedSentence = MutableStateFlow<List<AACTile>>(emptyList())
    val selectedSentence: StateFlow<List<AACTile>> = _selectedSentence.asStateFlow()

    private val _importExportStatus = MutableStateFlow<String?>(null)
    val importExportStatus: StateFlow<String?> = _importExportStatus.asStateFlow()

    private fun prepopulateIfEmpty() {
        viewModelScope.launch {
            if (repository.isEmpty()) {
                backupService.loadTilesFromAssets("default_tiles.json")?.let { defaultTiles ->
                    repository.insertTiles(defaultTiles)
                }
            }
        }
    }

    fun selectTile(tile: AACTile, onNavigateToCategory: (String) -> Unit) {
        viewModelScope.launch {
            val (shouldAdd, navigateId) = tileService.handleTilePress(tile)
            
            if (shouldAdd) {
                addTileToSentence(tile)
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
                id = java.util.UUID.randomUUID().toString(),
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
            _importExportStatus.value = if (success) "Database exported successfully" else "Export failed"
        }
    }

    fun importDatabase(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            val success = backupService.importDatabase(contentResolver, uri)
            _importExportStatus.value = if (success) "Database imported successfully" else "Import failed"
        }
    }

    fun clearImportExportStatus() {
        _importExportStatus.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}