package com.kon.myaacapp.ui.admin.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileAudioUseCase

class AdminListViewModelFactory(
    private val observeAllTilesUseCase:
    ObserveAllTilesUseCase,
    private val updateTileAudioUseCase:
    UpdateTileAudioUseCase,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                AdminListViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return AdminListViewModel(
                observeAllTilesUseCase =
                    observeAllTilesUseCase,
                updateTileAudioUseCase =
                    updateTileAudioUseCase,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}