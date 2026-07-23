package com.kon.myaacapp.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.CreateTileRequest
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.usecase.tile.AddTileUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch

class TileEditorViewModel(
    private val addTileUseCase: AddTileUseCase,
    private val updateTileUseCase: UpdateTileUseCase,
    private val observeTilesUseCase: ObserveTilesUseCase,
) : ViewModel() {

    private val _state =
        MutableStateFlow(TileEditorState())

    val state: StateFlow<TileEditorState> =
        _state.asStateFlow()

    private val _effects =
        MutableSharedFlow<TileEditorEffect>()

    val effects: SharedFlow<TileEditorEffect> =
        _effects.asSharedFlow()

    private var initializedTileId: String? = null
    private var initializedForNewTile: Boolean = false

    private var observeParentTilesJob: Job? = null

    private var observedParentId: String? = null
    private var observedLanguageCode: String? = null

    fun initialize(
        existingTile: CombinedTile?,
        initialCellIndex: Int?,
        currentParentId: String?,
        languageCode: String,
        categories: List<CombinedTile> = emptyList(),
    ) {
        val isNewTile = existingTile == null

        if (
            isNewTile &&
            initializedForNewTile
        ) {
            return
        }

        if (
            !isNewTile &&
            initializedTileId == existingTile.id
        ) {
            return
        }

        initializedForNewTile = isNewTile
        initializedTileId = existingTile?.id

        val definition = existingTile?.definition
        val layoutState = existingTile?.layoutState

        _state.value = TileEditorState(
            existingTile = existingTile,

            tileId = definition?.id.orEmpty(),
            label = definition?.label.orEmpty(),
            ttsText = definition?.ttsText.orEmpty(),

            labelFeminine =
                definition?.labelFeminine.orEmpty(),

            ttsTextFeminine =
                definition?.ttsTextFeminine.orEmpty(),

            emoji = definition?.emoji.orEmpty(),
            imageUri = definition?.imageUri,
            audioUri = definition?.audioUri,

            backgroundColorHex =
                definition?.backgroundColorHex.orEmpty(),

            partOfSpeech =
                definition?.partOfSpeech.orEmpty(),

            grammaticalGender =
                definition?.grammaticalGender.orEmpty(),

            tileType =
                definition?.resolvedType
                    ?: TileType.BASIC,

            languageCode =
                definition?.languageCode
                    ?: languageCode,

            parentId =
                layoutState?.parentId
                    ?: currentParentId,

            linkedCategoryId =
                layoutState?.linkedCategoryId,

            cellIndex =
                layoutState?.cellIndex?.toString()
                    ?: initialCellIndex?.toString()
                    ?: "",

            isHidden =
                layoutState?.isHidden ?: false,

            categories = categories,
        )

        observeTilesInParent(
            parentId = _state.value.parentId,
            languageCode = _state.value.languageCode,
        )
    }

    fun updateCategories(
        categories: List<CombinedTile>,
    ) {
        _state.update { currentState ->
            currentState.copy(
                categories = categories,
            )
        }
    }

    fun onAction(
        action: TileEditorAction,
    ) {
        when (action) {
            is TileEditorAction.LabelChanged -> {
                updateState {
                    copy(label = action.value)
                }
            }

            is TileEditorAction.TtsTextChanged -> {
                updateState {
                    copy(ttsText = action.value)
                }
            }

            is TileEditorAction.LabelFeminineChanged -> {
                updateState {
                    copy(
                        labelFeminine = action.value,
                    )
                }
            }

            is TileEditorAction.TtsTextFeminineChanged -> {
                updateState {
                    copy(
                        ttsTextFeminine = action.value,
                    )
                }
            }

            is TileEditorAction.EmojiChanged -> {
                updateState {
                    copy(emoji = action.value)
                }
            }

            is TileEditorAction.ImageUriChanged -> {
                updateState {
                    copy(imageUri = action.value)
                }
            }

            is TileEditorAction.AudioUriChanged -> {
                updateState {
                    copy(audioUri = action.value)
                }
            }

            is TileEditorAction.BackgroundColorChanged -> {
                updateState {
                    copy(
                        backgroundColorHex = action.value,
                    )
                }
            }

            is TileEditorAction.PartOfSpeechChanged -> {
                updateState {
                    copy(
                        partOfSpeech = action.value,
                    )
                }
            }

            is TileEditorAction.GrammaticalGenderChanged -> {
                updateState {
                    copy(
                        grammaticalGender = action.value,
                    )
                }
            }

            is TileEditorAction.TileTypeChanged -> {
                handleTileTypeChanged(action.value)
            }

            is TileEditorAction.ParentIdChanged -> {
                handleParentIdChanged(action.value)
            }

            is TileEditorAction.LinkedCategoryIdChanged -> {
                updateState {
                    copy(
                        linkedCategoryId = action.value,
                    )
                }
            }

            is TileEditorAction.CellIndexChanged -> {
                updateState {
                    copy(cellIndex = action.value)
                }
            }

            is TileEditorAction.HiddenChanged -> {
                updateState {
                    copy(isHidden = action.value)
                }
            }

            is TileEditorAction.TileIdChanged -> {
                updateState {
                    copy(
                        tileId = action.value.trim(),
                    )
                }
            }

            is TileEditorAction.OccupiedCellSelected -> {
                updateState {
                    copy(
                        pendingCellIndex =
                            action.cellIndex,
                        showOverwriteDialog = true,
                    )
                }
            }

            TileEditorAction.ConfirmOccupiedCell -> {
                confirmOccupiedCell()
            }

            TileEditorAction.DismissOccupiedCellDialog -> {
                dismissOccupiedCellDialog()
            }

            TileEditorAction.SaveClicked -> {
                saveTile()
            }

            TileEditorAction.CancelClicked -> {
                closeEditor()
            }

            TileEditorAction.ErrorConsumed -> {
                updateState {
                    copy(errorMessage = null)
                }
            }
        }
    }

    private fun updateState(
        transform: TileEditorState.() -> TileEditorState,
    ) {
        _state.update { currentState ->
            currentState.transform()
        }
    }

    private fun handleTileTypeChanged(
        tileType: TileType,
    ) {
        updateState {
            copy(
                tileType = tileType,
                linkedCategoryId =
                    if (
                        tileType == TileType.FOLDER ||
                        tileType == TileType.CONNECTOR
                    ) {
                        linkedCategoryId
                    } else {
                        null
                    },
            )
        }
    }

    private fun handleParentIdChanged(
        parentId: String?,
    ) {
        updateState {
            val originalParentId =
                existingTile?.layoutState?.parentId

            copy(
                parentId = parentId,
                cellIndex =
                    if (parentId != originalParentId) {
                        ""
                    } else {
                        cellIndex
                    },
                tilesInParent = emptyList(),
            )
        }

        observeTilesInParent(
            parentId = parentId,
            languageCode = _state.value.languageCode,
        )
    }

    private fun confirmOccupiedCell() {
        updateState {
            copy(
                cellIndex = pendingCellIndex,
                pendingCellIndex = "",
                showOverwriteDialog = false,
            )
        }
    }

    private fun dismissOccupiedCellDialog() {
        updateState {
            copy(
                pendingCellIndex = "",
                showOverwriteDialog = false,
            )
        }
    }

    private fun observeTilesInParent(
        parentId: String?,
        languageCode: String,
    ) {
        if (
            observedParentId == parentId &&
            observedLanguageCode == languageCode &&
            observeParentTilesJob?.isActive == true
        ) {
            return
        }

        observedParentId = parentId
        observedLanguageCode = languageCode

        observeParentTilesJob?.cancel()

        observeParentTilesJob = viewModelScope.launch {
            observeTilesUseCase(
                parentId = parentId,
                languageCode = languageCode,
            )
                .catch { error ->
                    val message =
                        error.message
                            ?: "Failed to load tiles for the selected category."

                    showError(message)
                }
                .collect { tiles ->
                    _state.update { currentState ->
                        currentState.copy(
                            tilesInParent = tiles,
                        )
                    }
                }
        }
    }

    private fun saveTile() {
        val currentState = _state.value

        if (
            !currentState.canSave ||
            currentState.isSubmitting
        ) {
            return
        }

        val validationError =
            validateState(currentState)

        if (validationError != null) {
            showError(validationError)
            return
        }

        _state.update {
            it.copy(
                isSubmitting = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val existingTile =
                    currentState.existingTile

                if (existingTile == null) {
                    addTileUseCase(
                        createRequest(currentState)
                    )
                } else {
                    updateTileUseCase(
                        createUpdatedTile(
                            existingTile = existingTile,
                            state = currentState,
                        )
                    )
                }
            }.onSuccess {
                _state.update {
                    it.copy(isSubmitting = false)
                }

                _effects.emit(
                    TileEditorEffect.CloseEditor
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(isSubmitting = false)
                }

                val message =
                    error.message
                        ?: "Failed to save tile."

                showError(message)
            }
        }
    }

    private fun validateState(
        state: TileEditorState,
    ): String? {
        if (state.label.isBlank()) {
            return "Tile label cannot be blank."
        }

        if (state.ttsText.isBlank()) {
            return "Tile TTS text cannot be blank."
        }

        if (state.languageCode.isBlank()) {
            return "Tile language code cannot be blank."
        }

        val cellIndex = state.cellIndex
            .takeIf { value ->
                value.isNotBlank()
            }
            ?.toIntOrNull()

        if (
            state.cellIndex.isNotBlank() &&
            cellIndex == null
        ) {
            return "Tile cell index must be a number."
        }

        if (
            cellIndex != null &&
            cellIndex < 0
        ) {
            return "Tile cell index cannot be negative."
        }

        return null
    }

    private fun createRequest(
        state: TileEditorState,
    ): CreateTileRequest {
        return CreateTileRequest(
            id = state.tileId
                .trim()
                .ifBlank { null },

            label = state.label.trim(),
            ttsText = state.ttsText.trim(),

            labelFeminine =
                state.labelFeminine
                    .trim()
                    .ifBlank { null },

            ttsTextFeminine =
                state.ttsTextFeminine
                    .trim()
                    .ifBlank { null },

            emoji =
                state.emoji
                    .trim()
                    .ifBlank { null },

            audioUri = state.audioUri,
            imageUri = state.imageUri,

            backgroundColorHex =
                state.backgroundColorHex
                    .trim()
                    .ifBlank { null },

            partOfSpeech =
                state.partOfSpeech
                    .trim()
                    .ifBlank { null },

            grammaticalGender =
                state.grammaticalGender
                    .trim()
                    .ifBlank { null },

            tileType = state.tileType,
            languageCode = state.languageCode,

            parentId = state.parentId,

            linkedCategoryId =
                normalizedLinkedCategoryId(state),

            cellIndex =
                state.cellIndex.toIntOrNull(),

            isHidden = state.isHidden,
        )
    }

    private fun createUpdatedTile(
        existingTile: CombinedTile,
        state: TileEditorState,
    ): CombinedTile {
        val updatedDefinition =
            existingTile.definition.copy(
                label = state.label.trim(),
                ttsText = state.ttsText.trim(),

                labelFeminine =
                    state.labelFeminine
                        .trim()
                        .ifBlank { null },

                ttsTextFeminine =
                    state.ttsTextFeminine
                        .trim()
                        .ifBlank { null },

                emoji =
                    state.emoji
                        .trim()
                        .ifBlank { null },

                audioUri = state.audioUri,
                imageUri = state.imageUri,

                backgroundColorHex =
                    state.backgroundColorHex
                        .trim()
                        .ifBlank { null },

                partOfSpeech =
                    state.partOfSpeech
                        .trim()
                        .ifBlank { null },

                grammaticalGender =
                    state.grammaticalGender
                        .trim()
                        .ifBlank { null },

                isCategory =
                    state.tileType == TileType.FOLDER,

                type = state.tileType,
            )

        val updatedLayoutState =
            existingTile.layoutState.copy(
                parentId = state.parentId,

                linkedCategoryId =
                    normalizedLinkedCategoryId(state),

                cellIndex =
                    state.cellIndex.toIntOrNull()
                        ?: existingTile.layoutState.cellIndex,

                isQuickFire =
                    state.tileType == TileType.QUICK_FIRE,

                isHidden = state.isHidden,
            )

        return existingTile.copy(
            definition = updatedDefinition,
            layoutState = updatedLayoutState,
        )
    }

    private fun normalizedLinkedCategoryId(
        state: TileEditorState,
    ): String? {
        return when (state.tileType) {
            TileType.FOLDER,
            TileType.CONNECTOR -> {
                state.linkedCategoryId
            }

            TileType.BASIC,
            TileType.QUICK_FIRE -> {
                null
            }
        }
    }

    private fun closeEditor() {
        viewModelScope.launch {
            _effects.emit(
                TileEditorEffect.CloseEditor
            )
        }
    }

    private fun showError(
        message: String,
    ) {
        _state.update {
            it.copy(errorMessage = message)
        }

        viewModelScope.launch {
            _effects.emit(
                TileEditorEffect.ShowError(
                    message = message,
                )
            )
        }
    }
}