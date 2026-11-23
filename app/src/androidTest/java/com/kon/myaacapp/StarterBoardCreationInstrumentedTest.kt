package com.kon.myaacapp

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.ui.communication.CommunicationViewModel
import com.kon.myaacapp.ui.communication.MainCommunicationScreen
import com.kon.myaacapp.ui.profile.ProfileManagerScreen
import com.kon.myaacapp.ui.profile.ProfileManagerViewModel
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

@RunWith(AndroidJUnit4::class)
class StarterBoardCreationInstrumentedTest {

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
            "starter_board_test_${UUID.randomUUID()}",
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
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val dataStoreFile = File(root, "datastore/settings.preferences_pb")
        val dbName = "starter_${userTag}_$runId.db"
        val context = IsolatedAppContext(targetContext, filesDir, cacheDir)

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
    fun createEnglishAndHebrewStarterBoardsFromUiWithOppositeInterfaceAndTtsLocales() {
        val stack = createStack("ui_starter_creation")
        lateinit var profileVm: ProfileManagerViewModel
        lateinit var commVm: CommunicationViewModel

        runBlocking {
            assertTrue(stack.coordinator.initialize())

            stack.settingsRepository.updateInterfaceLanguageCode("he")
            stack.settingsRepository.updateTilesUseTts(true)
            stack.settingsRepository.updateSpeakOnTilePress(true)

            profileVm = ProfileManagerViewModel(
                profileRepository = stack.profileRepository,
                aacRepository = stack.aacRepository,
            )
            commVm = CommunicationViewModel(
                application = application,
                settingsRepository = stack.settingsRepository,
                observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
                incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
                communicationSessionController = CommunicationSessionController(),
                audioService = AudioRecordingService(stack.context),
                profileRepository = stack.profileRepository,
            )
        }

        val uiLangState = MutableStateFlow("he")
        val showBoardState = MutableStateFlow(true)

        composeRule.setContent {
            val uiLang by uiLangState.collectAsState()
            val showBoard by showBoardState.collectAsState()
            val wrappedContext = LocaleHelper.wrap(targetContext, uiLang)
            val uiDirection = if (uiLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            val profileState by profileVm.state.collectAsState()
            val commState by commVm.state.collectAsState()

            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalResources provides wrappedContext.resources,
                    LocalConfiguration provides wrappedContext.resources.configuration,
                    LocalLayoutDirection provides uiDirection,
                ) {
                    if (!showBoard) {
                        ProfileManagerScreen(
                            state = profileState,
                            onAction = profileVm::onAction,
                            onNavigateBack = {},
                        )
                    } else {
                        MainCommunicationScreen(
                            state = commState,
                            onAction = commVm::onAction,
                        )
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("זמן ושגרה").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("זמן ושגרה").assertIsDisplayed()
        composeRule.onNodeWithText("Time and routines").assertDoesNotExist()

        runBlocking {
            // Customize the editable default so we can prove Starter ignores its edits.
            val editedDefault = stack.profileRepository.activeProfile.value!!.copy(
                layout = mapOf(
                    "root_yes" to TileLayoutState(
                        tileId = "yes",
                        parentId = null,
                        cellIndex = 0,
                        isHidden = true,
                        clickCount = 19,
                    ),
                ),
            )
            stack.profileRepository.updateActiveProfile(editedDefault)
        }
        showBoardState.value = false
        composeRule.waitForIdle()
        val heContext = LocaleHelper.wrap(targetContext, "he")

        // 1. In Hebrew UI, create an English Starter profile
        composeRule.onNodeWithContentDescription(heContext.getString(R.string.create_new_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(heContext.getString(R.string.enter_profile_name))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNode(hasSetTextAction())
            .performTextInput("Alex EN")
        composeRule.onNodeWithText(heContext.getString(R.string.profile_mode_default))
            .performScrollTo()
            .performClick()
        composeRule.onNode(
            hasText(heContext.getString(R.string.english)) and
                hasClickAction() and
                hasAnyAncestor(isDialog()),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(heContext.getString(R.string.add))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            stack.profileRepository.activeProfile.value?.profileName == "Alex EN"
        }

        runBlocking {
            val active = stack.profileRepository.activeProfile.value
            assertNotNull(active)
            assertEquals("Alex EN", active!!.profileName)
            assertEquals("en", active.activeLanguageCode)
            assertEquals(590, active.layout.size)
            assertEquals("he", stack.settingsRepository.interfaceLanguageCodeFlow.first())
        }

        // Navigate the newly created English Starter board while UI remains Hebrew
        showBoardState.value = true
        waitAndClickDisplayedText("Time and routines")
        waitAndClickDisplayedText("Morning routine")
        waitAndClickDisplayedText("Wash face")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            commVm.lastRequestedTtsLocale.value == Locale.forLanguageTag("en")
        }

        // 2. Switch UI to English, create a Hebrew Starter profile
        showBoardState.value = false
        uiLangState.value = "en"
        composeRule.waitForIdle()
        val enContext = LocaleHelper.wrap(targetContext, "en")

        composeRule.onNodeWithContentDescription(enContext.getString(R.string.create_new_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(enContext.getString(R.string.enter_profile_name))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction())
            .performTextInput("Dana HE")
        composeRule.onNodeWithText(enContext.getString(R.string.profile_mode_default))
            .performScrollTo()
            .performClick()
        composeRule.onNode(
            hasText(enContext.getString(R.string.hebrew)) and
                hasClickAction() and
                hasAnyAncestor(isDialog()),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(enContext.getString(R.string.add))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            stack.profileRepository.activeProfile.value?.profileName == "Dana HE"
        }

        runBlocking {
            val active = stack.profileRepository.activeProfile.value
            assertNotNull(active)
            assertEquals("Dana HE", active!!.profileName)
            assertEquals("he", active.activeLanguageCode)
            assertEquals(590, active.layout.size)
        }

        showBoardState.value = true
        waitAndClickDisplayedText("זמן ושגרה")
        waitAndClickDisplayedText("התארגנות בוקר")
        waitAndClickDisplayedText("לשטוף פנים")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            commVm.lastRequestedTtsLocale.value == Locale.forLanguageTag("he-IL")
        }
    }

    @Test
    fun misplacedStarterDefinitionsCannotOverrideAnotherBoardLanguage() = runBlocking {
        val stack = createStack("misplaced_starter_definitions")
        assertTrue(stack.coordinator.initialize())

        val englishFile = File(stack.filesDir, "tiles/en/default_dictionary.json")
        val englishDefinitions = json.decodeFromString<List<TileDefinition>>(englishFile.readText())
        val englishFolder = englishDefinitions.single { it.id == "time_routines_category" }
        val misplacedFolder = englishFolder.copy(languageCode = "he")
        writeJson(
            englishFile,
            englishDefinitions + listOf(misplacedFolder, misplacedFolder.copy(id = "misplaced_folder")),
        )

        stack.aacRepository.reload()

        val definitions = stack.aacRepository.baseDefinitions.value
        val hebrewFolder = definitions.single {
            it.id == englishFolder.id && it.languageCode == "he"
        }
        assertEquals("זמן ושגרה", hebrewFolder.label)
        assertEquals("זמן ושגרה", hebrewFolder.ttsText)
        assertTrue(englishFolder in definitions)
        assertFalse(definitions.any { it.id == "misplaced_folder" })
    }

    private fun waitAndClickDisplayedText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText(text).performClick()
    }

    @Test
    fun upgradeFromOldStarterArchivePreservesCustomizationsRecordingsAndMultiProfileLanguages() =
        runBlocking {
            val stack = createStack("upgrade_from_old")
            assertTrue(stack.backupService.importFromAssets("initial_data_old.zip"))
            File(stack.filesDir, "first_boot_complete.flag").writeText("booted")
            // Simulate pre-PR5 install that has no starter_data_version.txt
            File(stack.filesDir, "starter_data_version.txt").delete()

            val customWav = File(stack.filesDir, "audio_tiles/he/user_custom_1.wav").apply {
                parentFile?.mkdirs()
                writeBytes(createTestWavBytes())
            }
            val customizedDefault = UserProfile(
                profileId = "default",
                profileName = "פרופיל ברירת מחדל",
                activeLanguageCode = "he",
                layout = mapOf(
                    "root_water" to TileLayoutState(
                        tileId = "water",
                        parentId = null,
                        cellIndex = 2,
                    ),
                ),
            )
            val siblingEnglishProfile = UserProfile(
                profileId = "sibling_en",
                profileName = "Sibling English",
                activeLanguageCode = "en",
                layout = mapOf(
                    "root_yes" to TileLayoutState(
                        tileId = "yes",
                        parentId = null,
                        cellIndex = 0,
                    ),
                ),
            )
            writeJson(File(stack.filesDir, "profiles/profile_default.json"), customizedDefault)
            writeJson(File(stack.filesDir, "profiles/profile_sibling_en.json"), siblingEnglishProfile)

            val customTiles = listOf(
                TileDefinition(
                    id = "water",
                    label = "המים שלי",
                    ttsText = "תן לי מים",
                    audioUri = customWav.absolutePath,
                    isCustomAudio = true,
                    type = TileType.BASIC,
                    languageCode = "he",
                ),
            )
            writeJson(File(stack.filesDir, "tiles/he/export_user_defined.json"), customTiles)

            stack.settingsRepository.updateActiveProfileId("default")
            stack.settingsRepository.updateLanguageCode("he")

            // Upgrade initialization against current shipped initial_data.zip
            assertTrue(stack.coordinator.initialize())

            val profilesById = stack.profileRepository.profiles.value.associateBy { it.profileId }
            assertEquals("he", profilesById.getValue("default").activeLanguageCode)
            assertEquals("en", profilesById.getValue("sibling_en").activeLanguageCode)
            assertEquals(customizedDefault.layout, profilesById.getValue("default").layout)

            val preservedWater = stack.aacRepository.baseDefinitions.value
                .first { it.languageCode == "he" && it.id == "water" }
            assertEquals("המים שלי", preservedWater.label)
            assertEquals(customWav.absolutePath, preservedWater.audioUri)
            assertTrue(preservedWater.isCustomAudio)
            assertArrayEquals(createTestWavBytes(), customWav.readBytes())

            // Creating a new English Starter profile after upgrade includes the 9 added routine IDs
            stack.profileRepository.createProfile(
                name = "Upgraded EN Starter",
                creationMode = ProfileCreationMode.DEFAULT_TEMPLATE,
                boardLanguageCode = "en",
            )
            stack.aacRepository.reload()

            val newStarterEn = stack.profileRepository.activeProfile.value!!
            assertEquals("en", newStarterEn.activeLanguageCode)
            assertEquals(590, newStarterEn.layout.size)

            val morningTiles = stack.aacRepository
                .getCombinedTiles(parentId = "morning_routine_category", langCode = "en")
                .first()
            assertTrue(
                morningTiles.any {
                    it.definition.id == "wash_face" &&
                        it.label == "Wash face" &&
                        it.ttsText == "Wash my face"
                },
            )
            assertTrue(
                morningTiles.any {
                    it.definition.id == "change_diaper" &&
                        it.label == "Change diaper" &&
                        it.ttsText == "I need a diaper change"
                },
            )
            assertTrue(
                morningTiles.any {
                    it.definition.id == "leave_for_kindergarten" &&
                        it.label == "Time for school" &&
                        it.ttsText == "Time for school"
                },
            )
        }

    @Test
    fun backupAndRestoreOfNewlyCreatedEnglishStarterPreservesBoardAndKeepsDestinationHebrewUi() =
        runBlocking {
            val sourceStack = createStack("export_en_starter")
            val destStack = createStack("restore_onto_he_ui")

            assertTrue(sourceStack.coordinator.initialize())
            sourceStack.settingsRepository.updateInterfaceLanguageCode("en")
            sourceStack.profileRepository.createProfile(
                name = "Maya EN",
                creationMode = ProfileCreationMode.DEFAULT_TEMPLATE,
                boardLanguageCode = "en",
            )
            sourceStack.aacRepository.reload()
            val createdEn = sourceStack.profileRepository.activeProfile.value!!
            assertEquals("en", createdEn.activeLanguageCode)

            val archiveFile = File(testRootDir, "en_starter_backup.zip")
            sourceStack.backupManager.exportDatabase(
                uri = Uri.fromFile(archiveFile),
                contentResolver = targetContext.contentResolver,
            )
            val exportResult = sourceStack.backupManager.result.filterNotNull().first()
            assertTrue(exportResult.isSuccess)

            // Destination uses Hebrew UI
            assertTrue(destStack.coordinator.initialize())
            destStack.settingsRepository.updateInterfaceLanguageCode("he")

            destStack.backupManager.importDatabase(
                uri = Uri.fromFile(archiveFile),
                contentResolver = targetContext.contentResolver,
            )
            val importResult = destStack.backupManager.result.filterNotNull().first()
            assertTrue(importResult.isSuccess)

            val restoredProfile = destStack.profileRepository.activeProfile.value!!
            assertEquals("Maya EN", restoredProfile.profileName)
            assertEquals("en", restoredProfile.activeLanguageCode)
            assertEquals(590, restoredProfile.layout.size)
            assertEquals("he", destStack.settingsRepository.interfaceLanguageCodeFlow.first())

            val enMorning = destStack.aacRepository
                .getCombinedTiles(parentId = "morning_routine_category", langCode = "en")
                .first()
            assertTrue(enMorning.any { it.label == "Wash face" })
            assertFalse(enMorning.isEmpty())
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
        private val isolatedCacheDir: File,
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = isolatedFilesDir
        override fun getCacheDir(): File = isolatedCacheDir
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
