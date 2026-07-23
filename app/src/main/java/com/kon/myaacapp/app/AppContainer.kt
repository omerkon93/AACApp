package com.kon.myaacapp.app

import android.app.Application
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.repository.TileRepository
import com.kon.myaacapp.domain.usecase.analytics.ObserveUsageEventsUseCase
import com.kon.myaacapp.domain.usecase.tile.AddTileUseCase
import com.kon.myaacapp.domain.usecase.tile.AttachTileToCategoryUseCase
import com.kon.myaacapp.domain.usecase.tile.DeleteTileUseCase
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.domain.usecase.tile.RemoveTileFromCategoryUseCase
import com.kon.myaacapp.domain.usecase.tile.SwapTilePositionsUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileAudioUseCase
import com.kon.myaacapp.domain.usecase.tile.UpdateTileUseCase
import com.kon.myaacapp.ui.admin.grid.AdminGridViewModelFactory
import com.kon.myaacapp.ui.admin.layout.LayoutSettingsViewModelFactory
import com.kon.myaacapp.ui.admin.list.AdminListViewModelFactory
import com.kon.myaacapp.ui.admin.statistics.AdminStatisticsViewModelFactory
import com.kon.myaacapp.ui.admin.system.SystemSettingsViewModelFactory
import com.kon.myaacapp.ui.editor.TileEditorViewModelFactory
import com.kon.myaacapp.ui.profile.ProfileManagerViewModelFactory
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    application: Application,
    applicationScope: CoroutineScope,
) {
    private val database: AACDatabase =
        AACDatabase.getDatabase(application)

    val settingsRepository: SettingsRepository =
        SettingsRepository(application)

    val profileRepository: ProfileRepository =
        ProfileRepository(
            application,
            settingsRepository,
            applicationScope,
        )

    val aacRepository: AACRepository =
        AACRepository(
            aacTileDao = database.aacTileDao(),
            context = application,
            profileRepository = profileRepository,
        )

    val tileRepository: TileRepository =
        aacRepository

    val observeTilesUseCase: ObserveTilesUseCase =
        ObserveTilesUseCase(
            tileRepository = tileRepository,
        )

    val observeAllTilesUseCase: ObserveAllTilesUseCase =
        ObserveAllTilesUseCase(
            tileRepository = tileRepository,
        )

    val swapTilePositionsUseCase: SwapTilePositionsUseCase =
        SwapTilePositionsUseCase(
            tileRepository = tileRepository,
        )

    val attachTileToCategoryUseCase: AttachTileToCategoryUseCase =
        AttachTileToCategoryUseCase(
            tileRepository = tileRepository,
        )

    val removeTileFromCategoryUseCase:
            RemoveTileFromCategoryUseCase =
        RemoveTileFromCategoryUseCase(
            tileRepository = tileRepository,
        )

    val incrementTileClickUseCase: IncrementTileClickUseCase =
        IncrementTileClickUseCase(
            tileRepository = tileRepository,
        )

    val addTileUseCase: AddTileUseCase =
        AddTileUseCase(
            tileRepository = tileRepository,
        )

    val updateTileUseCase: UpdateTileUseCase =
        UpdateTileUseCase(
            tileRepository = tileRepository,
        )

    val updateTileAudioUseCase: UpdateTileAudioUseCase =
        UpdateTileAudioUseCase(
            tileRepository = tileRepository,
        )

    val deleteTileUseCase: DeleteTileUseCase =
        DeleteTileUseCase(
            tileRepository = tileRepository,
        )

    val tileEditorViewModelFactory: TileEditorViewModelFactory =
        TileEditorViewModelFactory(
            addTileUseCase = addTileUseCase,
            updateTileUseCase = updateTileUseCase,
            observeTilesUseCase = observeTilesUseCase,
        )

    val adminGridViewModelFactory: AdminGridViewModelFactory =
        AdminGridViewModelFactory(
            observeTilesUseCase = observeTilesUseCase,
            swapTilePositionsUseCase =
                swapTilePositionsUseCase,
        )

    val layoutSettingsViewModelFactory:
            LayoutSettingsViewModelFactory =
        LayoutSettingsViewModelFactory(
            settingsRepository = settingsRepository,
        )

    val adminListViewModelFactory:
            AdminListViewModelFactory =
        AdminListViewModelFactory(
            observeAllTilesUseCase =
                observeAllTilesUseCase,
            updateTileAudioUseCase =
                updateTileAudioUseCase,
        )

    val observeUsageEventsUseCase:
            ObserveUsageEventsUseCase =
        ObserveUsageEventsUseCase(
            tileRepository = tileRepository,
        )

    val adminStatisticsViewModelFactory =
        AdminStatisticsViewModelFactory(
            observeAllTilesUseCase =
                observeAllTilesUseCase,
            observeUsageEventsUseCase =
                observeUsageEventsUseCase,
        )

    val profileManagerViewModelFactory:
            ProfileManagerViewModelFactory =
        ProfileManagerViewModelFactory(
            profileRepository = profileRepository,
            aacRepository = aacRepository,
        )

    val systemSettingsViewModelFactory:
            SystemSettingsViewModelFactory =
        SystemSettingsViewModelFactory(
            application = application,
            settingsRepository = settingsRepository,
            aacRepository = aacRepository,
            profileRepository = profileRepository,
        )
}