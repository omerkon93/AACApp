package com.kon.myaacapp

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kon.myaacapp.core.locale.DownloadStatus
import com.kon.myaacapp.core.locale.LanguageDownloadHelper
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ExportedBoardSettings
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.audio.AudioPreviewManager
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TextToSpeechHelper
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.ui.admin.system.SystemSettingsAction
import com.kon.myaacapp.ui.admin.system.SystemSettingsEffect
import com.kon.myaacapp.ui.admin.system.SystemSettingsViewModel
import com.kon.myaacapp.ui.communication.CommunicationAction
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.ui.communication.CommunicationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageSeparationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appContext: Context
    private lateinit var application: Application
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        filesDir = tempFolder.newFolder("app_files")
        appContext = mock(Context::class.java)
        application = mock(Application::class.java)
        whenever(appContext.filesDir).thenReturn(filesDir)
        whenever(appContext.applicationContext).thenReturn(appContext)
        whenever(application.filesDir).thenReturn(filesDir)
        whenever(application.applicationContext).thenReturn(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class InMemoryDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val flow = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = flow

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            return mutex.withLock {
                val updated = transform(flow.value)
                flow.value = updated
                updated
            }
        }
    }

    private fun createSettingsRepository(): SettingsRepository {
        return SettingsRepository(appContext, InMemoryDataStore())
    }

    @Test
    fun `interfaceLanguageCode falls back to legacy languageCode and stays independent once updated`() =
        runTest(testDispatcher) {
            val settings = createSettingsRepository()

            settings.updateLanguageCode("en")
            assertEquals("en", settings.interfaceLanguageCodeFlow.first())

            settings.updateInterfaceLanguageCode("he")
            assertEquals("he", settings.interfaceLanguageCodeFlow.first())
            assertEquals("en", settings.languageCodeFlow.first())
            assertTrue(settings.isLanguageSeparationMigratedFlow.first())
        }

    @Test
    fun `applyExportedBoardSettings preserves destination interface preference for v2 and v3 backups`() =
        runTest(testDispatcher) {
            val settings = createSettingsRepository()

            // Destination explicitly uses English interface
            settings.updateInterfaceLanguageCode("en")

            // Legacy v2 backup only had languageCode = "he" (boardLanguageCode = null)
            val v2Settings = ExportedBoardSettings(
                activeProfileId = "he_profile",
                languageCode = "he",
                boardLanguageCode = null,
            )
            assertEquals("he", v2Settings.resolvedBoardLanguageCode())

            settings.applyExportedBoardSettings(v2Settings, resolvedActiveProfileId = "he_profile")

            assertEquals("en", settings.interfaceLanguageCodeFlow.first())
            assertEquals("he", settings.languageCodeFlow.first())
            assertEquals("he_profile", settings.activeProfileIdFlow.first())

            // Snapshot & rollback preserves both interfaceLanguageCode and migration flag
            val snapshot = settings.readRollbackSettingsSnapshot()
            assertEquals("en", snapshot.interfaceLanguageCode)
            assertEquals("he", snapshot.languageCode)
            assertEquals(true, snapshot.languageSeparationMigrated)

            settings.updateInterfaceLanguageCode("he")
            settings.restoreSettingsSnapshot(snapshot)
            assertEquals("en", settings.interfaceLanguageCodeFlow.first())
        }

    @Test
    fun `applyExportedBoardSettings freezes pre-restore legacy interface language if unset`() =
        runTest(testDispatcher) {
            val settings = createSettingsRepository()

            // Destination never explicitly set INTERFACE_LANGUAGE_CODE, only legacy LANGUAGE_CODE = "en"
            settings.updateLanguageCode("en")
            assertFalse(settings.hasExplicitInterfaceLanguagePreference())

            val restoredHebrewBoard = ExportedBoardSettings(
                activeProfileId = "he_profile",
                languageCode = "he",
                boardLanguageCode = "he",
            )
            settings.applyExportedBoardSettings(restoredHebrewBoard, "he_profile")

            assertEquals("en", settings.interfaceLanguageCodeFlow.first())
            assertEquals("he", settings.languageCodeFlow.first())
        }

    @Test
    fun `systemSettingsViewModel updates only interface language and supports retry after error`() =
        runTest(testDispatcher) {
            val settings = createSettingsRepository()
            settings.updateLanguageCode("he")
            settings.updateInterfaceLanguageCode("he")

            val aacRepository = mock(AACRepository::class.java)
            val profileRepository = mock(ProfileRepository::class.java)
            val backupService = mock(BackupService::class.java)
            whenever(backupService.settingsRepository).thenReturn(settings)

            val downloadStatusFlow = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
            val helper = mock(LanguageDownloadHelper::class.java)
            whenever(helper.downloadStatus).thenReturn(downloadStatusFlow)

            var shouldSucceed = false
            doAnswer { invocation ->
                val callback = invocation.getArgument<(Boolean) -> Unit>(1)
                if (shouldSucceed) {
                    downloadStatusFlow.value = DownloadStatus.Success
                    callback(true)
                } else {
                    downloadStatusFlow.value = DownloadStatus.Error("Offline error")
                    callback(false)
                }
            }.whenever(helper).downloadLanguage(any(), any())

            doAnswer {
                if (downloadStatusFlow.value is DownloadStatus.Error) {
                    downloadStatusFlow.value = DownloadStatus.Idle
                }
            }.whenever(helper).clearError()

            val vm = SystemSettingsViewModel(
                application = application,
                settingsRepository = settings,
                aacRepository = aacRepository,
                profileRepository = profileRepository,
                backupService = backupService,
                languageDownloadHelper = helper,
            )

            val collectJob = backgroundScope.launch {
                vm.state.collect {}
            }
            vm.state.first { it.languageCode == "he" }

            // First attempt fails -> stays on "he", UI remains usable, retry code tracked
            vm.onAction(SystemSettingsAction.LanguageChanged("en"))
            testScheduler.advanceUntilIdle()

            assertEquals("he", settings.interfaceLanguageCodeFlow.first())
            assertEquals("en", vm.state.value.pendingLanguageRetryCode)
            assertTrue(vm.state.value.canChangeLanguage)

            // Retry succeeds -> updates interface language only, never touches prepareLanguage or board
            shouldSucceed = true
            val recreateEffect = async { vm.effects.first { it is SystemSettingsEffect.RecreateActivity } }
            vm.onAction(SystemSettingsAction.RetryLanguageChange)
            testScheduler.advanceUntilIdle()
            recreateEffect.await()

            assertEquals("en", settings.interfaceLanguageCodeFlow.first())
            assertEquals("he", settings.languageCodeFlow.first())
            assertNull(vm.state.value.pendingLanguageRetryCode)
            verify(aacRepository, never()).prepareLanguage(any())
            verify(profileRepository, never()).updateActiveProfile(any())
            collectJob.cancel()
        }

    @Test
    fun `communicationViewModel and audioPreviewManager use active profile board language for TTS`() =
        runTest(testDispatcher) {
            val dsScope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = createSettingsRepository()
            settings.updateInterfaceLanguageCode("en")
            settings.updateLanguageCode("en")

            val hebrewProfile = UserProfile(
                profileId = "he_child",
                profileName = "ילד",
                activeLanguageCode = "he",
                layout = emptyMap(),
            )
            val profileRepo = mock(ProfileRepository::class.java)
            val activeProfileFlow = MutableStateFlow<UserProfile?>(hebrewProfile)
            val activeBoardLangFlow = MutableStateFlow("he")
            whenever(profileRepo.activeProfile).thenReturn(activeProfileFlow)
            whenever(profileRepo.activeBoardLanguage).thenReturn(activeBoardLangFlow)

            val observeTilesUseCase = mock(ObserveTilesUseCase::class.java)
            val hebrewTile = CombinedTile(
                definition = TileDefinition(
                    id = "water",
                    label = "מים",
                    ttsText = "מים",
                    type = TileType.BASIC,
                    languageCode = "he",
                ),
                layoutState = TileLayoutState(
                    tileId = "water",
                    parentId = null,
                    cellIndex = 0,
                ),
            )
            whenever(observeTilesUseCase(any(), any())).thenReturn(flowOf(listOf(hebrewTile)))
            val incrementUseCase = mock(IncrementTileClickUseCase::class.java)
            val audioService = mock(AudioRecordingService::class.java)
            val ttsHelper = TextToSpeechHelper(appContext)

            val commVm = CommunicationViewModel(
                application = application,
                settingsRepository = settings,
                observeTilesUseCase = observeTilesUseCase,
                incrementTileClickUseCase = incrementUseCase,
                communicationSessionController = CommunicationSessionController(),
                audioService = audioService,
                textToSpeechHelper = ttsHelper,
                profileRepository = profileRepo,
            )

            val previewTts = TextToSpeechHelper(appContext)
            val previewManager = AudioPreviewManager(
                application = application,
                settingsRepository = settings,
                scope = dsScope,
                audioService = audioService,
                textToSpeechHelper = previewTts,
                profileRepository = profileRepo,
            )

            testScheduler.advanceUntilIdle()

            // Interface is English, but board + TTS must be Hebrew (he-IL)
            assertEquals("en", settings.interfaceLanguageCodeFlow.first())
            assertEquals(Locale.forLanguageTag("he-IL"), commVm.lastRequestedTtsLocale.value)
            assertEquals(Locale.forLanguageTag("he-IL"), previewManager.lastRequestedTtsLocale.value)

            // Tap tile and speak sentence -> TTS still targets he-IL
            commVm.onAction(CommunicationAction.TileClicked(hebrewTile))
            commVm.onAction(CommunicationAction.SpeakSentenceClicked)
            testScheduler.advanceUntilIdle()
            assertEquals(Locale.forLanguageTag("he-IL"), commVm.lastRequestedTtsLocale.value)

            // Switch to English board while interface stays Hebrew
            settings.updateInterfaceLanguageCode("he")
            activeBoardLangFlow.value = "en"
            testScheduler.advanceUntilIdle()

            assertEquals(Locale.forLanguageTag("en"), commVm.lastRequestedTtsLocale.value)
            assertEquals(Locale.forLanguageTag("en"), previewManager.lastRequestedTtsLocale.value)

            dsScope.cancel()
        }
}
