package com.kon.myaacapp.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.domain.usecase.tile.AddTileUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase

class TileEditorViewModelFactory(
    private val addTileUseCase: AddTileUseCase,
    private val updateTileUseCase: UpdateTileUseCase,
    private val observeTilesUseCase: ObserveTilesUseCase,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                TileEditorViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return TileEditorViewModel(
                addTileUseCase = addTileUseCase,
                updateTileUseCase = updateTileUseCase,
                observeTilesUseCase = observeTilesUseCase,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}