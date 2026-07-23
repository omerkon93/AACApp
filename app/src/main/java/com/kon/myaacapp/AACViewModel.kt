@file:Suppress("SpellCheckingInspection")

package com.kon.myaacapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.app.AppContainer
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.AdminAuditFilter
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.service.AACTileService
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.domain.service.Gender
import com.kon.myaacapp.domain.service.QuickRecordingManager
import com.kon.myaacapp.domain.service.SentenceManager
import com.kon.myaacapp.domain.usecase.tile.AttachTileToCategoryUseCase
import com.kon.myaacapp.domain.usecase.tile.DeleteTileUseCase
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.RemoveTileFromCategoryUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TextToSpeechHelper
import com.kon.myaacapp.service.backup.BackupService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AACViewModel(
    application: Application,
    appContainer: AppContainer,
) : AndroidViewModel(application) {
    private val repository: AACRepository =
        appContainer.aacRepository

    private val settingsRepository: SettingsRepository =
        appContainer.settingsRepository

    private val profileRepository: ProfileRepository =
        appContainer.profileRepository

    private val observeTilesUseCase: ObserveTilesUseCase =
        appContainer.observeTilesUseCase

    private val observeAllTilesUseCase: ObserveAllTilesUseCase =
        appContainer.observeAllTilesUseCase

    private val attachTileToCategoryUseCase: AttachTileToCategoryUseCase =
        appContainer.attachTileToCategoryUseCase

    private val removeTileFromCategoryUseCase:
            RemoveTileFromCategoryUseCase =
        appContainer.removeTileFromCategoryUseCase

    private val incrementTileClickUseCase: IncrementTileClickUseCase =
        appContainer.incrementTileClickUseCase

    private val deleteTileUseCase: DeleteTileUseCase =
        appContainer.deleteTileUseCase

    private var ttsHelper =
        TextToSpeechHelper(application)
    private val sentenceManager: SentenceManager
    private val quickRecordingManager: QuickRecordingManager
    private val startupCoordinator: AppStartupCoordinator

    val tileService: AACTileService
    val audioService: AudioRecordingService
    val backupService: BackupService

    init {
        ttsHelper = TextToSpeechHelper(application)

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

    val showSentenceBar: StateFlow<Boolean> =
        settingsRepository.showSentenceBarFlow
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    val showBackButton: StateFlow<Boolean> = settingsRepository.showBackButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showBackspaceButton: StateFlow<Boolean> = settingsRepository.showBackspaceButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showSpeakButton: StateFlow<Boolean> = settingsRepository.showSpeakButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val homeInActionBar: StateFlow<Boolean> =
        settingsRepository.homeInActionBarFlow
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(5000),
                initialValue = true,
            )

    val gridColumns: StateFlow<Int> = settingsRepository.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val gridRows: StateFlow<Int> =
        settingsRepository.gridRowsFlow
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(5000),
                initialValue = 5,
            )

    private val gridSizeSettingsLoaded =
        combine(
            settingsRepository.gridColumnsFlow,
            settingsRepository.gridRowsFlow,
        ) { _, _ ->
            true
        }

    private val scaleSettingsLoaded =
        combine(
            settingsRepository.gridTileScaleFlow,
            settingsRepository.gridTileContainerScaleFlow,
            settingsRepository.barTileImageScaleFlow,
            settingsRepository.barTileTitleScaleFlow,
            settingsRepository.actionButtonScaleFlow,
        ) { _, _, _, _, _ ->
            true
        }

    private val visibilitySettingsLoaded =
        combine(
            settingsRepository.showSentenceBarFlow,
            settingsRepository.showBackButtonFlow,
            settingsRepository.showBackspaceButtonFlow,
            settingsRepository.showSpeakButtonFlow,
            settingsRepository.homeInActionBarFlow,
        ) { _, _, _, _, _ ->
            true
        }

    val layoutSettingsLoaded: StateFlow<Boolean> =
        combine(
            gridSizeSettingsLoaded,
            scaleSettingsLoaded,
            visibilitySettingsLoaded,
        ) {
                gridSizeLoaded,
                scaleLoaded,
                visibilityLoaded ->

            gridSizeLoaded &&
                    scaleLoaded &&
                    visibilityLoaded
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val userGender: StateFlow<Gender> = tileService.userGender

    val currentTiles: StateFlow<List<CombinedTile>> =
        combine(
            _currentParentId,
            languageCode,
        ) { parentId, lang ->
            parentId to lang
        }
            .flatMapLatest { (parentId, lang) ->
                observeTilesUseCase(
                    parentId = parentId,
                    languageCode = lang,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val allTiles: StateFlow<List<CombinedTile>> =
        languageCode
            .flatMapLatest { language ->
                observeAllTilesUseCase(
                    languageCode = language,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val allCategories: StateFlow<List<CombinedTile>> = allTiles
        .map { list -> list.filter { it.isCategory } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            incrementTileClickUseCase(
                tileId = tile.id,
                parentId = tile.parentId,
                languageCode = languageCode.value,
            )
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

    fun attachTileToCategory(
        tileId: String,
        parentId: String?,
        cellIndex: Int?,
    ) {
        viewModelScope.launch {
            attachTileToCategoryUseCase(
                tileId = tileId,
                parentId = parentId,
                languageCode = languageCode.value,
                cellIndex = cellIndex,
            )
        }
    }

    fun removeTileFromCategory(
        tileId: String,
        parentId: String?,
    ) {
        viewModelScope.launch {
            removeTileFromCategoryUseCase(
                tileId = tileId,
                parentId = parentId,
                languageCode = languageCode.value,
            )
        }
    }

    fun deleteTile(
        tile: CombinedTile,
    ) {
        viewModelScope.launch {
            tile.audioUri?.let { audioUri ->
                audioService.deleteRecording(audioUri)
            }

            deleteTileUseCase(tile)

            profileRepository.profiles.value.forEach { profile ->
                val updatedLayout = profile.layout
                    .filterValues { layoutState ->
                        layoutState.tileId != tile.id
                    }

                if (updatedLayout.size != profile.layout.size) {
                    profileRepository.updateActiveProfile(
                        profile.copy(
                            layout = updatedLayout,
                        )
                    )
                }
            }
        }
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

    override fun onCleared() {
        ttsHelper.shutdown()
    }
}