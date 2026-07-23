package com.kon.myaacapp.ui.admin.layout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LayoutSettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state =
        MutableStateFlow(LayoutSettingsState())

    val state: StateFlow<LayoutSettingsState> =
        _state.asStateFlow()

    private val _effects =
        MutableSharedFlow<LayoutSettingsEffect>()

    val effects: SharedFlow<LayoutSettingsEffect> =
        _effects.asSharedFlow()

    init {
        observeLayoutSettings()
    }

    fun onAction(
        action: LayoutSettingsAction,
    ) {
        when (action) {
            is LayoutSettingsAction.GridColumnsChanged -> {
                updateGridColumns(action.value)
            }

            is LayoutSettingsAction.GridRowsChanged -> {
                updateGridRows(action.value)
            }

            is LayoutSettingsAction.GridTileScaleChanged -> {
                updateGridTileScale(action.value)
            }

            is LayoutSettingsAction
            .GridTileContainerScaleChanged -> {
                updateGridTileContainerScale(
                    action.value
                )
            }

            is LayoutSettingsAction.BarTileImageScaleChanged -> {
                updateBarTileImageScale(action.value)
            }

            is LayoutSettingsAction.BarTileTitleScaleChanged -> {
                updateBarTileTitleScale(action.value)
            }

            is LayoutSettingsAction.ActionButtonScaleChanged -> {
                updateActionButtonScale(action.value)
            }

            is LayoutSettingsAction.ShowSentenceBarChanged -> {
                updateShowSentenceBar(action.value)
            }

            is LayoutSettingsAction.ShowBackButtonChanged -> {
                updateShowBackButton(action.value)
            }

            is LayoutSettingsAction
            .ShowBackspaceButtonChanged -> {
                updateShowBackspaceButton(
                    action.value
                )
            }

            is LayoutSettingsAction.ShowSpeakButtonChanged -> {
                updateShowSpeakButton(action.value)
            }

            is LayoutSettingsAction.HomeInActionBarChanged -> {
                updateHomeInActionBar(action.value)
            }

            LayoutSettingsAction.SaveCurrentAsDefault -> {
                saveCurrentAsDefault()
            }

            LayoutSettingsAction.RestoreDefault -> {
                restoreDefault()
            }

            LayoutSettingsAction.ErrorConsumed -> {
                _state.update { currentState ->
                    currentState.copy(
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun observeLayoutSettings() {
        viewModelScope.launch {
            combine(
                observeGridSettings(),
                observeBarSettings(),
                observeVisibilitySettings(),
            ) {
                    gridSettings,
                    barSettings,
                    visibilitySettings ->

                CombinedLayoutSettings(
                    gridSettings = gridSettings,
                    barSettings = barSettings,
                    visibilitySettings =
                        visibilitySettings,
                )
            }.collect { combinedSettings ->
                _state.update { currentState ->
                    currentState.copy(
                        gridColumns =
                            combinedSettings
                                .gridSettings
                                .gridColumns,

                        gridRows =
                            combinedSettings
                                .gridSettings
                                .gridRows,

                        gridTileScale =
                            combinedSettings
                                .gridSettings
                                .gridTileScale,

                        gridTileContainerScale =
                            combinedSettings
                                .gridSettings
                                .gridTileContainerScale,

                        barTileImageScale =
                            combinedSettings
                                .barSettings
                                .barTileImageScale,

                        barTileTitleScale =
                            combinedSettings
                                .barSettings
                                .barTileTitleScale,

                        actionButtonScale =
                            combinedSettings
                                .barSettings
                                .actionButtonScale,

                        showSentenceBar =
                            combinedSettings
                                .visibilitySettings
                                .showSentenceBar,

                        showBackButton =
                            combinedSettings
                                .visibilitySettings
                                .showBackButton,

                        showBackspaceButton =
                            combinedSettings
                                .visibilitySettings
                                .showBackspaceButton,

                        showSpeakButton =
                            combinedSettings
                                .visibilitySettings
                                .showSpeakButton,

                        homeInActionBar =
                            combinedSettings
                                .visibilitySettings
                                .homeInActionBar,
                    )
                }
            }
        }
    }

    private fun observeGridSettings() =
        combine(
            settingsRepository.gridColumnsFlow,
            settingsRepository.gridRowsFlow,
            settingsRepository.gridTileScaleFlow,
            settingsRepository.gridTileContainerScaleFlow,
        ) {
                gridColumns,
                gridRows,
                gridTileScale,
                gridTileContainerScale ->

            GridSettings(
                gridColumns =
                    gridColumns.coerceIn(
                        minimumValue = 1,
                        maximumValue = 8,
                    ),

                gridRows =
                    gridRows.coerceIn(
                        minimumValue = 1,
                        maximumValue = 10,
                    ),

                gridTileScale =
                    gridTileScale.coerceIn(
                        minimumValue = 0.5f,
                        maximumValue = 2f,
                    ),

                gridTileContainerScale =
                    gridTileContainerScale.coerceIn(
                        minimumValue = 0.5f,
                        maximumValue = 1f,
                    ),
            )
        }

    private fun observeBarSettings() =
        combine(
            settingsRepository.barTileImageScaleFlow,
            settingsRepository.barTileTitleScaleFlow,
            settingsRepository.actionButtonScaleFlow,
        ) {
                barTileImageScale,
                barTileTitleScale,
                actionButtonScale ->

            BarSettings(
                barTileImageScale =
                    barTileImageScale.coerceIn(
                        minimumValue = 0.5f,
                        maximumValue = 2f,
                    ),

                barTileTitleScale =
                    barTileTitleScale.coerceIn(
                        minimumValue = 0.5f,
                        maximumValue = 2f,
                    ),

                actionButtonScale =
                    actionButtonScale.coerceIn(
                        minimumValue = 0.5f,
                        maximumValue = 2f,
                    ),
            )
        }

    private fun observeVisibilitySettings() =
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

            VisibilitySettings(
                showSentenceBar = showSentenceBar,
                showBackButton = showBackButton,
                showBackspaceButton =
                    showBackspaceButton,
                showSpeakButton = showSpeakButton,
                homeInActionBar = homeInActionBar,
            )
        }

    private fun updateGridColumns(
        value: Int,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 1,
                maximumValue = 8,
            )

        persistSetting {
            settingsRepository.updateGridColumns(
                normalizedValue
            )
        }
    }

    private fun updateGridRows(
        value: Int,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 1,
                maximumValue = 10,
            )

        persistSetting {
            settingsRepository.updateGridRows(
                normalizedValue
            )
        }
    }

    private fun updateGridTileScale(
        value: Float,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 0.5f,
                maximumValue = 2f,
            )

        persistSetting {
            settingsRepository.updateGridTileScale(
                normalizedValue
            )
        }
    }

    private fun updateGridTileContainerScale(
        value: Float,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 0.5f,
                maximumValue = 1f,
            )

        persistSetting {
            settingsRepository
                .updateGridTileContainerScale(
                    normalizedValue
                )
        }
    }

    private fun updateBarTileImageScale(
        value: Float,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 0.5f,
                maximumValue = 2f,
            )

        persistSetting {
            settingsRepository
                .updateBarTileImageScale(
                    normalizedValue
                )
        }
    }

    private fun updateBarTileTitleScale(
        value: Float,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 0.5f,
                maximumValue = 2f,
            )

        persistSetting {
            settingsRepository
                .updateBarTileTitleScale(
                    normalizedValue
                )
        }
    }

    private fun updateActionButtonScale(
        value: Float,
    ) {
        val normalizedValue =
            value.coerceIn(
                minimumValue = 0.5f,
                maximumValue = 2f,
            )

        persistSetting {
            settingsRepository
                .updateActionButtonScale(
                    normalizedValue
                )
        }
    }

    private fun updateShowSentenceBar(
        value: Boolean,
    ) {
        persistSetting {
            settingsRepository
                .updateShowSentenceBar(value)
        }
    }

    private fun updateShowBackButton(
        value: Boolean,
    ) {
        persistSetting {
            settingsRepository
                .updateShowBackButton(value)
        }
    }

    private fun updateShowBackspaceButton(
        value: Boolean,
    ) {
        persistSetting {
            settingsRepository
                .updateShowBackspaceButton(value)
        }
    }

    private fun updateShowSpeakButton(
        value: Boolean,
    ) {
        persistSetting {
            settingsRepository
                .updateShowSpeakButton(value)
        }
    }

    private fun updateHomeInActionBar(
        value: Boolean,
    ) {
        persistSetting {
            settingsRepository
                .updateHomeInActionBar(value)
        }
    }

    private fun saveCurrentAsDefault() {
        if (_state.value.isBusy) {
            return
        }

        _state.update { currentState ->
            currentState.copy(
                isSavingDefault = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                settingsRepository
                    .saveCurrentLayoutAsDefault()
            }.onSuccess {
                _state.update { currentState ->
                    currentState.copy(
                        isSavingDefault = false,
                    )
                }

                _effects.emit(
                    LayoutSettingsEffect
                        .CurrentLayoutSavedAsDefault
                )
            }.onFailure { error ->
                _state.update { currentState ->
                    currentState.copy(
                        isSavingDefault = false,
                    )
                }

                showError(
                    error.message
                        ?: "Failed to save the current layout as default."
                )
            }
        }
    }

    private fun restoreDefault() {
        if (_state.value.isBusy) {
            return
        }

        _state.update { currentState ->
            currentState.copy(
                isRestoringDefault = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                settingsRepository
                    .restoreDefaultLayoutSettings()
            }.onSuccess {
                _state.update { currentState ->
                    currentState.copy(
                        isRestoringDefault = false,
                    )
                }

                _effects.emit(
                    LayoutSettingsEffect
                        .DefaultLayoutRestored
                )
            }.onFailure { error ->
                _state.update { currentState ->
                    currentState.copy(
                        isRestoringDefault = false,
                    )
                }

                showError(
                    error.message
                        ?: "Failed to restore the default layout."
                )
            }
        }
    }

    private fun persistSetting(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                operation()
            }.onFailure { error ->
                showError(
                    error.message
                        ?: "Failed to update layout setting."
                )
            }
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
                LayoutSettingsEffect.ShowError(
                    message = message,
                )
            )
        }
    }
}

private data class GridSettings(
    val gridColumns: Int,
    val gridRows: Int,
    val gridTileScale: Float,
    val gridTileContainerScale: Float,
)

private data class BarSettings(
    val barTileImageScale: Float,
    val barTileTitleScale: Float,
    val actionButtonScale: Float,
)

private data class VisibilitySettings(
    val showSentenceBar: Boolean,
    val showBackButton: Boolean,
    val showBackspaceButton: Boolean,
    val showSpeakButton: Boolean,
    val homeInActionBar: Boolean,
)

private data class CombinedLayoutSettings(
    val gridSettings: GridSettings,
    val barSettings: BarSettings,
    val visibilitySettings: VisibilitySettings,
)