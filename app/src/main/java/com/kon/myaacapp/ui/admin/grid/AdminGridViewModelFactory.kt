package com.kon.myaacapp.ui.admin.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.SwapTilePositionsUseCase

class AdminGridViewModelFactory(
    private val observeTilesUseCase: ObserveTilesUseCase,
    private val swapTilePositionsUseCase: SwapTilePositionsUseCase,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                AdminGridViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return AdminGridViewModel(
                observeTilesUseCase = observeTilesUseCase,
                swapTilePositionsUseCase =
                    swapTilePositionsUseCase,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}