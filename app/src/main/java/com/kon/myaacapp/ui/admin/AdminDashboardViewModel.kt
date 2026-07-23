package com.kon.myaacapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.usecase.tile.AttachTileToCategoryUseCase
import com.kon.myaacapp.domain.usecase.tile.DeleteTileUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.RemoveTileFromCategoryUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminDashboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val observeAllTilesUseCase:
    ObserveAllTilesUseCase,
    private val attachTileToCategoryUseCase:
    AttachTileToCategoryUseCase,
    private val removeTileFromCategoryUseCase:
    RemoveTileFromCategoryUseCase,
    private val deleteTileUseCase:
    DeleteTileUseCase,
    private val audioRecordingService:
    AudioRecordingService,
) : ViewModel() {

    private val currentParentId =
        MutableStateFlow<String?>(null)

    private val navigationStack =
        mutableListOf<String?>()

    private val operationState =
        MutableStateFlow(
            AdminDashboardOperationState()
        )

    private val _effects =
        MutableSharedFlow<AdminDashboardEffect>()

    val effects =
        _effects.asSharedFlow()

    private val languageCode =
        settingsRepository.languageCodeFlow
            .map { value ->
                LocaleHelper.normalize(value)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = "he",
            )

    private val allTiles =
        languageCode.flatMapLatest { language ->
            observeAllTilesUseCase(
                languageCode = language,
            )
        }

    private val gridSizeSettings =
        combine(
            settingsRepository.gridColumnsFlow,
            settingsRepository.gridRowsFlow,
        ) { columns, rows ->
            AdminGridSizeSettings(
                columns = columns,
                rows = rows,
            )
        }

    private val gridScaleSettings =
        combine(
            settingsRepository.gridTileScaleFlow,
            settingsRepository
                .gridTileContainerScaleFlow,
        ) { tileScale, containerScale ->
            AdminGridScaleSettings(
                tileScale = tileScale,
                containerScale = containerScale,
            )
        }

    private val gridSettings =
        combine(
            gridSizeSettings,
            gridScaleSettings,
        ) { size, scale ->
            AdminDashboardGridSettings(
                columns = size.columns,
                rows = size.rows,
                tileScale = scale.tileScale,
                containerScale =
                    scale.containerScale,
            )
        }

    private val dashboardContent =
        combine(
            currentParentId,
            languageCode,
            allTiles,
        ) {
                parentId,
                language,
                tiles ->

            AdminDashboardContent(
                currentParentId = parentId,
                languageCode = language,
                allTiles = tiles,
                allCategories =
                    tiles.filter { tile ->
                        tile.isCategory
                    },
            )
        }

    val state: StateFlow<AdminDashboardState> =
        combine(
            dashboardContent,
            gridSettings,
            operationState,
        ) {
                content,
                grid,
                operation ->

            AdminDashboardState(
                currentParentId =
                    content.currentParentId,

                languageCode =
                    content.languageCode,

                allTiles =
                    content.allTiles,

                allCategories =
                    content.allCategories,

                gridColumns =
                    grid.columns,

                gridRows =
                    grid.rows,

                gridTileScale =
                    grid.tileScale,

                gridTileContainerScale =
                    grid.containerScale,

                isDeletingTile =
                    operation.isDeletingTile,

                errorMessage =
                    operation.errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5_000,
                ),
            initialValue =
                AdminDashboardState(),
        )

    fun onAction(
        action: AdminDashboardAction,
    ) {
        when (action) {
            is AdminDashboardAction.OpenCategory -> {
                openCategory(
                    categoryId = action.categoryId,
                )
            }

            AdminDashboardAction.NavigateUp -> {
                navigateUp()
            }

            AdminDashboardAction.ResetToHome -> {
                resetToHome()
            }

            is AdminDashboardAction
            .AttachTileToCategory -> {
                attachTileToCurrentCategory(
                    tileId = action.tileId,
                    cellIndex = action.cellIndex,
                )
            }

            is AdminDashboardAction
            .RemoveTileFromCategory -> {
                removeTileFromCurrentCategory(
                    tileId = action.tileId,
                )
            }

            is AdminDashboardAction.DeleteTile -> {
                deleteTile(
                    tile = action.tile,
                )
            }

            AdminDashboardAction.ErrorConsumed -> {
                operationState.update { currentState ->
                    currentState.copy(
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun openCategory(
        categoryId: String,
    ) {
        if (
            categoryId.isBlank() ||
            currentParentId.value == categoryId
        ) {
            return
        }

        navigationStack.add(
            currentParentId.value
        )

        currentParentId.value =
            categoryId
    }

    private fun navigateUp() {
        currentParentId.value =
            if (navigationStack.isNotEmpty()) {
                navigationStack.removeAt(
                    navigationStack.lastIndex
                )
            } else {
                null
            }
    }

    private fun resetToHome() {
        navigationStack.clear()
        currentParentId.value = null
    }

    private fun attachTileToCurrentCategory(
        tileId: String,
        cellIndex: Int?,
    ) {
        if (tileId.isBlank()) {
            return
        }

        viewModelScope.launch {
            runCatching {
                attachTileToCategoryUseCase(
                    tileId = tileId,
                    parentId =
                        currentParentId.value,
                    languageCode =
                        languageCode.value,
                    cellIndex = cellIndex,
                )
            }.onFailure { error ->
                showError(
                    message =
                        error.message
                            ?: "Failed to attach tile.",
                )
            }
        }
    }

    private fun removeTileFromCurrentCategory(
        tileId: String,
    ) {
        if (tileId.isBlank()) {
            return
        }

        viewModelScope.launch {
            runCatching {
                removeTileFromCategoryUseCase(
                    tileId = tileId,
                    parentId =
                        currentParentId.value,
                    languageCode =
                        languageCode.value,
                )
            }.onFailure { error ->
                showError(
                    message =
                        error.message
                            ?: "Failed to remove tile.",
                )
            }
        }
    }

    private fun deleteTile(
        tile: CombinedTile,
    ) {
        if (
            tile.id.isBlank() ||
            operationState.value.isDeletingTile
        ) {
            return
        }

        operationState.update { currentState ->
            currentState.copy(
                isDeletingTile = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                /*
                 * Delete persisted tile data first.
                 * The audio file is deleted only after
                 * persistence succeeds.
                 */
                deleteTileUseCase(tile)

                profileRepository.profiles.value
                    .forEach { profile ->
                        val updatedLayout =
                            profile.layout.filterValues {
                                    layoutState ->
                                layoutState.tileId !=
                                        tile.id
                            }

                        if (
                            updatedLayout.size !=
                            profile.layout.size
                        ) {
                            profileRepository
                                .updateActiveProfile(
                                    profile.copy(
                                        layout =
                                            updatedLayout,
                                    )
                                )
                        }
                    }

                tile.audioUri?.let { audioUri ->
                    audioRecordingService
                        .deleteRecording(audioUri)
                }
            }.onSuccess {
                operationState.update { currentState ->
                    currentState.copy(
                        isDeletingTile = false,
                    )
                }
            }.onFailure { error ->
                operationState.update { currentState ->
                    currentState.copy(
                        isDeletingTile = false,
                    )
                }

                showError(
                    message =
                        error.message
                            ?: "Failed to delete tile.",
                )
            }
        }
    }

    private fun showError(
        message: String,
    ) {
        operationState.update { currentState ->
            currentState.copy(
                errorMessage = message,
            )
        }

        viewModelScope.launch {
            _effects.emit(
                AdminDashboardEffect.ShowError(
                    message = message,
                )
            )
        }
    }
}

private data class AdminDashboardContent(
    val currentParentId: String?,
    val languageCode: String,
    val allTiles: List<CombinedTile>,
    val allCategories: List<CombinedTile>,
)

private data class AdminGridSizeSettings(
    val columns: Int,
    val rows: Int,
)

private data class AdminGridScaleSettings(
    val tileScale: Float,
    val containerScale: Float,
)

private data class AdminDashboardGridSettings(
    val columns: Int,
    val rows: Int,
    val tileScale: Float,
    val containerScale: Float,
)

private data class AdminDashboardOperationState(
    val isDeletingTile: Boolean = false,
    val errorMessage: String? = null,
)