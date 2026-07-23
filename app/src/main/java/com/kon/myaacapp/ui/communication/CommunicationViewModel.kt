package com.kon.myaacapp.ui.communication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.service.AACTileService
import com.kon.myaacapp.domain.service.SentenceManager
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TextToSpeechHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val observeTilesUseCase: ObserveTilesUseCase,
    private val incrementTileClickUseCase: IncrementTileClickUseCase,
    private val communicationSessionController: CommunicationSessionController,
) : AndroidViewModel(application) {

    private val audioService =
        AudioRecordingService(application)

    private val textToSpeechHelper =
        TextToSpeechHelper(application)

    private val tileService =
        AACTileService(
            settingsRepository = settingsRepository,
            scope = viewModelScope,
        )

    private val sentenceManager =
        SentenceManager(
            audioService = audioService,
            ttsHelper = textToSpeechHelper,
            tileService = tileService,
            scope = viewModelScope,
        )

    private val currentParentId =
        MutableStateFlow<String?>(null)

    private val navigationStack =
        mutableListOf<String?>()

    private val _effects =
        MutableSharedFlow<CommunicationEffect>()

    val effects =
        _effects.asSharedFlow()

    private val languageCode =
        settingsRepository.languageCodeFlow
            .map { value ->
                LocaleHelper.normalize(value)
            }
            .onEach { value ->
                textToSpeechHelper.setLanguage(value)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = "he",
            )

    private val speakOnTilePress =
        settingsRepository.speakOnTilePressFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = true,
            )

    private val tiles =
        combine(
            currentParentId,
            languageCode,
        ) { parentId, language ->
            parentId to language
        }.flatMapLatest { (parentId, language) ->
            observeTilesUseCase(
                parentId = parentId,
                languageCode = language,
            )
        }

    private val gridSettings =
        combine(
            settingsRepository.gridColumnsFlow,
            settingsRepository.gridRowsFlow,
            settingsRepository.gridTileScaleFlow,
            settingsRepository
                .gridTileContainerScaleFlow,
        ) {
                columns,
                rows,
                tileScale,
                containerScale ->

            CommunicationGridSettings(
                columns = columns,
                rows = rows,
                tileScale = tileScale,
                containerScale = containerScale,
            )
        }

    private val barSettings =
        combine(
            settingsRepository.barTileImageScaleFlow,
            settingsRepository.barTileTitleScaleFlow,
            settingsRepository.actionButtonScaleFlow,
        ) {
                imageScale,
                titleScale,
                actionScale ->

            CommunicationBarSettings(
                imageScale = imageScale,
                titleScale = titleScale,
                actionScale = actionScale,
            )
        }

    private val visibilitySettings =
        combine(
            settingsRepository.showSentenceBarFlow,
            settingsRepository.showBackButtonFlow,
            settingsRepository.showBackspaceButtonFlow,
            settingsRepository.showSpeakButtonFlow,
            settingsRepository.homeInActionBarFlow,
        ) {
                showSentenceBar,
                showBackButton,
                showBackspaceButton,
                showSpeakButton,
                homeInActionBar ->

            CommunicationVisibilitySettings(
                showSentenceBar = showSentenceBar,
                showBackButton = showBackButton,
                showBackspaceButton =
                    showBackspaceButton,
                showSpeakButton = showSpeakButton,
                homeInActionBar = homeInActionBar,
            )
        }

    private val layoutSettings =
        combine(
            gridSettings,
            barSettings,
            visibilitySettings,
        ) {
                grid,
                bar,
                visibility ->

            CommunicationLayoutSettings(
                grid = grid,
                bar = bar,
                visibility = visibility,
            )
        }

    private val communicationContent =
        combine(
            tiles,
            sentenceManager.selectedSentence,
            currentParentId,
            languageCode,
            tileService.userGender,
        ) {
                currentTiles,
                sentence,
                parentId,
                language,
                gender ->

            CommunicationContent(
                tiles = currentTiles,
                sentence = sentence,
                currentParentId = parentId,
                languageCode = language,
                userGender = gender,
            )
        }

    val state: StateFlow<CommunicationState> =
        combine(
            communicationContent,
            layoutSettings,
        ) {
                content,
                layout ->

            CommunicationState(
                tiles = content.tiles,
                sentence = content.sentence,
                currentParentId =
                    content.currentParentId,
                userGender = content.userGender,
                languageCode =
                    content.languageCode,

                gridColumns =
                    layout.grid.columns,
                gridRows =
                    layout.grid.rows,
                gridTileScale =
                    layout.grid.tileScale,
                gridTileContainerScale =
                    layout.grid.containerScale,

                barTileImageScale =
                    layout.bar.imageScale,
                barTileTitleScale =
                    layout.bar.titleScale,
                actionButtonScale =
                    layout.bar.actionScale,

                showSentenceBar =
                    layout.visibility
                        .showSentenceBar,
                showBackButton =
                    layout.visibility
                        .showBackButton,
                showBackspaceButton =
                    layout.visibility
                        .showBackspaceButton,
                showSpeakButton =
                    layout.visibility
                        .showSpeakButton,
                homeInActionBar =
                    layout.visibility
                        .homeInActionBar,

                layoutSettingsLoaded = true,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5_000,
                ),
            initialValue =
                CommunicationState(),
        )

    init {
        viewModelScope.launch {
            communicationSessionController
                .resetVersion
                .collect {
                    sentenceManager.clear()
                    resetToHome()
                }
        }
    }

    fun onAction(
        action: CommunicationAction,
    ) {
        when (action) {
            is CommunicationAction.TileClicked -> {
                selectTile(action.tile)
            }

            CommunicationAction.BackClicked -> {
                navigateBack()
            }

            CommunicationAction.HomeClicked -> {
                resetToHome()
            }

            CommunicationAction
                .SpeakSentenceClicked -> {
                sentenceManager.speak()
            }

            CommunicationAction
                .ClearSentenceClicked -> {
                sentenceManager.clear()
            }

            CommunicationAction
                .BackspaceSentenceClicked -> {
                sentenceManager.backspace()
            }

            CommunicationAction
                .AdminSettingsClicked -> {
                emitEffect(
                    CommunicationEffect
                        .OpenAdminSettings
                )
            }

            CommunicationAction.ErrorConsumed -> {
                // Error state will be added when needed.
            }
        }
    }

    private fun selectTile(
        tile: CombinedTile,
    ) {
        when (tile.definition.resolvedType) {
            TileType.FOLDER -> {
                if (speakOnTilePress.value) {
                    speakTile(tile)
                }

                val categoryId =
                    tile.definition
                        .defaultLinkedCategoryId
                        ?: tile.definition.id

                navigateToCategory(categoryId)
            }

            TileType.CONNECTOR -> {
                sentenceManager.addTile(tile)

                if (speakOnTilePress.value) {
                    speakTile(tile)
                }

                tile.definition.defaultLinkedCategoryId
                    ?.let { categoryId ->
                        navigateToCategory(categoryId)
                    }
            }

            TileType.QUICK_FIRE -> {
                speakTile(tile)
            }

            TileType.BASIC -> {
                sentenceManager.addTile(tile)

                if (speakOnTilePress.value) {
                    speakTile(tile)
                }
            }
        }

        viewModelScope.launch {
            runCatching {
                incrementTileClickUseCase(
                    tileId = tile.id,
                    parentId = tile.parentId,
                    languageCode =
                        languageCode.value,
                )
            }.onFailure { error ->
                _effects.emit(
                    CommunicationEffect.ShowError(
                        message =
                            error.message
                                ?: "Failed to record tile usage.",
                    )
                )
            }
        }
    }

    private fun speakTile(
        tile: CombinedTile,
    ) {
        val text =
            tileService.getTTSText(tile)

        val audioUri =
            tile.definition.audioUri

        if (!audioUri.isNullOrBlank()) {
            audioService.playRecording(audioUri)
        } else if (text.isNotBlank()) {
            textToSpeechHelper.speak(text)
        }
    }

    private fun navigateToCategory(
        categoryId: String,
    ) {
        if (
            currentParentId.value ==
            categoryId
        ) {
            return
        }

        navigationStack.add(
            currentParentId.value
        )

        currentParentId.value =
            categoryId
    }

    private fun navigateBack() {
        if (navigationStack.isNotEmpty()) {
            currentParentId.value =
                navigationStack.removeAt(
                    navigationStack.lastIndex
                )
        } else if (
            currentParentId.value != null
        ) {
            currentParentId.value = null
        } else {
            emitEffect(
                CommunicationEffect
                    .NavigateBackFromRoot
            )
        }
    }

    private fun resetToHome() {
        navigationStack.clear()
        currentParentId.value = null
    }

    private fun emitEffect(
        effect: CommunicationEffect,
    ) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }

    override fun onCleared() {
        audioService.stopPlayback()
        textToSpeechHelper.shutdown()
    }
}

private data class CommunicationContent(
    val tiles: List<CombinedTile>,
    val sentence: List<CombinedTile>,
    val currentParentId: String?,
    val languageCode: String,
    val userGender:
    com.kon.myaacapp.domain.service.Gender,
)

private data class CommunicationGridSettings(
    val columns: Int,
    val rows: Int,
    val tileScale: Float,
    val containerScale: Float,
)

private data class CommunicationBarSettings(
    val imageScale: Float,
    val titleScale: Float,
    val actionScale: Float,
)

private data class CommunicationVisibilitySettings(
    val showSentenceBar: Boolean,
    val showBackButton: Boolean,
    val showBackspaceButton: Boolean,
    val showSpeakButton: Boolean,
    val homeInActionBar: Boolean,
)

private data class CommunicationLayoutSettings(
    val grid: CommunicationGridSettings,
    val bar: CommunicationBarSettings,
    val visibility:
    CommunicationVisibilitySettings,
)