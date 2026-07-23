package com.kon.myaacapp.ui.admin.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kon.myaacapp.domain.model.CombinedTile

@Composable
fun AdminGridRoute(
    currentParentId: String?,
    languageCode: String,
    gridColumns: Int,
    gridRows: Int,
    gridTileScale: Float,
    gridTileContainerScale: Float,
    viewModelFactory: AdminGridViewModelFactory,
    onEditTile: (CombinedTile) -> Unit,
    onCreateTile: (Int) -> Unit,
    onError: (String) -> Unit = {},
    content: @Composable (
        state: AdminGridState,
        onAction: (AdminGridAction) -> Unit,
    ) -> Unit,
) {
    val adminGridViewModel: AdminGridViewModel = viewModel(
        key = "admin-grid",
        factory = viewModelFactory,
    )

    val state by adminGridViewModel.state
        .collectAsStateWithLifecycle()

    /*
     * Keep the feature ViewModel synchronized with the current
     * category, language, and layout settings.
     */
    LaunchedEffect(
        adminGridViewModel,
        currentParentId,
        languageCode,
        gridColumns,
        gridRows,
        gridTileScale,
        gridTileContainerScale,
    ) {
        adminGridViewModel.updateConfiguration(
            parentId = currentParentId,
            languageCode = languageCode,
            gridColumns = gridColumns,
            gridRows = gridRows,
            gridTileScale = gridTileScale,
            gridTileContainerScale =
                gridTileContainerScale,
        )
    }

    /*
     * Handle one-time navigation and error events.
     */
    LaunchedEffect(adminGridViewModel) {
        adminGridViewModel.effects.collect { effect ->
            when (effect) {
                is AdminGridEffect.OpenTileActions -> {
                    onEditTile(effect.tile)
                }

                is AdminGridEffect.OpenTileCreator -> {
                    onCreateTile(effect.cellIndex)
                }

                is AdminGridEffect.ShowError -> {
                    onError(effect.message)
                }
            }
        }
    }

    content(
        state,
        adminGridViewModel::onAction,
    )
}