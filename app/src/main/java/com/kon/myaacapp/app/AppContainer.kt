package com.kon.myaacapp.app

import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.service.backup.BackupService
import android.app.Activity
import android.app.Application
import com.kon.myaacapp.BuildConfig
import com.kon.myaacapp.data.billing.AndroidPlayBillingGateway
import com.kon.myaacapp.data.billing.PlayBillingFamilyEntitlementProvider
import com.kon.myaacapp.data.billing.SharedPrefsFamilyEntitlementCache
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
import java.lang.ref.WeakReference
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
import com.kon.myaacapp.ui.communication.CommunicationViewModelFactory
import com.kon.myaacapp.ui.editor.TileEditorViewModelFactory
import com.kon.myaacapp.ui.profile.ProfileManagerViewModelFactory
import kotlinx.coroutines.CoroutineScope
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.service.audio.AudioPreviewManager
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.ui.admin.AdminDashboardViewModelFactory

class AppContainer(
    application: Application,
    applicationScope: CoroutineScope,
) {
    @Volatile
    private var currentActivityRef: WeakReference<Activity?> = WeakReference(null)

    fun updateCurrentActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    fun clearCurrentActivityIfMatching(activity: Activity) {
        if (currentActivityRef.get() === activity) {
            currentActivityRef = WeakReference(null)
        }
    }

    private val database: AACDatabase =
        AACDatabase.getDatabase(application)

    val settingsRepository: SettingsRepository =
        SettingsRepository(application)

    val familyEntitlementProvider: FamilyEntitlementProvider =
        PlayBillingFamilyEntitlementProvider(
            appPackageName = application.packageName,
            productId = BuildConfig.FAMILY_SUBSCRIPTION_PRODUCT_ID,
            preferredBasePlanId = BuildConfig.FAMILY_SUBSCRIPTION_BASE_PLAN_ID,
            isProfileCapEnforced = BuildConfig.ENFORCE_PROFILE_CAP,
            gateway = AndroidPlayBillingGateway(
                context = application,
                currentActivityProvider = { currentActivityRef.get() },
            ),
            cache = SharedPrefsFamilyEntitlementCache(application),
            scope = applicationScope,
        )

    val profileRepository: ProfileRepository =
        ProfileRepository(
            context = application,
            settingsRepository = settingsRepository,
            scope = applicationScope,
            familyEntitlementProvider = familyEntitlementProvider,
        )

    val aacRepository: AACRepository =
        AACRepository(
            aacTileDao = database.aacTileDao(),
            context = application,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )

    val backupService: BackupService =
        BackupService(
            context = application,
            repository = aacRepository,
            database = database,
            settingsRepository = settingsRepository,
        )

    val startupCoordinator:
            AppStartupCoordinator =
        AppStartupCoordinator(
            context = application,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )

    val tileRepository: TileRepository =
        aacRepository

    val audioRecordingService:
            AudioRecordingService =
        AudioRecordingService(application)

    val audioPreviewManager:
            AudioPreviewManager =
        AudioPreviewManager(
            application = application,
            settingsRepository = settingsRepository,
            scope = applicationScope,
            audioService = audioRecordingService,
            profileRepository = profileRepository,
        )

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

    val communicationSessionController =
        CommunicationSessionController()

    val adminDashboardViewModelFactory:
            AdminDashboardViewModelFactory =
        AdminDashboardViewModelFactory(
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
            familyEntitlementProvider = familyEntitlementProvider,
        )

    val systemSettingsViewModelFactory:
            SystemSettingsViewModelFactory =
        SystemSettingsViewModelFactory(
            application = application,
            settingsRepository = settingsRepository,
            aacRepository = aacRepository,
            profileRepository = profileRepository,
            backupService = backupService,
        )

    val communicationViewModelFactory:
            CommunicationViewModelFactory =
        CommunicationViewModelFactory(
            application = application,
            settingsRepository = settingsRepository,
            observeTilesUseCase =
                observeTilesUseCase,
            incrementTileClickUseCase =
                incrementTileClickUseCase,
            communicationSessionController =
                communicationSessionController,
            profileRepository = profileRepository,
        )
}