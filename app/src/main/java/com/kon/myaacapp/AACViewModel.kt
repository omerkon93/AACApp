@file:Suppress("SpellCheckingInspection")

package com.kon.myaacapp

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.kon.myaacapp.domain.model.AdminAuditFilter
import com.kon.myaacapp.domain.model.AnalyticsTimeFilter
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AACTileService
import com.kon.myaacapp.domain.service.AnalyticsManager
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.service.Gender
import com.kon.myaacapp.domain.service.QuickRecordingManager
import com.kon.myaacapp.domain.service.SentenceManager
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AACViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AACRepository
    private val settingsRepository: SettingsRepository
    private val profileRepository: ProfileRepository
    private val ttsHelper: TextToSpeechHelper
    private val languageDownloadHelper: LanguageDownloadHelper
    private val analyticsManager: AnalyticsManager
    private val sentenceManager: SentenceManager
    private val quickRecordingManager: QuickRecordingManager
    private val backupManager: BackupManager
    private val startupCoordinator: AppStartupCoordinator

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
        analyticsManager = AnalyticsManager(
            repository = repository,
            scope = viewModelScope,
        )

        tileService = AACTileService(
            settingsRepository = settingsRepository,
            scope = viewModelScope,
        )

        audioService = AudioRecordingService(application)

        quickRecordingManager = QuickRecordingManager(
            context = application,
            repository = repository,
            audioService = audioService,
            scope = viewModelScope,
        )

        backupService = BackupService(
            application,
            repository,
        )

        startupCoordinator = AppStartupCoordinator(
            context = application,
            backupService = backupService,
            repository = repository,
            profileRepository = profileRepository,
        )

        backupManager = BackupManager(
            application = application,
            backupService = backupService,
            repository = repository,
            profileRepository = profileRepository,
            scope = viewModelScope,
        )

        sentenceManager = SentenceManager(
            audioService = audioService,
            ttsHelper = ttsHelper,
            tileService = tileService,
            scope = viewModelScope,
        )

        viewModelScope.launch {
            startupCoordinator.initialize()
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

            sentenceManager.clear()
            resetToHome()
        }
    }

    fun switchProfile(
        profileId: String,
    ) {
        viewModelScope.launch {
            profileRepository.switchProfile(profileId)

            sentenceManager.clear()
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

            sentenceManager.clear()
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

    // 👉 NEW LAYOUT SETTINGS STATE FLOWS
    val gridTileScale: StateFlow<Float> = settingsRepository.gridTileScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val gridTileContainerScale: StateFlow<Float> = settingsRepository.gridTileContainerScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val barTileImageScale: StateFlow<Float> = settingsRepository.barTileImageScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val barTileTitleScale: StateFlow<Float> = settingsRepository.barTileTitleScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val actionButtonScale: StateFlow<Float> = settingsRepository.actionButtonScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val showSentenceBar: StateFlow<Boolean> = settingsRepository.showSentenceBarFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showBackButton: StateFlow<Boolean> = settingsRepository.showBackButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showBackspaceButton: StateFlow<Boolean> = settingsRepository.showBackspaceButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showSpeakButton: StateFlow<Boolean> = settingsRepository.showSpeakButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val homeInActionBar: StateFlow<Boolean> = settingsRepository.homeInActionBarFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gridColumns: StateFlow<Int> = settingsRepository.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val gridRows: StateFlow<Int> = settingsRepository.gridRowsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    // 👉 NEW LAYOUT SETTINGS UPDATE FUNCTIONS
    fun updateGridTileScale(scale: Float) {
        viewModelScope.launch { settingsRepository.updateGridTileScale(scale) }
    }

    fun updateGridTileContainerScale(scale: Float) {
        viewModelScope.launch { settingsRepository.updateGridTileContainerScale(scale) }
    }

    fun updateBarTileImageScale(scale: Float) {
        viewModelScope.launch { settingsRepository.updateBarTileImageScale(scale) }
    }

    fun updateBarTileTitleScale(scale: Float) {
        viewModelScope.launch { settingsRepository.updateBarTileTitleScale(scale) }
    }

    fun updateActionButtonScale(scale: Float) {
        viewModelScope.launch { settingsRepository.updateActionButtonScale(scale) }
    }

    fun updateShowSentenceBar(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowSentenceBar(show) }
    }

    fun saveCurrentLayoutAsDefault() {
        viewModelScope.launch {
            settingsRepository.saveCurrentLayoutAsDefault()
        }
    }

    fun restoreDefaultLayoutSettings() {
        viewModelScope.launch {
            settingsRepository.restoreDefaultLayoutSettings()
        }
    }

    fun updateShowBackButton(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowBackButton(show) }

    fun updateShowBackspaceButton(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowBackspaceButton(show) }

    fun updateShowSpeakButton(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowSpeakButton(show) }

    fun updateHomeInActionBar(show: Boolean) = viewModelScope.launch { settingsRepository.updateHomeInActionBar(show) }

    fun updateGridColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.updateGridColumns(columns) }
    }

    fun updateGridRows(rows: Int) {
        viewModelScope.launch { settingsRepository.updateGridRows(rows) }
    }

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

        settingsRepository.updateLanguageCode(
            normalizedLanguage
        )

        val tilesPrepared = repository.prepareLanguage(
            normalizedLanguage
        )

        if (!tilesPrepared) {
            Log.w(
                "AACViewModel",
                "UI switched to $normalizedLanguage, " +
                        "but no tile definitions were found.",
            )
        }

        /*
         * Sentence tiles contain definitions from the previous language.
         * Clear them before the Activity is recreated.
         */
        sentenceManager.clear()
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
    val recordingTileId: StateFlow<String?> =
        quickRecordingManager.recordingTileId

    @Suppress("unused")
    fun startQuickRecording(
        tileId: String,
    ) {
        quickRecordingManager.startRecording(
            tileId = tileId,
            languageCode = languageCode.value,
        )
    }

    @Suppress("unused")
    fun stopQuickRecording(
        tileId: String,
    ) {
        quickRecordingManager.stopRecording(
            tileId = tileId,
            languageCode = languageCode.value,
        )
    }

    val selectedSentence: StateFlow<List<CombinedTile>> =
        sentenceManager.selectedSentence

    val importExportStatus: StateFlow<String?> =
        backupManager.status

    @Suppress("UNUSED_PARAMETER")
    fun resetToDefault(
        context: Context,
    ) {
        backupManager.resetToDefault()
        sentenceManager.clear()
        resetToHome()
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

    private fun addTileToSentence(
        tile: CombinedTile,
    ) {
        sentenceManager.addTile(tile)
    }

    fun backspaceSentence() {
        sentenceManager.backspace()
    }

    fun clearSentence() {
        sentenceManager.clear()
    }

    fun speakSentence() {
        sentenceManager.speak()
    }

    fun playPreviewAudio(
        ttsText: String,
        audioUri: String?,
    ) {
        sentenceManager.playPreview(
            ttsText = ttsText,
            audioUri = audioUri,
        )
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

            repository.updateTileIndex(
                tileId = tile.id,
                parentId = tile.parentId,
                newIndex = tile.cellIndex ?: 0
            )

            repository.updateTileVisibility(
                tileId = tile.id,
                parentId = tile.parentId,
                isHidden = tile.isHidden
            )
        }
    }

    fun updateTile(tile: CombinedTile) {
        viewModelScope.launch {
            val definition = tile.definition
            val layoutState = tile.layoutState

            val legacyTile = AACTile(
                id = definition.id,
                label = definition.label,
                ttsText = definition.ttsText,
                labelFeminine = definition.labelFeminine,
                ttsTextFeminine = definition.ttsTextFeminine,
                emoji = definition.emoji,
                audioUri = definition.audioUri,
                imageUri = definition.imageUri,
                backgroundColorHex = definition.backgroundColorHex,
                partOfSpeech = definition.partOfSpeech,
                grammaticalGender = definition.grammaticalGender,
                isCategory = definition.resolvedType == TileType.FOLDER,
                languageCode = definition.languageCode,
                parentId = layoutState.parentId,
                linkedCategoryId = layoutState.linkedCategoryId,
                cellIndex = layoutState.cellIndex,
                isQuickFire = layoutState.isQuickFire ||
                        definition.resolvedType == TileType.QUICK_FIRE,
                isHidden = layoutState.isHidden
            )

            repository.updateTile(legacyTile)

            repository.updateTileIndex(
                tileId = definition.id,
                parentId = layoutState.parentId,
                newIndex = layoutState.cellIndex
            )

            repository.updateTileVisibility(
                tileId = definition.id,
                parentId = layoutState.parentId,
                isHidden = layoutState.isHidden
            )
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

    @Suppress("unused")
    fun exportDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        backupManager.exportDatabase(
            uri = uri,
            contentResolver = contentResolver,
        )
    }

    fun importDatabase(
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        backupManager.importDatabase(
            uri = uri,
            contentResolver = contentResolver,
        )

        sentenceManager.clear()
        resetToHome()
    }

    fun clearImportExportStatus() {
        backupManager.clearStatus()
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

    val selectedTimeFilter: StateFlow<AnalyticsTimeFilter> =
        analyticsManager.selectedTimeFilter

    val filteredClickEvents: StateFlow<List<TileClickEvent>> =
        analyticsManager.filteredClickEvents

    fun setTimeFilter(
        filter: AnalyticsTimeFilter,
    ) {
        analyticsManager.setTimeFilter(filter)
    }

    @Suppress("unused")
    fun resetStatistics(
        context: Context,
    ) {
        viewModelScope.launch {
            repository.clearAllStatistics()

            backupManager.setStatus(
                context.getString(
                    R.string.stats_reset_success
                )
            )
        }
    }

    @Suppress("unused")
    fun removeAllAudio(
        context: Context,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val tiles = repository.getAllTilesSync()

            tiles.forEach { tile ->
                if (tile.audioUri != null) {
                    updateTile(
                        tile.copy(audioUri = null)
                    )
                }
            }

            backupManager.setStatus(
                context.getString(
                    R.string.audio_removed_success
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        languageDownloadHelper.unregister()
        ttsHelper.shutdown()
    }

    @Suppress("UNUSED_PARAMETER")
    fun exportAndShareDatabase(
        context: Context,
        onReady: (Uri?) -> Unit,
    ) {
        backupManager.exportAndShare(
            onReady = onReady,
        )
    }
}