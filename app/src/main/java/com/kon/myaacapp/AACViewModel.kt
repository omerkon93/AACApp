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
    private val ttsHelper: TextToSpeechHelper
    
    val tileService: AACTileService
    val audioService: AudioRecordingService
    val backupService: BackupService

    init {
        val database = AACDatabase.getDatabase(application)
        repository = AACRepository(database.aacTileDao())
        ttsHelper = TextToSpeechHelper(application)
        
        tileService = AACTileService(repository)
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

    private val _selectedSentence = MutableStateFlow<List<AACTile>>(emptyList())
    val selectedSentence: StateFlow<List<AACTile>> = _selectedSentence.asStateFlow()

    private val _importExportStatus = MutableStateFlow<String?>(null)
    val importExportStatus: StateFlow<String?> = _importExportStatus.asStateFlow()

    private fun prepopulateIfEmpty() {
        viewModelScope.launch {
            if (repository.isEmpty()) {
                val defaultTiles = backupService.loadTilesFromAssets("default_tiles.json")
                if (defaultTiles != null) {
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
            
            // Speak the resolved gender-specific text
            val speechText = tileService.getTTSText(tile)
            ttsHelper.speak(speechText)

            navigateId?.let { onNavigateToCategory(it) }
        }
    }

    fun addTileToSentence(tile: AACTile) {
        _selectedSentence.value = _selectedSentence.value + tile
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
        val fullText = _selectedSentence.value.joinToString(" ") { tile ->
            tileService.getTTSText(tile)
        }
        if (fullText.isNotEmpty()) {
            ttsHelper.speak(fullText)
        }
    }

    fun navigateBack() {
        _currentParentId.value = null
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
        cellIndex: Int? = null
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
                cellIndex = cellIndex
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
            repository.deleteTile(tile)
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