package com.kon.myaacapp.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kon.myaacapp.domain.model.CombinedTile

@Composable
fun TileEditorRoute(
    editorSessionKey: Int,
    existingTile: CombinedTile?,
    initialCellIndex: Int?,
    currentParentId: String?,
    languageCode: String,
    categories: List<CombinedTile>,
    viewModelFactory: TileEditorViewModelFactory,
    onDismiss: () -> Unit,
    onError: (String) -> Unit = {},
    content: @Composable (
        state: TileEditorState,
        onAction: (TileEditorAction) -> Unit,
    ) -> Unit,
) {
    val editorKey = buildString {
        append("tile-editor-")
        append(editorSessionKey)
        append("-")
        append(existingTile?.id ?: "new")
        append("-")
        append(initialCellIndex ?: "unassigned")
    }

    val tileEditorViewModel: TileEditorViewModel = viewModel(
        key = editorKey,
        factory = viewModelFactory,
    )

    val state by tileEditorViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        tileEditorViewModel,
        existingTile?.id,
        initialCellIndex,
        currentParentId,
        languageCode,
    ) {
        tileEditorViewModel.initialize(
            existingTile = existingTile,
            initialCellIndex = initialCellIndex,
            currentParentId = currentParentId,
            languageCode = languageCode,
            categories = categories,
        )
    }

    LaunchedEffect(
        tileEditorViewModel,
        categories,
    ) {
        tileEditorViewModel.updateCategories(
            categories = categories,
        )
    }

    LaunchedEffect(tileEditorViewModel) {
        tileEditorViewModel.effects.collect { effect ->
            when (effect) {
                TileEditorEffect.CloseEditor -> {
                    onDismiss()
                }

                is TileEditorEffect.ShowError -> {
                    onError(effect.message)
                }
            }
        }
    }

    content(
        state,
        tileEditorViewModel::onAction,
    )
}