package com.kon.myaacapp.ui.admin.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileAudioUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminListViewModel(
    private val observeAllTilesUseCase:
    ObserveAllTilesUseCase,
    private val updateTileAudioUseCase:
    UpdateTileAudioUseCase,
) : ViewModel() {

    private val _state =
        MutableStateFlow(AdminListState())

    val state: StateFlow<AdminListState> =
        _state.asStateFlow()

    private val _effects =
        MutableSharedFlow<AdminListEffect>()

    val effects: SharedFlow<AdminListEffect> =
        _effects.asSharedFlow()

    private var observeTilesJob: Job? = null
    private var observedLanguageCode: String? = null

    fun updateLanguage(
        languageCode: String,
    ) {
        val normalizedLanguage =
            languageCode.trim().ifBlank { "he" }

        if (
            observedLanguageCode ==
            normalizedLanguage &&
            observeTilesJob?.isActive == true
        ) {
            return
        }

        observedLanguageCode =
            normalizedLanguage

        _state.update { currentState ->
            currentState.copy(
                languageCode =
                    normalizedLanguage,
            )
        }

        observeTiles(
            languageCode = normalizedLanguage,
        )
    }

    fun onAction(
        action: AdminListAction,
    ) {
        when (action) {
            is AdminListAction.SearchQueryChanged -> {
                updateStateAndFilter {
                    copy(searchQuery = action.value)
                }
            }

            is AdminListAction.FilterActiveChanged -> {
                updateStateAndFilter {
                    copy(
                        isFilterActive = action.value,
                    )
                }
            }

            AdminListAction.OpenFilterSheet -> {
                _state.update { currentState ->
                    currentState.copy(
                        isFilterActive = true,
                        showFilterSheet = true,
                    )
                }
            }

            AdminListAction.CloseFilterSheet -> {
                _state.update { currentState ->
                    currentState.copy(
                        showFilterSheet = false,
                    )
                }
            }

            is AdminListAction.MediaFilterChanged -> {
                changeMediaFilter(
                    filter = action.filter,
                    isSelected = action.isSelected,
                )
            }

            is AdminListAction.TileTypeFilterChanged -> {
                changeTileTypeFilter(
                    tileType = action.tileType,
                    isSelected = action.isSelected,
                )
            }

            is AdminListAction.UsageFilterChanged -> {
                changeUsageFilter(
                    filter = action.filter,
                    isSelected = action.isSelected,
                )
            }

            AdminListAction.ClearFilters -> {
                updateStateAndFilter {
                    copy(
                        selectedMediaFilters =
                            emptySet(),
                        selectedTypes =
                            emptySet(),
                        selectedUsageFilters =
                            emptySet(),
                    )
                }
            }

            AdminListAction.AddTileClicked -> {
                emitEffect(
                    AdminListEffect.OpenTileCreator
                )
            }

            is AdminListAction.EditTileClicked -> {
                emitEffect(
                    AdminListEffect.OpenTileEditor(
                        tile = action.tile,
                    )
                )
            }

            is AdminListAction.DeleteTileClicked -> {
                emitEffect(
                    AdminListEffect
                        .RequestTileDeletion(
                            tile = action.tile,
                        )
                )
            }

            is AdminListAction.QuickRecordClicked -> {
                requestQuickRecording(
                    tile = action.tile,
                )
            }

            AdminListAction
                .MicrophonePermissionGranted -> {
                startRecording()
            }

            AdminListAction
                .MicrophonePermissionDenied -> {
                showError(
                    message =
                        "Microphone permission is required to record audio.",
                )
            }

            is AdminListAction.RecordingStarted -> {
                _state.update { currentState ->
                    currentState.copy(
                        isRecording = true,
                        temporaryAudioPath =
                            action.temporaryAudioPath,
                        errorMessage = null,
                    )
                }
            }

            AdminListAction.StopRecordingClicked -> {
                if (_state.value.isRecording) {
                    emitEffect(
                        AdminListEffect.StopRecording
                    )
                }
            }

            AdminListAction.RecordingStopped -> {
                _state.update { currentState ->
                    currentState.copy(
                        isRecording = false,
                    )
                }
            }

            AdminListAction
                .PreviewRecordingClicked -> {
                previewRecording()
            }

            AdminListAction.SaveRecordingClicked -> {
                saveRecording()
            }

            AdminListAction.CancelRecordingClicked -> {
                cancelRecording()
            }

            AdminListAction.ErrorConsumed -> {
                _state.update { currentState ->
                    currentState.copy(
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun observeTiles(
        languageCode: String,
    ) {
        observeTilesJob?.cancel()

        _state.update { currentState ->
            currentState.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        observeTilesJob = viewModelScope.launch {
            observeAllTilesUseCase(
                languageCode = languageCode,
            )
                .catch { error ->
                    val message =
                        error.message
                            ?: "Failed to load tiles."

                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = message,
                        )
                    }

                    _effects.emit(
                        AdminListEffect.ShowError(
                            message = message,
                        )
                    )
                }
                .collect { tiles ->
                    _state.update { currentState ->
                        val updatedState =
                            currentState.copy(
                                allTiles = tiles,
                                isLoading = false,
                                errorMessage = null,
                            )

                        updatedState.copy(
                            filteredTiles =
                                filterTiles(
                                    state =
                                        updatedState,
                                ),
                        )
                    }
                }
        }
    }

    private fun changeMediaFilter(
        filter: MediaFilter,
        isSelected: Boolean,
    ) {
        updateStateAndFilter {
            copy(
                selectedMediaFilters =
                    selectedMediaFilters.updated(
                        value = filter,
                        isSelected = isSelected,
                    )
            )
        }
    }

    private fun changeTileTypeFilter(
        tileType: TileType,
        isSelected: Boolean,
    ) {
        updateStateAndFilter {
            copy(
                selectedTypes =
                    selectedTypes.updated(
                        value = tileType,
                        isSelected = isSelected,
                    )
            )
        }
    }

    private fun changeUsageFilter(
        filter: UsageFilter,
        isSelected: Boolean,
    ) {
        updateStateAndFilter {
            copy(
                selectedUsageFilters =
                    selectedUsageFilters.updated(
                        value = filter,
                        isSelected = isSelected,
                    )
            )
        }
    }

    private fun updateStateAndFilter(
        transform: AdminListState.() ->
        AdminListState,
    ) {
        _state.update { currentState ->
            val updatedState =
                currentState.transform()

            updatedState.copy(
                filteredTiles =
                    filterTiles(
                        state = updatedState,
                    ),
            )
        }
    }

    private fun filterTiles(
        state: AdminListState,
    ): List<CombinedTile> {
        if (
            state.searchQuery.isBlank() &&
            !state.hasActiveFilters
        ) {
            return state.allTiles
        }

        val normalizedQuery =
            state.searchQuery
                .trim()
                .lowercase()

        return state.allTiles.filter { tile ->
            matchesSearch(
                tile = tile,
                query = normalizedQuery,
            ) &&
                    matchesMediaFilters(
                        tile = tile,
                        state = state,
                    ) &&
                    matchesTileTypeFilters(
                        tile = tile,
                        state = state,
                    ) &&
                    matchesUsageFilters(
                        tile = tile,
                        state = state,
                    )
        }
    }

    private fun matchesSearch(
        tile: CombinedTile,
        query: String,
    ): Boolean {
        if (query.isBlank()) {
            return true
        }

        return tile.label
            .lowercase()
            .contains(query) ||
                tile.ttsText
                    .lowercase()
                    .contains(query)
    }

    private fun matchesMediaFilters(
        tile: CombinedTile,
        state: AdminListState,
    ): Boolean {
        val selectedFilters =
            state.selectedMediaFilters

        if (
            !state.isFilterActive ||
            selectedFilters.isEmpty()
        ) {
            return true
        }

        return selectedFilters.any { filter ->
            when (filter) {
                MediaFilter.MISSING_AUDIO -> {
                    tile.audioUri.isNullOrBlank()
                }

                MediaFilter.MISSING_TTS -> {
                    tile.ttsText.isBlank()
                }

                MediaFilter.MISSING_IMAGE -> {
                    tile.imageUri.isNullOrBlank() &&
                            tile.emoji.isNullOrBlank()
                }
            }
        }
    }

    private fun matchesTileTypeFilters(
        tile: CombinedTile,
        state: AdminListState,
    ): Boolean {
        val selectedTypes =
            state.selectedTypes

        if (
            !state.isFilterActive ||
            selectedTypes.isEmpty()
        ) {
            return true
        }

        return tile.tileType in selectedTypes
    }

    private fun matchesUsageFilters(
        tile: CombinedTile,
        state: AdminListState,
    ): Boolean {
        val selectedFilters =
            state.selectedUsageFilters

        if (
            !state.isFilterActive ||
            selectedFilters.isEmpty()
        ) {
            return true
        }

        return selectedFilters.any { filter ->
            when (filter) {
                UsageFilter.LOW_USAGE -> {
                    tile.clickCount < 5
                }

                UsageFilter.HIDDEN -> {
                    tile.isHidden
                }
            }
        }
    }

    private fun requestQuickRecording(
        tile: CombinedTile,
    ) {
        _state.update { currentState ->
            currentState.copy(
                quickRecordTile = tile,
                isRecording = false,
                temporaryAudioPath = null,
                isSavingRecording = false,
                errorMessage = null,
            )
        }

        emitEffect(
            AdminListEffect.RequestMicrophonePermission(
                tile = tile,
            )
        )
    }

    private fun startRecording() {
        val currentState = _state.value

        val tile = currentState.quickRecordTile

        if (tile == null) {
            showError(
                message = "No tile was selected for recording.",
            )

            return
        }

        if (currentState.isRecording) {
            return
        }

        emitEffect(
            AdminListEffect.StartRecording(
                tile = tile,
                languageCode =
                    currentState.languageCode,
            )
        )
    }

    private fun previewRecording() {
        val currentState = _state.value

        val tile = currentState.quickRecordTile
        val audioPath =
            currentState.temporaryAudioPath

        if (tile == null || audioPath == null) {
            showError(
                message = "No recording is available for preview.",
            )

            return
        }

        if (currentState.isRecording) {
            showError(
                message = "Stop recording before playing the preview.",
            )

            return
        }

        emitEffect(
            AdminListEffect.PlayAudioPreview(
                ttsText = tile.ttsText,
                audioUri = audioPath,
            )
        )
    }

    private fun saveRecording() {
        val currentState = _state.value

        if (
            currentState.isRecording ||
            currentState.isSavingRecording
        ) {
            return
        }

        val tile = currentState.quickRecordTile
        val audioPath =
            currentState.temporaryAudioPath

        if (tile == null) {
            showError(
                message = "No tile was selected for recording.",
            )

            return
        }

        if (audioPath.isNullOrBlank()) {
            showError(
                message = "No recording is available to save.",
            )

            return
        }

        _state.update { state ->
            state.copy(
                isSavingRecording = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                updateTileAudioUseCase(
                    tile = tile,
                    audioUri = audioPath,
                )
            }.onSuccess {
                clearRecordingState()
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        isSavingRecording = false,
                    )
                }

                showError(
                    message = error.message
                        ?: "Failed to save the recording.",
                )
            }
        }
    }

    private fun cancelRecording() {
        val wasRecording =
            _state.value.isRecording

        if (wasRecording) {
            emitEffect(
                AdminListEffect.StopRecording
            )
        }

        clearRecordingState()
    }

    private fun clearRecordingState() {
        _state.update { currentState ->
            currentState.copy(
                quickRecordTile = null,
                isRecording = false,
                temporaryAudioPath = null,
                isSavingRecording = false,
            )
        }
    }

    private fun emitEffect(
        effect: AdminListEffect,
    ) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }

    private fun showError(
        message: String,
    ) {
        _state.update { currentState ->
            currentState.copy(
                errorMessage = message,
            )
        }

        emitEffect(
            AdminListEffect.ShowError(
                message = message,
            )
        )
    }

    private fun <T> Set<T>.updated(
        value: T,
        isSelected: Boolean,
    ): Set<T> {
        return if (isSelected) {
            this + value
        } else {
            this - value
        }
    }
}