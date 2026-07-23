package com.kon.myaacapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.usecase.tile.AttachTileToCategoryUseCase
import com.kon.myaacapp.domain.usecase.tile.DeleteTileUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.RemoveTileFromCategoryUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService

class AdminDashboardViewModelFactory(
    private val settingsRepository:
    SettingsRepository,
    private val profileRepository:
    ProfileRepository,
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
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                AdminDashboardViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return AdminDashboardViewModel(
                settingsRepository =
                    settingsRepository,
                profileRepository =
                    profileRepository,
                observeAllTilesUseCase =
                    observeAllTilesUseCase,
                attachTileToCategoryUseCase =
                    attachTileToCategoryUseCase,
                removeTileFromCategoryUseCase =
                    removeTileFromCategoryUseCase,
                deleteTileUseCase =
                    deleteTileUseCase,
                audioRecordingService =
                    audioRecordingService,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}