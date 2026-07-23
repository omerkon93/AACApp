package com.kon.myaacapp.ui.communication

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase

class CommunicationViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val observeTilesUseCase: ObserveTilesUseCase,
    private val incrementTileClickUseCase: IncrementTileClickUseCase,
    private val communicationSessionController: CommunicationSessionController,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                CommunicationViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return CommunicationViewModel(
                application = application,
                settingsRepository =
                    settingsRepository,
                observeTilesUseCase =
                    observeTilesUseCase,
                incrementTileClickUseCase =
                    incrementTileClickUseCase,
                communicationSessionController =
                    communicationSessionController,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}