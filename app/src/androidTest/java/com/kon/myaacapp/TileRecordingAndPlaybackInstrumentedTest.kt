package com.kon.myaacapp

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.local.entity.ROOT_PARENT_ID
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AACTileService
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.service.SentenceManager
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.audio.AudioPreviewManager
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.PcmAudioRecorder
import com.kon.myaacapp.service.audio.TilePlaybackPath
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.ui.communication.CommunicationAction
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.ui.communication.CommunicationViewModel
import com.kon.myaacapp.ui.editor.components.AudioSection
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class TileRecordingAndPlaybackInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var targetContext: Context
    private lateinit var application: Application
    private lateinit var testRoot: File
    private lateinit var archiveDir: File
    private val openStacks = mutableListOf<IsolatedStack>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Before
    fun setup() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        application = targetContext.applicationContext as Application
        testRoot = File(
            targetContext.cacheDir,
            "tile_recording_playback_tests_${System.nanoTime()}",
        ).apply { mkdirs() }
        archiveDir = File(testRoot, "archives").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            targetContext.packageName,
            android.Manifest.permission.RECORD_AUDIO,
        )
    }

    @After
    fun tearDown() {
        openStacks.forEach { it.dispose(targetContext) }
        openStacks.clear()
        testRoot.deleteRecursively()
    }

    @Test
    fun customAndEditedStarterRecordingsPlayWhenTilesUseTtsIsEnabledAcrossRestartAndBackupRestore() =
        runBlocking {
            val sourceStack = createStack("user_a")
            val customTileWavBytes = createPlayableWavBytes(frequencyHz = 440.0, durationMs = 200)
            val editedStarterWavBytes = createPlayableWavBytes(frequencyHz = 660.0, durationMs = 200)
            val bundledStarterWavBytes = createPlayableWavBytes(frequencyHz = 880.0, durationMs = 200)

            val customTileWavFile = File(sourceStack.filesDir, "audio_tiles/he/audio_custom_1.wav").apply {
                parentFile?.mkdirs()
                writeBytes(customTileWavBytes)
            }
            val editedStarterWavFile = File(sourceStack.filesDir, "audio_tiles/he/audio_stop_custom.wav").apply {
                parentFile?.mkdirs()
                writeBytes(editedStarterWavBytes)
            }
            File(sourceStack.filesDir, "audio_tiles/he/audio_stop_bundled.wav").apply {
                parentFile?.mkdirs()
                writeBytes(bundledStarterWavBytes)
            }
            File(sourceStack.filesDir, "audio_tiles/he/audio_water.wav").apply {
                parentFile?.mkdirs()
                writeBytes(bundledStarterWavBytes)
            }

            File(sourceStack.filesDir, "first_boot_complete.flag").writeText("booted")
            val profile = UserProfile(
                profileId = "default",
                profileName = "פרופיל בדיקה",
                activeLanguageCode = "he",
                layout = mapOf(
                    "${ROOT_PARENT_ID}_custom_1" to TileLayoutState("custom_1", null, cellIndex = 0),
                    "${ROOT_PARENT_ID}_stop" to TileLayoutState("stop", null, cellIndex = 1),
                    "${ROOT_PARENT_ID}_water" to TileLayoutState("water", null, cellIndex = 2),
                    "${ROOT_PARENT_ID}_no_audio" to TileLayoutState("no_audio", null, cellIndex = 3),
                ),
            )
            writeJson(File(sourceStack.filesDir, "profiles/profile_default.json"), profile)

            val defaultDefinitions = listOf(
                TileDefinition(
                    id = "stop",
                    label = "עצור",
                    ttsText = "עצור",
                    audioUri = "audio_tiles/he/audio_stop_bundled.wav",
                    isCustomAudio = false,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 1,
                ),
                TileDefinition(
                    id = "water",
                    label = "מים",
                    ttsText = "מים",
                    audioUri = "audio_tiles/he/audio_water.wav",
                    isCustomAudio = false,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 2,
                ),
                TileDefinition(
                    id = "no_audio",
                    label = "שלום",
                    ttsText = "שלום",
                    audioUri = null,
                    isCustomAudio = false,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 3,
                ),
            )
            writeJson(File(sourceStack.filesDir, "tiles/he/default_dictionary.json"), defaultDefinitions)

            val userDefinitions = listOf(
                TileDefinition(
                    id = "custom_1",
                    label = "אריח מוקלט",
                    ttsText = "טקסט חלופי",
                    audioUri = customTileWavFile.absolutePath,
                    isCustomAudio = true,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 0,
                ),
                // Starter tile edited by caregiver WITH custom recording
                TileDefinition(
                    id = "stop",
                    label = "עצור בבקשה",
                    ttsText = "עצור בבקשה",
                    audioUri = editedStarterWavFile.absolutePath,
                    isCustomAudio = true,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 1,
                ),
                // Starter tile edited by caregiver WITHOUT changing audio (still bundled)
                TileDefinition(
                    id = "water",
                    label = "מים קרים",
                    ttsText = "מים קרים",
                    audioUri = "audio_tiles/he/audio_water.wav",
                    isCustomAudio = false,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 2,
                ),
            )
            writeJson(File(sourceStack.filesDir, "tiles/he/export_user_defined.json"), userDefinitions)

            sourceStack.settingsRepository.apply {
                updateActiveProfileId("default")
                updateLanguageCode("he")
                updateTilesUseTts(true)
                updateSpeakOnTilePress(true)
            }
            sourceStack.profileRepository.reload()
            sourceStack.aacRepository.reload()

            // Simulate app restart on the same user storage
            sourceStack.closeWithoutDeletingDisk()
            val restartedStack = reopenStack(sourceStack)
            assertTrue(restartedStack.settingsRepository.tilesUseTtsFlow.first())

            verifyBoardPlaybackPaths(
                stack = restartedStack,
                expectedCustomBytes = customTileWavBytes,
                expectedEditedStarterBytes = editedStarterWavBytes,
            )

            // Export portable backup while tilesUseTts = true
            val exportedZip = File(archiveDir, "tts_with_recordings_backup.zip")
            restartedStack.backupManager.exportDatabase(Uri.fromFile(exportedZip), targetContext.contentResolver)
            val exportResult = restartedStack.backupManager.result.filterNotNull().first()
            assertTrue(exportResult.isSuccess)

            // Delete User A files completely before restoring to User B
            restartedStack.filesDir.deleteRecursively()
            assertFalse(customTileWavFile.exists())
            assertFalse(editedStarterWavFile.exists())

            val destStack = createStack("user_b")
            destStack.settingsRepository.updateTilesUseTts(false)
            destStack.backupManager.importDatabase(Uri.fromFile(exportedZip), targetContext.contentResolver)
            val importResult = destStack.backupManager.result.filterNotNull().first()
            assertTrue(importResult.isSuccess)
            assertTrue(importResult.missingMedia.isEmpty())
            assertTrue(destStack.settingsRepository.tilesUseTtsFlow.first())

            verifyBoardPlaybackPaths(
                stack = destStack,
                expectedCustomBytes = customTileWavBytes,
                expectedEditedStarterBytes = editedStarterWavBytes,
            )
        }

    @Test
    fun missingOrCorruptRecordingFallsBackToTtsOnBoardAndPreviewAndSentence() = runBlocking {
        val stack = createStack("fallback_user")
        val missingPath = File(stack.filesDir, "audio_tiles/he/deleted_voice.wav").absolutePath
        val corruptFile = File(stack.filesDir, "audio_tiles/he/corrupt_voice.wav").apply {
            parentFile?.mkdirs()
            writeBytes("not-a-valid-wav".toByteArray())
        }

        File(stack.filesDir, "first_boot_complete.flag").writeText("booted")
        val profile = UserProfile(
            profileId = "default",
            profileName = "ברירת מחדל",
            activeLanguageCode = "he",
            layout = mapOf(
                "${ROOT_PARENT_ID}_tile_missing" to TileLayoutState("tile_missing", null, cellIndex = 0),
                "${ROOT_PARENT_ID}_tile_corrupt" to TileLayoutState("tile_corrupt", null, cellIndex = 1),
            ),
        )
        writeJson(File(stack.filesDir, "profiles/profile_default.json"), profile)
        writeJson(File(stack.filesDir, "tiles/he/default_dictionary.json"), emptyList<TileDefinition>())
        writeJson(
            File(stack.filesDir, "tiles/he/export_user_defined.json"),
            listOf(
                TileDefinition(
                    id = "tile_missing",
                    label = "חסר",
                    ttsText = "אריח עם הקלטה חסרה",
                    audioUri = missingPath,
                    isCustomAudio = true,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 0,
                ),
                TileDefinition(
                    id = "tile_corrupt",
                    label = "פגום",
                    ttsText = "אריח עם הקלטה פגומה",
                    audioUri = corruptFile.absolutePath,
                    isCustomAudio = true,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 1,
                ),
            ),
        )

        stack.settingsRepository.updateActiveProfileId("default")
        stack.settingsRepository.updateLanguageCode("he")
        stack.settingsRepository.updateTilesUseTts(true)
        stack.settingsRepository.updateSpeakOnTilePress(true)
        stack.profileRepository.reload()
        stack.aacRepository.reload()

        val vm = CommunicationViewModel(
            application = application,
            settingsRepository = stack.settingsRepository,
            observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
            incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
            communicationSessionController = CommunicationSessionController(),
            audioService = AudioRecordingService(stack.context),
        )

        val tiles = stack.aacRepository.getCombinedTiles(null, "he").first()
        val missingTile = tiles.single { it.id == "tile_missing" }
        val corruptTile = tiles.single { it.id == "tile_corrupt" }

        vm.onAction(CommunicationAction.TileClicked(missingTile))
        val missingEvent = vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "tile_missing" }
        assertEquals(TilePlaybackPath.TTS, missingEvent.path)
        assertTrue(missingEvent.usedFallback)
        assertFalse(missingEvent.fileExists)

        vm.onAction(CommunicationAction.TileClicked(corruptTile))
        val corruptEvent = vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "tile_corrupt" }
        assertEquals(TilePlaybackPath.TTS, corruptEvent.path)
        assertTrue(corruptEvent.usedFallback)
        assertFalse(corruptEvent.fileExists)

        // Verify AudioPreviewManager reuses the same fallback path
        val previewManager = AudioPreviewManager(
            application = application,
            settingsRepository = stack.settingsRepository,
            scope = stack.scope,
            audioService = AudioRecordingService(stack.context),
        )
        previewManager.playPreview(
            ttsText = missingTile.ttsText,
            audioUri = missingTile.audioUri,
            isCustomAudio = missingTile.isCustomAudio,
            tileId = missingTile.id,
        )
        val previewEvent = previewManager.lastPlaybackEvent.filterNotNull().first { it.tileId == "tile_missing" }
        assertEquals(TilePlaybackPath.TTS, previewEvent.path)
        assertTrue(previewEvent.usedFallback)

        // Verify SentenceManager shares the same fallback behavior
        val sentenceAudio = AudioRecordingService(stack.context)
        val sentencePlayer = com.kon.myaacapp.service.audio.TileSpeechPlayer(
            audioPlayer = sentenceAudio,
            speechSynthesizer = com.kon.myaacapp.service.audio.TextToSpeechHelper(application),
        )
        val sentenceManager = SentenceManager(
            audioService = sentenceAudio,
            ttsHelper = com.kon.myaacapp.service.audio.TextToSpeechHelper(application),
            tileService = AACTileService(stack.settingsRepository, stack.scope),
            scope = stack.scope,
            tileSpeechPlayer = sentencePlayer,
            tilesUseTtsProvider = { stack.settingsRepository.tilesUseTtsFlow.first() },
        )
        sentenceManager.addTile(missingTile)
        sentenceManager.speak()
        val sentenceEvent = sentencePlayer.lastPlaybackEvent.filterNotNull().first { it.tileId == "tile_missing" }
        assertEquals(TilePlaybackPath.TTS, sentenceEvent.path)
        assertTrue(sentenceEvent.usedFallback)
    }

    @Test
    fun legacyArchiveWithoutIsCustomAudioResolvesCustomVsBundledPrecedence() = runBlocking {
        val destStack = createStack("legacy_audio_dest")
        val legacyZip = File(archiveDir, "legacy_audio_backup.zip")

        val bundledAudioBytes = createPlayableWavBytes(frequencyHz = 330.0, durationMs = 200)
        val customStarterAudioBytes = createPlayableWavBytes(frequencyHz = 550.0, durationMs = 200)
        val customTileAudioBytes = createPlayableWavBytes(frequencyHz = 770.0, durationMs = 200)

        val profile = UserProfile(
            profileId = "default",
            profileName = "ברירת מחדל",
            activeLanguageCode = "he",
            layout = mapOf(
                "${ROOT_PARENT_ID}_starter_edited_label_only" to TileLayoutState("starter_edited_label_only", null, cellIndex = 0),
                "${ROOT_PARENT_ID}_starter_edited_custom_voice" to TileLayoutState("starter_edited_custom_voice", null, cellIndex = 1),
                "${ROOT_PARENT_ID}_user_created_tile" to TileLayoutState("user_created_tile", null, cellIndex = 2),
            ),
        )
        val defaultTiles = listOf(
            TileDefinition(
                id = "starter_edited_label_only",
                label = "מים",
                ttsText = "מים",
                audioUri = "audio_tiles/he/bundled_water.wav",
                type = TileType.BASIC,
                languageCode = "he",
                defaultCellIndex = 0,
            ),
            TileDefinition(
                id = "starter_edited_custom_voice",
                label = "אוכל",
                ttsText = "אוכל",
                audioUri = "audio_tiles/he/bundled_food.wav",
                type = TileType.BASIC,
                languageCode = "he",
                defaultCellIndex = 1,
            ),
        )
        // Legacy user dictionary JSON without any "isCustomAudio" key
        val legacyUserJson = """
            [
              {
                "id": "starter_edited_label_only",
                "label": "מים צוננים",
                "ttsText": "מים צוננים",
                "audioUri": "/data/user/99/com.kon.myaacapp/files/audio_tiles/he/bundled_water.wav",
                "languageCode": "he",
                "defaultCellIndex": 0
              },
              {
                "id": "starter_edited_custom_voice",
                "label": "אוכל טעים",
                "ttsText": "אוכל טעים",
                "audioUri": "/data/user/99/com.kon.myaacapp/files/audio_tiles/he/custom_food.wav",
                "languageCode": "he",
                "defaultCellIndex": 1
              },
              {
                "id": "user_created_tile",
                "label": "נדנדה",
                "ttsText": "אני רוצה נדנדה",
                "audioUri": "audio_tiles/he/custom_swing.wav",
                "languageCode": "he",
                "defaultCellIndex": 2
              }
            ]
        """.trimIndent()

        ZipOutputStream(legacyZip.outputStream().buffered()).use { zos ->
            putZipTextEntry(zos, "profiles/profile_default.json", json.encodeToString(profile))
            putZipTextEntry(zos, "tiles/he/default_dictionary.json", json.encodeToString(defaultTiles))
            putZipTextEntry(zos, "tiles/he/export_user_defined.json", legacyUserJson)
            putZipBytesEntry(zos, "audio_tiles/he/bundled_water.wav", bundledAudioBytes)
            putZipBytesEntry(zos, "audio_tiles/he/bundled_food.wav", bundledAudioBytes)
            putZipBytesEntry(zos, "audio_tiles/he/custom_food.wav", customStarterAudioBytes)
            putZipBytesEntry(zos, "audio_tiles/he/custom_swing.wav", customTileAudioBytes)
        }

        destStack.settingsRepository.updateTilesUseTts(true)
        destStack.settingsRepository.updateLanguageCode("he")
        destStack.backupManager.importDatabase(Uri.fromFile(legacyZip), targetContext.contentResolver)
        assertTrue(destStack.backupManager.result.filterNotNull().first().isSuccess)

        val restoredTiles = destStack.aacRepository.getCombinedTiles(null, "he").first()
        val labelOnlyStarter = restoredTiles.single { it.id == "starter_edited_label_only" }
        val customStarter = restoredTiles.single { it.id == "starter_edited_custom_voice" }
        val userCreated = restoredTiles.single { it.id == "user_created_tile" }

        assertFalse(labelOnlyStarter.isCustomAudio)
        assertTrue(customStarter.isCustomAudio)
        assertTrue(userCreated.isCustomAudio)

        val vm = CommunicationViewModel(
            application = application,
            settingsRepository = destStack.settingsRepository,
            observeTilesUseCase = ObserveTilesUseCase(destStack.aacRepository),
            incrementTileClickUseCase = IncrementTileClickUseCase(destStack.aacRepository),
            communicationSessionController = CommunicationSessionController(),
            audioService = AudioRecordingService(destStack.context),
        )

        vm.onAction(CommunicationAction.TileClicked(labelOnlyStarter))
        assertEquals(
            TilePlaybackPath.TTS,
            vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "starter_edited_label_only" }.path,
        )

        vm.onAction(CommunicationAction.TileClicked(customStarter))
        assertEquals(
            TilePlaybackPath.AUDIO_RECORDING,
            vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "starter_edited_custom_voice" }.path,
        )

        vm.onAction(CommunicationAction.TileClicked(userCreated))
        assertEquals(
            TilePlaybackPath.AUDIO_RECORDING,
            vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "user_created_tile" }.path,
        )
    }

    @Test
    fun audioSectionSilentFirstAttemptLeavesNoReferenceAndSilentReplacementKeepsPreviousRecording() {
        val stack = createStack("ui_audio_section")
        var nextCaptureIsAudible = false
        val audioService = AudioRecordingService(stack.context) {
            object : PcmAudioRecorder {
                var emitted = false
                override fun initialize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int = 1024
                override fun start(): Boolean = true
                override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
                    if (!emitted) {
                        emitted = true
                        for (i in 0 until size) {
                            buffer[offset + i] = if (nextCaptureIsAudible) {
                                if (i % 2 == 0) 7000 else -7000
                            } else {
                                0
                            }
                        }
                        return size
                    }
                    Thread.sleep(5)
                    return 0
                }
                override fun stopAndRelease() = Unit
            }
        }

        var currentAudioUri by mutableStateOf<String?>(null)
        val uriHistory = mutableListOf<String?>()

        composeRule.setContent {
            MyAACAppTheme {
                AudioSection(
                    audioUri = currentAudioUri,
                    onAudioUriChange = { updated ->
                        uriHistory += updated
                        currentAudioUri = updated
                    },
                    languageCode = "he",
                    audioService = audioService,
                )
            }
        }

        val recordLabel = targetContext.getString(R.string.custom_voice_recording)
        val testAudioLabel = targetContext.getString(R.string.test_audio)
        val silentError = targetContext.getString(R.string.recording_silent_error)
        val silentKept = targetContext.getString(R.string.recording_silent_kept_previous)

        // 1. Silent first attempt
        nextCaptureIsAudible = false
        composeRule.onNodeWithText(recordLabel).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("(", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(androidx.compose.ui.test.hasText("(", substring = true)).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(silentError))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(silentError).assertIsDisplayed()
        assertNull(currentAudioUri)
        assertTrue(uriHistory.isEmpty())
        composeRule.onNodeWithText(testAudioLabel).assertDoesNotExist()

        // 2. Audible recording succeeds
        nextCaptureIsAudible = true
        composeRule.onNodeWithText(recordLabel).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("(", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(androidx.compose.ui.test.hasText("(", substring = true)).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            currentAudioUri != null
        }
        val workingPath = currentAudioUri!!
        assertTrue(File(workingPath).isFile)
        assertTrue(audioService.canPlayAudio(workingPath))
        composeRule.onNodeWithText(testAudioLabel).assertIsDisplayed()

        // 3. Silent replacement attempt keeps prior working recording
        nextCaptureIsAudible = false
        composeRule.onNodeWithText(recordLabel).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("(", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(androidx.compose.ui.test.hasText("(", substring = true)).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(silentKept))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(silentKept).assertIsDisplayed()
        assertEquals(workingPath, currentAudioUri)
        assertTrue(File(workingPath).isFile)
        assertTrue(audioService.canPlayAudio(workingPath))
        composeRule.onNodeWithText(testAudioLabel).assertIsDisplayed()
    }

    private suspend fun verifyBoardPlaybackPaths(
        stack: IsolatedStack,
        expectedCustomBytes: ByteArray,
        expectedEditedStarterBytes: ByteArray,
    ) {
        val tiles = stack.aacRepository.getCombinedTiles(null, "he").first()
        assertEquals(
            "Expected 4 tiles in ${stack.filesDir.parentFile?.name} (active=${stack.profileRepository.activeProfile.value?.profileId}), got ${tiles.map { "${it.id}@${it.cellIndex}" }}",
            4,
            tiles.size,
        )
        val customTile = tiles.single { it.id == "custom_1" }
        val editedStarterCustom = tiles.single { it.id == "stop" }
        val editedStarterBundled = tiles.single { it.id == "water" }
        val noAudioTile = tiles.single { it.id == "no_audio" }

        assertTrue(customTile.isCustomAudio)
        assertTrue(editedStarterCustom.isCustomAudio)
        assertFalse(editedStarterBundled.isCustomAudio)
        assertFalse(noAudioTile.isCustomAudio)

        val customFile = File(customTile.audioUri!!)
        val editedStarterFile = File(editedStarterCustom.audioUri!!)
        assertTrue("Custom WAV missing at ${customFile.absolutePath}", customFile.isFile)
        assertTrue("Edited starter WAV missing at ${editedStarterFile.absolutePath}", editedStarterFile.isFile)
        assertTrue(customFile.absolutePath.startsWith(stack.filesDir.absolutePath))
        assertTrue(editedStarterFile.absolutePath.startsWith(stack.filesDir.absolutePath))
        assertArrayEquals(expectedCustomBytes, customFile.readBytes())
        assertArrayEquals(expectedEditedStarterBytes, editedStarterFile.readBytes())

        val vm = CommunicationViewModel(
            application = application,
            settingsRepository = stack.settingsRepository,
            observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
            incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
            communicationSessionController = CommunicationSessionController(),
            audioService = AudioRecordingService(stack.context),
        )

        vm.onAction(CommunicationAction.TileClicked(customTile))
        val customEvent = vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "custom_1" }
        assertEquals(TilePlaybackPath.AUDIO_RECORDING, customEvent.path)
        assertTrue(customEvent.fileExists)
        assertFalse(customEvent.usedFallback)

        vm.onAction(CommunicationAction.TileClicked(editedStarterCustom))
        val starterCustomEvent = vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "stop" }
        assertEquals(TilePlaybackPath.AUDIO_RECORDING, starterCustomEvent.path)
        assertTrue(starterCustomEvent.fileExists)
        assertFalse(starterCustomEvent.usedFallback)

        vm.onAction(CommunicationAction.TileClicked(editedStarterBundled))
        val starterBundledEvent = vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "water" }
        assertEquals(TilePlaybackPath.TTS, starterBundledEvent.path)
        assertFalse(starterBundledEvent.usedFallback)

        vm.onAction(CommunicationAction.TileClicked(noAudioTile))
        val noAudioEvent = vm.lastPlaybackEvent.filterNotNull().first { it.tileId == "no_audio" }
        assertEquals(TilePlaybackPath.TTS, noAudioEvent.path)
        assertFalse(noAudioEvent.usedFallback)
    }

    private fun createPlayableWavBytes(frequencyHz: Double, durationMs: Int): ByteArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs) / 1000
        val totalAudioLen = (numSamples * 2).toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * 2).toLong()

        val out = ByteArrayOutputStream(44 + totalAudioLen.toInt())
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte(); header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte(); header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
        header[24] = (sampleRate.toLong() and 0xffL).toByte(); header[25] = (sampleRate.toLong() shr 8 and 0xffL).toByte()
        header[26] = (sampleRate.toLong() shr 16 and 0xffL).toByte(); header[27] = (sampleRate.toLong() shr 24 and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte(); header[29] = (byteRate shr 8 and 0xffL).toByte()
        header[30] = (byteRate shr 16 and 0xffL).toByte(); header[31] = (byteRate shr 24 and 0xffL).toByte()
        header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte(); header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte(); header[43] = (totalAudioLen shr 24 and 0xffL).toByte()
        out.write(header)

        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i * frequencyHz / sampleRate
            val sample = (sin(angle) * 16000.0).toInt().toShort().toInt()
            out.write(sample and 0xFF)
            out.write((sample shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    private fun createStack(name: String): IsolatedStack {
        val stackRoot = File(testRoot, name).apply { mkdirs() }
        val filesDir = File(stackRoot, "files").apply { mkdirs() }
        val dataStoreFile = File(stackRoot, "settings.preferences_pb")
        val dbName = "audio_test_${name}_${System.nanoTime()}.db"
        return buildStack(filesDir, dataStoreFile, dbName)
    }

    private fun reopenStack(previous: IsolatedStack): IsolatedStack {
        openStacks.remove(previous)
        return buildStack(previous.filesDir, previous.dataStoreFile, previous.dbName)
    }

    private fun buildStack(
        filesDir: File,
        dataStoreFile: File,
        dbName: String,
    ): IsolatedStack {
        val context = IsolatedAppContext(targetContext, filesDir)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dataStoreFile },
        )
        val database = Room.databaseBuilder(targetContext, AACDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val settingsRepository = SettingsRepository(context, dataStore)
        val profileRepository = ProfileRepository(context, settingsRepository, scope)
        val aacRepository = AACRepository(database.aacTileDao(), context, profileRepository, settingsRepository)
        val backupService = BackupService(context, aacRepository, database, settingsRepository)
        val backupManager = BackupManager(application, backupService, aacRepository, profileRepository, scope)

        runBlocking {
            profileRepository.reload()
            aacRepository.reload()
        }

        return IsolatedStack(
            filesDir = filesDir,
            dataStoreFile = dataStoreFile,
            dbName = dbName,
            context = context,
            scope = scope,
            dataStore = dataStore,
            database = database,
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            aacRepository = aacRepository,
            backupService = backupService,
            backupManager = backupManager,
        ).also { openStacks += it }
    }

    private inline fun <reified T> writeJson(file: File, value: T) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(value))
    }

    private fun putZipTextEntry(zos: ZipOutputStream, name: String, content: String) {
        putZipBytesEntry(zos, name, content.toByteArray(Charsets.UTF_8))
    }

    private fun putZipBytesEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private data class IsolatedStack(
        val filesDir: File,
        val dataStoreFile: File,
        val dbName: String,
        val context: Context,
        val scope: CoroutineScope,
        val dataStore: DataStore<Preferences>,
        val database: AACDatabase,
        val settingsRepository: SettingsRepository,
        val profileRepository: ProfileRepository,
        val aacRepository: AACRepository,
        val backupService: BackupService,
        val backupManager: BackupManager,
    ) {
        fun closeWithoutDeletingDisk() {
            runCatching { scope.cancel() }
            runCatching { database.close() }
        }

        fun dispose(baseContext: Context) {
            closeWithoutDeletingDisk()
            runCatching { baseContext.deleteDatabase(dbName) }
        }
    }

    private class IsolatedAppContext(
        base: Context,
        private val customFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = customFilesDir
        override fun getApplicationContext(): Context = this
    }
}
