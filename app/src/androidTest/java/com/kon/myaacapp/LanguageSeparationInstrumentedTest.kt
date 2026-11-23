package com.kon.myaacapp

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kon.myaacapp.core.locale.DownloadStatus
import com.kon.myaacapp.core.locale.LanguageDownloadHelper
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ExportedBoardSettings
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TilePlaybackPath
import com.kon.myaacapp.service.backup.BackupManifest
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.ui.admin.system.AdminSystemSettings
import com.kon.myaacapp.ui.admin.system.SystemSettingsAction
import com.kon.myaacapp.ui.admin.system.SystemSettingsEffect
import com.kon.myaacapp.ui.admin.system.SystemSettingsState
import com.kon.myaacapp.ui.admin.system.SystemSettingsViewModel
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.ui.communication.CommunicationViewModel
import com.kon.myaacapp.ui.communication.MainCommunicationScreen
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LanguageSeparationInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var targetContext: Context
    private lateinit var application: Application
    private lateinit var testRootDir: File
    private val createdStacks = mutableListOf<IsolatedStack>()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        application = targetContext.applicationContext as Application
        testRootDir = File(
            targetContext.cacheDir,
            "lang_separation_test_${UUID.randomUUID()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        createdStacks.forEach { it.dispose(targetContext) }
        createdStacks.clear()
        testRootDir.deleteRecursively()
    }

    private fun createStack(userTag: String): IsolatedStack {
        val runId = UUID.randomUUID().toString().take(8)
        val root = File(testRootDir, "user_${userTag}_$runId").apply { mkdirs() }
        val filesDir = File(root, "files").apply { mkdirs() }
        val dataStoreFile = File(root, "datastore/settings.preferences_pb")
        val dbName = "lang_${userTag}_$runId.db"
        val context = IsolatedAppContext(targetContext, filesDir)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dataStoreFile },
        )
        val database = Room.databaseBuilder(
            targetContext,
            AACDatabase::class.java,
            dbName,
        ).build()
        val settingsRepository = SettingsRepository(context, dataStore)
        val profileRepository = ProfileRepository(context, settingsRepository, scope)
        val aacRepository = AACRepository(
            aacTileDao = database.aacTileDao(),
            context = context,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )
        val backupService = BackupService(
            context = context,
            repository = aacRepository,
            database = database,
            settingsRepository = settingsRepository,
        )
        val backupManager = BackupManager(
            application = application,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            scope = scope,
        )
        val coordinator = AppStartupCoordinator(
            context = context,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )

        return IsolatedStack(
            filesDir = filesDir,
            dbName = dbName,
            context = context,
            scope = scope,
            database = database,
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            aacRepository = aacRepository,
            backupService = backupService,
            backupManager = backupManager,
            coordinator = coordinator,
        ).also { createdStacks += it }
    }

    @Test
    fun legacyUpgradeMigrationPreservesHebrewAndEnglishBoardsIncludingStaleProfileLanguage() =
        runBlocking {
            // 1. Legacy Hebrew install
            val hebrewStack = createStack("legacy_he")
            File(hebrewStack.filesDir, "first_boot_complete.flag").writeText("booted")
            val hebrewProfile = UserProfile(
                profileId = "he_legacy",
                profileName = "פרופיל עברי",
                activeLanguageCode = "he",
                layout = mapOf(
                    "root_he_tile" to TileLayoutState(
                        tileId = "he_tile",
                        parentId = null,
                        cellIndex = 0,
                    ),
                ),
            )
            writeJson(File(hebrewStack.filesDir, "profiles/profile_he_legacy.json"), hebrewProfile)
            writeJson(
                File(hebrewStack.filesDir, "tiles/he/default_dictionary.json"),
                listOf(
                    TileDefinition(
                        id = "he_tile",
                        label = "שלום",
                        ttsText = "שלום",
                        type = TileType.BASIC,
                        languageCode = "he",
                        defaultCellIndex = 0,
                    ),
                ),
            )
            hebrewStack.settingsRepository.updateActiveProfileId("he_legacy")
            hebrewStack.settingsRepository.updateLanguageCode("he")

            assertTrue(hebrewStack.coordinator.initialize())
            assertEquals("he", hebrewStack.settingsRepository.interfaceLanguageCodeFlow.first())
            assertEquals("he", hebrewStack.profileRepository.activeBoardLanguage.first())
            assertTrue(hebrewStack.settingsRepository.isLanguageSeparationMigratedFlow.first())

            // 2. Legacy English install with STALE activeLanguageCode = "he" on profile disk
            val englishStack = createStack("legacy_en_stale")
            File(englishStack.filesDir, "first_boot_complete.flag").writeText("booted")
            val staleProfile = UserProfile(
                profileId = "en_legacy",
                profileName = "English Board",
                activeLanguageCode = "he",
                layout = mapOf(
                    "root_en_tile" to TileLayoutState(
                        tileId = "en_tile",
                        parentId = null,
                        cellIndex = 0,
                    ),
                ),
            )
            writeJson(File(englishStack.filesDir, "profiles/profile_en_legacy.json"), staleProfile)
            writeJson(
                File(englishStack.filesDir, "tiles/en/default_dictionary.json"),
                listOf(
                    TileDefinition(
                        id = "en_tile",
                        label = "Hello",
                        ttsText = "Hello",
                        type = TileType.BASIC,
                        languageCode = "en",
                        defaultCellIndex = 0,
                    ),
                ),
            )
            englishStack.settingsRepository.updateActiveProfileId("en_legacy")
            englishStack.settingsRepository.updateLanguageCode("en")

            assertTrue(englishStack.coordinator.initialize())
            assertEquals("en", englishStack.settingsRepository.interfaceLanguageCodeFlow.first())
            assertEquals("en", englishStack.profileRepository.activeBoardLanguage.first())
            assertEquals("en", englishStack.profileRepository.activeProfile.value?.activeLanguageCode)

            val loadedEnglishTiles = englishStack.aacRepository
                .getCombinedTiles(parentId = null, langCode = "en")
                .first()
            assertEquals(listOf("Hello"), loadedEnglishTiles.map { it.label })

            // Subsequent interface switch to Hebrew leaves English board intact
            englishStack.settingsRepository.updateInterfaceLanguageCode("he")
            assertEquals("he", englishStack.settingsRepository.interfaceLanguageCodeFlow.first())
            assertEquals("en", englishStack.profileRepository.activeBoardLanguage.first())
        }

    @Test
    fun offlineInterfaceSwitchPreservesHebrewBoardLayoutRecordingsFoldersAndTtsLocale() {
        val stack = createStack("offline_he_board_en_ui")
        lateinit var commViewModel: CommunicationViewModel
        lateinit var sysViewModel: SystemSettingsViewModel

        runBlocking {
            File(stack.filesDir, "first_boot_complete.flag").writeText("booted")
            val customWavFile = File(stack.filesDir, "audio_tiles/he/custom_voice.wav").apply {
                parentFile?.mkdirs()
                writeBytes(createTestWavBytes())
            }
            val profile = UserProfile(
                profileId = "child_he",
                profileName = "לוח עברי",
                activeLanguageCode = "he",
                layout = mapOf(
                    "root_tts_tile" to TileLayoutState(
                        tileId = "tts_tile",
                        parentId = null,
                        cellIndex = 0,
                    ),
                    "root_rec_tile" to TileLayoutState(
                        tileId = "rec_tile",
                        parentId = null,
                        cellIndex = 4,
                    ),
                    "root_folder_1" to TileLayoutState(
                        tileId = "folder_1",
                        parentId = null,
                        linkedCategoryId = "folder_1",
                        cellIndex = 17,
                    ),
                    "folder_1_nested_child" to TileLayoutState(
                        tileId = "nested_child",
                        parentId = "folder_1",
                        cellIndex = 0,
                    ),
                ),
            )
            writeJson(File(stack.filesDir, "profiles/profile_child_he.json"), profile)
            writeJson(
                File(stack.filesDir, "tiles/he/default_dictionary.json"),
                listOf(
                    TileDefinition(
                        id = "tts_tile",
                        label = "מים",
                        ttsText = "מים",
                        type = TileType.BASIC,
                        languageCode = "he",
                        defaultCellIndex = 0,
                    ),
                ),
            )
            writeJson(
                File(stack.filesDir, "tiles/he/export_user_defined.json"),
                listOf(
                    TileDefinition(
                        id = "rec_tile",
                        label = "קול שלי",
                        ttsText = "קול שלי",
                        audioUri = customWavFile.absolutePath,
                        isCustomAudio = true,
                        type = TileType.BASIC,
                        languageCode = "he",
                        defaultCellIndex = 4,
                    ),
                    TileDefinition(
                        id = "folder_1",
                        label = "תיקייה",
                        ttsText = "תיקייה",
                        isCategory = true,
                        type = TileType.FOLDER,
                        languageCode = "he",
                        defaultCellIndex = 17,
                        defaultLinkedCategoryId = "folder_1",
                    ),
                    TileDefinition(
                        id = "nested_child",
                        label = "ילד פנימי",
                        ttsText = "ילד פנימי",
                        type = TileType.BASIC,
                        languageCode = "he",
                        defaultParentId = "folder_1",
                        defaultCellIndex = 0,
                    ),
                ),
            )
            stack.settingsRepository.apply {
                updateActiveProfileId("child_he")
                updateLanguageCode("he")
                updateInterfaceLanguageCode("he")
                updateGridColumns(5)
                updateGridRows(7)
                updateTilesUseTts(true)
                updateSpeakOnTilePress(true)
            }

            assertTrue(stack.coordinator.initialize())

            val audioService = AudioRecordingService(stack.context)
            commViewModel = CommunicationViewModel(
                application = application,
                settingsRepository = stack.settingsRepository,
                observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
                incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
                communicationSessionController = CommunicationSessionController(),
                audioService = audioService,
                profileRepository = stack.profileRepository,
            )
            sysViewModel = SystemSettingsViewModel(
                application = application,
                settingsRepository = stack.settingsRepository,
                aacRepository = stack.aacRepository,
                profileRepository = stack.profileRepository,
                backupService = stack.backupService,
                languageDownloadHelper = LanguageDownloadHelper(targetContext),
            )
        }

        val interfaceLangState = kotlinx.coroutines.flow.MutableStateFlow("he")

        composeRule.setContent {
            val uiLang by interfaceLangState.collectAsState()
            val wrappedContext = LocaleHelper.wrap(targetContext, uiLang)
            val uiDirection = if (uiLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            val commState by commViewModel.state.collectAsState()

            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalLayoutDirection provides uiDirection,
                ) {
                    MainCommunicationScreen(
                        state = commState,
                        onAction = commViewModel::onAction,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("מים").assertIsDisplayed()
        composeRule.onNodeWithText("קול שלי").assertIsDisplayed()
        composeRule.onNodeWithText("תיקייה").assertIsDisplayed()

        val initialTtsBounds = composeRule.onNodeWithText("מים").getBoundsInRoot()
        val initialRecBounds = composeRule.onNodeWithText("קול שלי").getBoundsInRoot()
        val initialFolderBounds = composeRule.onNodeWithText("תיקייה").getBoundsInRoot()

        // RTL grid: cell 0 ("מים") is to the RIGHT of cell 4 ("קול שלי")
        assertTrue(initialTtsBounds.left > initialRecBounds.left)

        // Switch interface language to English using real LanguageDownloadHelper (offline bundled check)
        runBlocking {
            val recreateDeferred = async {
                sysViewModel.effects.first { it is SystemSettingsEffect.RecreateActivity }
            }
            sysViewModel.onAction(SystemSettingsAction.LanguageChanged("en"))
            recreateDeferred.await()

            assertEquals("en", stack.settingsRepository.interfaceLanguageCodeFlow.first())
            assertEquals("he", stack.profileRepository.activeBoardLanguage.first())
            assertEquals(5, stack.settingsRepository.gridColumnsFlow.first())
            assertEquals(7, stack.settingsRepository.gridRowsFlow.first())
        }

        // Update UI locale & direction to English (LTR) while board remains Hebrew (RTL)
        interfaceLangState.value = "en"
        composeRule.waitForIdle()

        val afterTtsBounds = composeRule.onNodeWithText("מים").getBoundsInRoot()
        val afterRecBounds = composeRule.onNodeWithText("קול שלי").getBoundsInRoot()
        val afterFolderBounds = composeRule.onNodeWithText("תיקייה").getBoundsInRoot()

        // Same visible cell positions despite LTR interface direction
        assertEquals(initialTtsBounds.left.value, afterTtsBounds.left.value, 1f)
        assertEquals(initialRecBounds.left.value, afterRecBounds.left.value, 1f)
        assertEquals(initialFolderBounds.top.value, afterFolderBounds.top.value, 1f)

        // Tapping recorded tile still plays custom WAV even with tilesUseTts=true
        composeRule.onNodeWithText("קול שלי").performClick()
        composeRule.waitForIdle()
        val playbackEvent = commViewModel.lastPlaybackEvent.value
        assertNotNull(playbackEvent)
        assertEquals(TilePlaybackPath.AUDIO_RECORDING, playbackEvent?.path)
        assertTrue(playbackEvent?.fileExists == true)

        // Tapping Hebrew TTS tile still requests he-IL locale
        composeRule.onNodeWithText("מים").performClick()
        composeRule.waitForIdle()
        assertEquals(Locale.forLanguageTag("he-IL"), commViewModel.lastRequestedTtsLocale.value)
        assertEquals(TilePlaybackPath.TTS, commViewModel.lastPlaybackEvent.value?.path)

        // Nested folder remains intact
        composeRule.onNodeWithText("תיקייה").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ילד פנימי").assertIsDisplayed()
    }

    @Test
    fun englishBoardWithHebrewInterfaceUsesLtrBoardAndRtlHebrewSettings() {
        val stack = createStack("en_board_he_ui")
        lateinit var commViewModel: CommunicationViewModel

        runBlocking {
            File(stack.filesDir, "first_boot_complete.flag").writeText("booted")
            val profile = UserProfile(
                profileId = "en_child",
                profileName = "English Child",
                activeLanguageCode = "en",
                layout = mapOf(
                    "root_en_left" to TileLayoutState(tileId = "en_left", parentId = null, cellIndex = 0),
                    "root_en_right" to TileLayoutState(tileId = "en_right", parentId = null, cellIndex = 2),
                ),
            )
            writeJson(File(stack.filesDir, "profiles/profile_en_child.json"), profile)
            writeJson(
                File(stack.filesDir, "tiles/en/default_dictionary.json"),
                listOf(
                    TileDefinition(
                        id = "en_left",
                        label = "LeftTile",
                        ttsText = "Left",
                        type = TileType.BASIC,
                        languageCode = "en",
                        defaultCellIndex = 0,
                    ),
                    TileDefinition(
                        id = "en_right",
                        label = "RightTile",
                        ttsText = "Right",
                        type = TileType.BASIC,
                        languageCode = "en",
                        defaultCellIndex = 2,
                    ),
                ),
            )
            stack.settingsRepository.apply {
                updateActiveProfileId("en_child")
                updateLanguageCode("en")
                updateInterfaceLanguageCode("he")
                updateGridColumns(3)
                updateGridRows(5)
                updateTilesUseTts(true)
            }
            assertTrue(stack.coordinator.initialize())

            commViewModel = CommunicationViewModel(
                application = application,
                settingsRepository = stack.settingsRepository,
                observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
                incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
                communicationSessionController = CommunicationSessionController(),
                audioService = AudioRecordingService(stack.context),
                profileRepository = stack.profileRepository,
            )
        }

        val showSettings = kotlinx.coroutines.flow.MutableStateFlow(false)

        composeRule.setContent {
            val isSettings by showSettings.collectAsState()
            val hebrewContext = LocaleHelper.wrap(targetContext, "he")
            val commState by commViewModel.state.collectAsState()

            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides hebrewContext,
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    if (!isSettings) {
                        MainCommunicationScreen(
                            state = commState,
                            onAction = commViewModel::onAction,
                        )
                    } else {
                        AdminSystemSettings(
                            state = SystemSettingsState(
                                languageCode = "he",
                                languageDownloadStatus = DownloadStatus.Error("offline"),
                                pendingLanguageRetryCode = "fr",
                            ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val cell0Bounds = composeRule.onNodeWithText("LeftTile").getBoundsInRoot()
        val cell2Bounds = composeRule.onNodeWithText("RightTile").getBoundsInRoot()
        // English board renders LTR even when interface LayoutDirection is RTL: cell 0 is LEFT of cell 2
        assertTrue(cell0Bounds.left < cell2Bounds.left)

        composeRule.onNodeWithText("LeftTile").performClick()
        composeRule.waitForIdle()
        assertEquals(Locale.forLanguageTag("en"), commViewModel.lastRequestedTtsLocale.value)

        // Caregiver settings screen uses Hebrew UI resources & shows retryable error + enabled chips
        showSettings.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("הגדרות כלליות").assertIsDisplayed()
        composeRule.onNodeWithText("נסה שוב").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("אנגלית").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun restoringVersion2AndVersion3HebrewBackupsPreservesDestinationEnglishInterface() =
        runBlocking {
            val destUser = createStack("dest_en_ui")
            File(destUser.filesDir, "first_boot_complete.flag").writeText("booted")
            val destProfile = UserProfile(
                profileId = "dest_en_profile",
                profileName = "Dest English",
                activeLanguageCode = "en",
                layout = mapOf(
                    "root_old_en" to TileLayoutState(tileId = "old_en", parentId = null, cellIndex = 0),
                ),
            )
            writeJson(File(destUser.filesDir, "profiles/profile_dest_en_profile.json"), destProfile)
            writeJson(
                File(destUser.filesDir, "tiles/en/default_dictionary.json"),
                listOf(
                    TileDefinition(
                        id = "old_en",
                        label = "OldEn",
                        ttsText = "OldEn",
                        type = TileType.BASIC,
                        languageCode = "en",
                        defaultCellIndex = 0,
                    ),
                ),
            )
            destUser.settingsRepository.apply {
                updateActiveProfileId("dest_en_profile")
                updateLanguageCode("en")
                updateInterfaceLanguageCode("en")
            }
            assertTrue(destUser.coordinator.initialize())

            // Craft a version 2 archive that ONLY has languageCode="he" (boardLanguageCode=null)
            // and even a stale profile activeLanguageCode="en" inside JSON.
            val v2Archive = File(testRootDir, "v2_hebrew_backup.zip")
            val v2Manifest = BackupManifest(
                formatVersion = 2,
                exportedAtEpochMs = 12345L,
                settings = ExportedBoardSettings(
                    activeProfileId = "v2_hebrew",
                    gridColumns = 4,
                    gridRows = 6,
                    languageCode = "he",
                    boardLanguageCode = null,
                ),
            )
            val v2Profile = UserProfile(
                profileId = "v2_hebrew",
                profileName = "V2 Hebrew",
                activeLanguageCode = "en",
                layout = mapOf(
                    "root_v2_tile" to TileLayoutState(tileId = "v2_tile", parentId = null, cellIndex = 0),
                ),
            )
            val v2Tiles = listOf(
                TileDefinition(
                    id = "v2_tile",
                    label = "תפוח",
                    ttsText = "תפוח",
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 0,
                ),
            )

            ZipOutputStream(v2Archive.outputStream().buffered()).use { zos ->
                putZipTextEntry(zos, BackupService.BACKUP_MANIFEST_FILE, json.encodeToString(v2Manifest))
                putZipTextEntry(zos, "profiles/profile_v2_hebrew.json", json.encodeToString(v2Profile))
                putZipTextEntry(zos, "tiles/he/default_dictionary.json", json.encodeToString(v2Tiles))
            }

            destUser.backupManager.importDatabase(Uri.fromFile(v2Archive), targetContext.contentResolver)
            val v2Result = destUser.backupManager.result.filterNotNull().first()
            assertTrue(v2Result.isSuccess)

            // Restored board is Hebrew, destination interface stays English!
            assertEquals("en", destUser.settingsRepository.interfaceLanguageCodeFlow.first())
            assertEquals("he", destUser.profileRepository.activeBoardLanguage.first())
            assertEquals("v2_hebrew", destUser.profileRepository.activeProfile.value?.profileId)
            assertEquals("he", destUser.profileRepository.activeProfile.value?.activeLanguageCode)
            assertEquals(4, destUser.settingsRepository.gridColumnsFlow.first())
            assertEquals(6, destUser.settingsRepository.gridRowsFlow.first())

            val restoredTiles = destUser.aacRepository.getCombinedTiles(null, "he").first()
            assertEquals(listOf("תפוח"), restoredTiles.map { it.label })

            // Re-export produces v3 archive carrying boardLanguageCode="he", and restoring onto
            // another English-interface user keeps destination interface="en" and board="he".
            val v3Archive = File(testRootDir, "v3_hebrew_backup.zip")
            assertTrue(
                destUser.backupService.exportDatabase(
                    targetContext.contentResolver,
                    Uri.fromFile(v3Archive),
                ),
            )
            destUser.backupManager.clearResult()

            val secondDest = createStack("second_dest_en_ui")
            File(secondDest.filesDir, "first_boot_complete.flag").writeText("booted")
            secondDest.settingsRepository.updateInterfaceLanguageCode("en")
            secondDest.backupManager.importDatabase(Uri.fromFile(v3Archive), targetContext.contentResolver)
            val v3Result = secondDest.backupManager.result.filterNotNull().first()
            assertTrue(v3Result.isSuccess)
            assertEquals("en", secondDest.settingsRepository.interfaceLanguageCodeFlow.first())
            assertEquals("he", secondDest.profileRepository.activeBoardLanguage.first())
        }

    @Test
    fun retryButtonAppearsOnErrorAndTriggersRetryLanguageChange() {
        val clickedActions = mutableListOf<SystemSettingsAction>()

        composeRule.setContent {
            val englishContext = LocaleHelper.wrap(targetContext, "en")
            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides englishContext,
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    AdminSystemSettings(
                        state = SystemSettingsState(
                            languageCode = "en",
                            languageDownloadStatus = DownloadStatus.Error("Play Store unavailable"),
                            pendingLanguageRetryCode = "he",
                        ),
                        onAction = { clickedActions += it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assertTrue(clickedActions.contains(SystemSettingsAction.RetryLanguageChange))
    }

    private fun writeJson(file: File, value: Any) {
        file.parentFile?.mkdirs()
        val encoded = when (value) {
            is UserProfile -> json.encodeToString(value)
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                json.encodeToString(value as List<TileDefinition>)
            }
            else -> error("Unsupported type")
        }
        file.writeText(encoded)
    }

    private fun putZipTextEntry(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun createTestWavBytes(): ByteArray {
        val sampleRate = 44100
        val sampleCount = 2205
        val dataSize = sampleCount * 2
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * 2
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(intToLittleEndian(totalDataLen))
        out.write("WAVEfmt ".toByteArray())
        out.write(intToLittleEndian(16))
        out.write(byteArrayOf(1, 0, 1, 0))
        out.write(intToLittleEndian(sampleRate))
        out.write(intToLittleEndian(byteRate))
        out.write(byteArrayOf(2, 0, 16, 0))
        out.write("data".toByteArray())
        out.write(intToLittleEndian(dataSize))
        for (i in 0 until sampleCount) {
            val sample: Short = if (i % 2 == 0) 8000 else -8000
            out.write(sample.toInt() and 0xFF)
            out.write((sample.toInt() shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private class IsolatedAppContext(
        base: Context,
        private val isolatedFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = isolatedFilesDir
        override fun getApplicationContext(): Context = this
    }

    private class IsolatedStack(
        val filesDir: File,
        private val dbName: String,
        val context: Context,
        private val scope: CoroutineScope,
        private val database: AACDatabase,
        val settingsRepository: SettingsRepository,
        val profileRepository: ProfileRepository,
        val aacRepository: AACRepository,
        val backupService: BackupService,
        val backupManager: BackupManager,
        val coordinator: AppStartupCoordinator,
    ) {
        fun dispose(targetContext: Context) {
            runCatching { database.close() }
            runCatching { targetContext.deleteDatabase(dbName) }
            scope.cancel()
        }
    }
}
