package com.kon.myaacapp.ui.admin.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.SwapTilePositionsUseCase
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

class AdminGridViewModel(
    private val observeTilesUseCase: ObserveTilesUseCase,
    private val swapTilePositionsUseCase: SwapTilePositionsUseCase,
) : ViewModel() {

    private val _state =
        MutableStateFlow(AdminGridState())

    val state: StateFlow<AdminGridState> =
        _state.asStateFlow()

    private val _effects =
        MutableSharedFlow<AdminGridEffect>()

    val effects: SharedFlow<AdminGridEffect> =
        _effects.asSharedFlow()

    private var observeTilesJob: Job? = null

    private var observedParentId: String? = null
    private var observedLanguageCode: String? = null

    fun updateConfiguration(
        parentId: String?,
        languageCode: String,
        gridColumns: Int,
        gridRows: Int,
        gridTileScale: Float,
        gridTileContainerScale: Float,
    ) {
        _state.update { currentState ->
            currentState.copy(
                currentParentId = parentId,
                languageCode = languageCode,
                gridColumns =
                    gridColumns.coerceAtLeast(1),
                gridRows =
                    gridRows.coerceAtLeast(1),
                gridTileScale = gridTileScale,
                gridTileContainerScale =
                    gridTileContainerScale.coerceIn(
                        minimumValue = 0.1f,
                        maximumValue = 1f,
                    ),
            )
        }

        observeTilesIfNeeded(
            parentId = parentId,
            languageCode = languageCode,
        )
    }

    fun onAction(
        action: AdminGridAction,
    ) {
        when (action) {
            is AdminGridAction.TileEditClicked -> {
                openTileActions(action.tile)
            }

            is AdminGridAction.EmptyCellClicked -> {
                openTileCreator(action.cellIndex)
            }

            is AdminGridAction.DragStarted -> {
                _state.update { currentState ->
                    currentState.copy(
                        draggedIndex = action.cellIndex,
                        hoveredIndex = action.cellIndex,
                    )
                }
            }

            is AdminGridAction.DragHovered -> {
                _state.update { currentState ->
                    currentState.copy(
                        hoveredIndex = action.cellIndex,
                    )
                }
            }

            AdminGridAction.DragEnded -> {
                completeDrag()
            }

            AdminGridAction.DragCancelled -> {
                clearDragState()
            }

            AdminGridAction.ErrorConsumed -> {
                _state.update { currentState ->
                    currentState.copy(
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun observeTilesIfNeeded(
        parentId: String?,
        languageCode: String,
    ) {
        if (
            observedParentId == parentId &&
            observedLanguageCode == languageCode &&
            observeTilesJob?.isActive == true
        ) {
            return
        }

        observedParentId = parentId
        observedLanguageCode = languageCode

        observeTilesJob?.cancel()

        _state.update { currentState ->
            currentState.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        observeTilesJob = viewModelScope.launch {
            observeTilesUseCase(
                parentId = parentId,
                languageCode = languageCode,
            )
                .catch { error ->
                    val message =
                        error.message
                            ?: "Failed to load grid tiles."

                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = message,
                        )
                    }

                    _effects.emit(
                        AdminGridEffect.ShowError(
                            message = message,
                        )
                    )
                }
                .collect { tiles ->
                    _state.update { currentState ->
                        currentState.copy(
                            tiles = tiles,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private fun openTileActions(
        tile: com.kon.myaacapp.domain.model.CombinedTile,
    ) {
        _state.update { currentState ->
            currentState.copy(
                selectedTile = tile,
            )
        }

        viewModelScope.launch {
            _effects.emit(
                AdminGridEffect.OpenTileActions(
                    tile = tile,
                )
            )
        }
    }

    private fun openTileCreator(
        cellIndex: Int,
    ) {
        if (cellIndex < 0) {
            showError(
                message = "Cell index cannot be negative.",
            )

            return
        }

        viewModelScope.launch {
            _effects.emit(
                AdminGridEffect.OpenTileCreator(
                    cellIndex = cellIndex,
                )
            )
        }
    }

    private fun completeDrag() {
        val currentState = _state.value

        val fromIndex =
            currentState.draggedIndex

        val toIndex =
            currentState.hoveredIndex

        clearDragState()

        if (
            fromIndex == null ||
            toIndex == null ||
            fromIndex == toIndex
        ) {
            return
        }

        viewModelScope.launch {
            runCatching {
                swapTilePositionsUseCase(
                    parentId =
                        currentState.currentParentId,
                    fromIndex = fromIndex,
                    toIndex = toIndex,
                )
            }.onFailure { error ->
                showError(
                    message = error.message
                        ?: "Failed to move tile.",
                )
            }
        }
    }

    private fun clearDragState() {
        _state.update { currentState ->
            currentState.copy(
                draggedIndex = null,
                hoveredIndex = null,
            )
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

        viewModelScope.launch {
            _effects.emit(
                AdminGridEffect.ShowError(
                    message = message,
                )
            )
        }
    }
}