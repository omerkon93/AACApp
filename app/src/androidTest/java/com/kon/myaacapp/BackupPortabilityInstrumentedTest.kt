package com.kon.myaacapp

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.data.local.entity.ROOT_PARENT_ID
import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.data.local.entity.TilePlacement
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.service.Gender
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.backup.BackupManifest
import com.kon.myaacapp.service.backup.BackupMediaType
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.service.backup.RestoreApplyStep
import com.kon.myaacapp.service.image.ImageStorageService
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.ui.communication.CommunicationViewModel
import com.kon.myaacapp.ui.communication.MainCommunicationScreen
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupPortabilityInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var targetContext: Context
    private lateinit var application: Application
    private lateinit var testRootDir: File
    private lateinit var archiveDir: File
    private val createdStacks = mutableListOf<IsolatedUserStack>()

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
            "backup_portability_test_${UUID.randomUUID()}",
        ).apply { mkdirs() }
        archiveDir = File(testRootDir, "archives").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        createdStacks.forEach { it.dispose(targetContext) }
        createdStacks.clear()
        testRootDir.deleteRecursively()
    }

    private fun createUserStack(
        userTag: String,
        reuseFilesDir: File? = null,
        reuseDataStoreFile: File? = null,
        reuseDbName: String? = null,
    ): IsolatedUserStack {
        val runId = UUID.randomUUID().toString().take(8)
        val userDir = File(testRootDir, "user_${userTag}_$runId").apply { mkdirs() }
        val filesDir = reuseFilesDir ?: File(userDir, "files").apply { mkdirs() }
        val dataStoreFile = reuseDataStoreFile ?: File(userDir, "datastore/settings.preferences_pb")
        val dbName = reuseDbName ?: "portability_${userTag}_$runId.db"
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

        return IsolatedUserStack(
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
        ).also { createdStacks += it }
    }

    @Test
    fun crossUserExportImportOpensProfileWith5x7GridVisibleCellBeyond14RenderedImageAndDestinationAudio() {
        val destUser = runBlocking {
            val sourceUser = createUserStack("source")
            val destUser = createUserStack("dest")

            val sourceImageBytes = createTestPngBytes(Color.BLUE)
            val sourceAudioBytes = createTestWavBytes(seedByte = 0x2A)
            val sourceImageFile = File(sourceUser.filesDir, "images/custom_20.png").apply {
                parentFile?.mkdirs()
                writeBytes(sourceImageBytes)
            }
            val sourceAudioFile = File(sourceUser.filesDir, "audio_tiles/he/audio_cell_20.wav").apply {
                parentFile?.mkdirs()
                writeBytes(sourceAudioBytes)
            }

            File(sourceUser.filesDir, "first_boot_complete.flag").writeText("booted")
            val sourceProfile = UserProfile(
                profileId = "hebrew_5x7",
                profileName = "פרופיל 5x7",
                activeLanguageCode = "he",
                layout = mapOf(
                    "tile_root_0" to TileLayoutState(tileId = "tile_root_0", parentId = null, cellIndex = 0),
                    "tile_cell_20" to TileLayoutState(tileId = "tile_cell_20", parentId = null, cellIndex = 20),
                ),
            )
            writeJson(File(sourceUser.filesDir, "profiles/profile_hebrew_5x7.json"), sourceProfile)

            val sourceDefinitions = listOf(
                TileDefinition(
                    id = "tile_root_0",
                    label = "שלום",
                    ttsText = "שלום",
                    emoji = "👋",
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 0,
                ),
                TileDefinition(
                    id = "tile_cell_20",
                    label = "אריח עשרים",
                    ttsText = "אני בתא עשרים",
                    imageUri = sourceImageFile.absolutePath,
                    audioUri = sourceAudioFile.absolutePath,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 20,
                ),
            )
            writeJson(
                File(sourceUser.filesDir, "tiles/he/default_dictionary.json"),
                emptyList<TileDefinition>(),
            )
            writeJson(
                File(sourceUser.filesDir, "tiles/he/export_user_defined.json"),
                sourceDefinitions,
            )

            // Configure all allowlisted settings to distinct non-factory values
            sourceUser.settingsRepository.apply {
                updateActiveProfileId("hebrew_5x7")
                updateGridColumns(5)
                updateGridRows(7)
                updateGridTileScale(1.25f)
                updateGridTileContainerScale(0.85f)
                updateBarTileImageScale(1.3f)
                updateBarTileTitleScale(1.15f)
                updateActionButtonScale(1.4f)
                updateShowSentenceBar(true)
                updateShowBackButton(false)
                updateShowBackspaceButton(false)
                updateShowSpeakButton(false)
                updateHomeInActionBar(false)
                updateSpeakOnTilePress(false)
                updateTilesUseTts(false)
                updateUserGender(Gender.FEMALE)
                updateLanguageCode("he")
            }
            sourceUser.profileRepository.reload()
            sourceUser.aacRepository.reload()

            // Export from source user
            val exportedZip = File(archiveDir, "portable_export.zip")
            sourceUser.backupManager.exportDatabase(Uri.fromFile(exportedZip), targetContext.contentResolver)
            val exportResult = sourceUser.backupManager.result.filterNotNull().first()
            assertTrue(exportResult.isSuccess)
            assertTrue(exportedZip.length() > 0L)

            // Verify format version, relative media paths, and allowlisted settings in archive
            ZipFile(exportedZip).use { zip ->
                val manifestEntry = zip.getEntry(BackupService.BACKUP_MANIFEST_FILE)
                assertNotNull(manifestEntry)
                val manifest = json.decodeFromString<BackupManifest>(
                    zip.getInputStream(manifestEntry).bufferedReader().readText(),
                )
                assertEquals(BackupService.CURRENT_BACKUP_FORMAT_VERSION, manifest.formatVersion)
                assertEquals("hebrew_5x7", manifest.settings?.activeProfileId)
                assertEquals(5, manifest.settings?.gridColumns)
                assertEquals(7, manifest.settings?.gridRows)
                assertEquals(1.25f, manifest.settings?.gridTileScale ?: 0f, 0.001f)
                assertEquals(0.85f, manifest.settings?.gridTileContainerScale ?: 0f, 0.001f)
                assertEquals(1.3f, manifest.settings?.barTileImageScale ?: 0f, 0.001f)
                assertEquals(1.15f, manifest.settings?.barTileTitleScale ?: 0f, 0.001f)
                assertEquals(1.4f, manifest.settings?.actionButtonScale ?: 0f, 0.001f)
                assertEquals(true, manifest.settings?.showSentenceBar)
                assertEquals(false, manifest.settings?.showBackButton)
                assertEquals(false, manifest.settings?.showBackspaceButton)
                assertEquals(false, manifest.settings?.showSpeakButton)
                assertEquals(false, manifest.settings?.homeInActionBar)
                assertEquals(false, manifest.settings?.speakOnTilePress)
                assertEquals(false, manifest.settings?.tilesUseTts)
                assertEquals(Gender.FEMALE.name, manifest.settings?.userGender)
                assertEquals("he", manifest.settings?.languageCode)

                val exportedTilesEntry = zip.getEntry("tiles/he/export_user_defined.json")
                assertNotNull(exportedTilesEntry)
                val exportedTiles = json.decodeFromString<List<TileDefinition>>(
                    zip.getInputStream(exportedTilesEntry).bufferedReader().readText(),
                )
                val exportedCell20 = exportedTiles.single { it.id == "tile_cell_20" }
                assertEquals("images/custom_20.png", exportedCell20.imageUri)
                assertEquals("audio_tiles/he/audio_cell_20.wav", exportedCell20.audioUri)
                assertNotNull(zip.getEntry("images/custom_20.png"))
                assertNotNull(zip.getEntry("audio_tiles/he/audio_cell_20.wav"))
            }

            // Delete source user's files completely to verify zero cross-user disk dependency
            sourceUser.filesDir.deleteRecursively()
            assertFalse(sourceImageFile.exists())
            assertFalse(sourceAudioFile.exists())

            // Seed destination user with contrasting defaults (English, 3x5, default profile)
            seedDefaultDestinationUser(destUser)

            // Import into destination user
            destUser.backupManager.importDatabase(Uri.fromFile(exportedZip), targetContext.contentResolver)
            val importResult = destUser.backupManager.result.filterNotNull().first()
            assertTrue(importResult.isSuccess)
            assertTrue(importResult.missingMedia.isEmpty())

            // Verify intended profile & all settings reproduced
            assertEquals("hebrew_5x7", destUser.settingsRepository.activeProfileIdFlow.first())
            assertEquals("hebrew_5x7", destUser.profileRepository.activeProfile.value?.profileId)
            assertEquals(5, destUser.settingsRepository.gridColumnsFlow.first())
            assertEquals(7, destUser.settingsRepository.gridRowsFlow.first())
            assertEquals(1.25f, destUser.settingsRepository.gridTileScaleFlow.first(), 0.001f)
            assertEquals(0.85f, destUser.settingsRepository.gridTileContainerScaleFlow.first(), 0.001f)
            assertEquals(1.3f, destUser.settingsRepository.barTileImageScaleFlow.first(), 0.001f)
            assertEquals(1.15f, destUser.settingsRepository.barTileTitleScaleFlow.first(), 0.001f)
            assertEquals(1.4f, destUser.settingsRepository.actionButtonScaleFlow.first(), 0.001f)
            assertEquals(true, destUser.settingsRepository.showSentenceBarFlow.first())
            assertEquals(false, destUser.settingsRepository.showBackButtonFlow.first())
            assertEquals(false, destUser.settingsRepository.showBackspaceButtonFlow.first())
            assertEquals(false, destUser.settingsRepository.showSpeakButtonFlow.first())
            assertEquals(false, destUser.settingsRepository.homeInActionBarFlow.first())
            assertEquals(false, destUser.settingsRepository.speakOnTilePressFlow.first())
            assertEquals(false, destUser.settingsRepository.tilesUseTtsFlow.first())
            assertEquals(Gender.FEMALE, destUser.settingsRepository.userGenderFlow.first())
            assertEquals("he", destUser.settingsRepository.languageCodeFlow.first())

            // Verify tile beyond cell 14 and destination-resolved image + audio
            val restoredTiles = destUser.aacRepository.getCombinedTiles(null, "he").first()
            val restoredCell20 = restoredTiles.single { it.id == "tile_cell_20" }
            assertEquals(20, restoredCell20.layoutState.cellIndex)

            val expectedDestImage = File(destUser.filesDir, "images/custom_20.png")
            val expectedDestAudio = File(destUser.filesDir, "audio_tiles/he/audio_cell_20.wav")
            assertEquals(expectedDestImage.absolutePath, restoredCell20.imageUri)
            assertEquals(expectedDestAudio.absolutePath, restoredCell20.audioUri)
            assertTrue(expectedDestImage.isFile)
            assertTrue(expectedDestAudio.isFile)
            assertArrayEquals(sourceImageBytes, expectedDestImage.readBytes())
            assertArrayEquals(sourceAudioBytes, expectedDestAudio.readBytes())

            val resolvedModel = ImageStorageService.resolveImageModel(
                destUser.context,
                restoredCell20.imageUri,
            )
            assertTrue(resolvedModel is File)
            assertNotNull(BitmapFactory.decodeFile((resolvedModel as File).absolutePath))
            destUser
        }

        // Render MainCommunicationScreen with restored userB ViewModel and assert cell 20 tile is visible
        val communicationViewModel = CommunicationViewModel(
            application = application,
            settingsRepository = destUser.settingsRepository,
            observeTilesUseCase = ObserveTilesUseCase(destUser.aacRepository),
            incrementTileClickUseCase = IncrementTileClickUseCase(destUser.aacRepository),
            communicationSessionController = CommunicationSessionController(),
        )
        composeRule.setContent {
            val state = communicationViewModel.state.collectAsStateWithLifecycle()
            MyAACAppTheme {
                MainCommunicationScreen(
                    state = state.value,
                    onAction = communicationViewModel::onAction,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("אריח עשרים").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("אריח עשרים").assertIsDisplayed()
    }

    @Test
    fun legacyUnversionedBackupWithOldAbsoluteMediaResolvesFromArchiveAndPreservesDestinationPreferences() =
        runBlocking {
            val destUser = createUserStack("dest_legacy")
            seedDefaultDestinationUser(destUser)

            // Configure destination preferences before restoring a legacy archive without settings
            destUser.settingsRepository.apply {
                updateActiveProfileId("missing_profile_id")
                updateGridColumns(4)
                updateGridRows(6)
                updateGridTileScale(1.35f)
                updateGridTileContainerScale(0.9f)
                updateBarTileImageScale(1.2f)
                updateBarTileTitleScale(1.1f)
                updateActionButtonScale(1.25f)
                updateShowSentenceBar(true)
                updateShowBackButton(false)
                updateShowBackspaceButton(true)
                updateShowSpeakButton(false)
                updateHomeInActionBar(false)
                updateSpeakOnTilePress(false)
                updateTilesUseTts(false)
                updateUserGender(Gender.FEMALE)
                updateLanguageCode("he")
            }

            // Place poison files on disk at the exact old absolute path to prove restore NEVER reads from it
            val poisonRoot = File(testRootDir, "poison_old_device/files").apply { mkdirs() }
            val poisonImageFile = File(poisonRoot, "images/old_pic.png").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            }
            val poisonAudioFile = File(poisonRoot, "audio_tiles/he/old_voice.wav").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(0x05, 0x06, 0x07, 0x08))
            }

            val archivedImageBytes = createTestPngBytes(Color.GREEN)
            val archivedAudioBytes = createTestWavBytes(seedByte = 0x55)

            val legacyZip = File(archiveDir, "legacy_unversioned_absolute_paths.zip")
            val legacyProfile = UserProfile(
                profileId = "legacy_profile",
                profileName = "פרופיל ישן",
                activeLanguageCode = "he",
                layout = mapOf(
                    "tile_old_abs" to TileLayoutState(tileId = "tile_old_abs", parentId = null, cellIndex = 2),
                ),
            )
            val legacyTiles = listOf(
                TileDefinition(
                    id = "tile_old_abs",
                    label = "ישן",
                    ttsText = "ישן",
                    imageUri = poisonImageFile.absolutePath,
                    audioUri = poisonAudioFile.absolutePath,
                    type = TileType.BASIC,
                    languageCode = "he",
                    defaultCellIndex = 2,
                ),
            )

            ZipOutputStream(legacyZip.outputStream().buffered()).use { zos ->
                putZipTextEntry(zos, "profiles/profile_legacy_profile.json", json.encodeToString(legacyProfile))
                putZipTextEntry(zos, "tiles/he/default_dictionary.json", "[]")
                putZipTextEntry(zos, "tiles/he/export_user_defined.json", json.encodeToString(legacyTiles))
                putZipBytesEntry(zos, "images/old_pic.png", archivedImageBytes)
                putZipBytesEntry(zos, "audio_tiles/he/old_voice.wav", archivedAudioBytes)
            }

            destUser.backupManager.importDatabase(Uri.fromFile(legacyZip), targetContext.contentResolver)
            val result = destUser.backupManager.result.filterNotNull().first()
            assertTrue(result.isSuccess)
            assertTrue(result.missingMedia.isEmpty())

            // Valid profile from legacy archive became active
            assertEquals("legacy_profile", destUser.settingsRepository.activeProfileIdFlow.first())
            assertEquals("legacy_profile", destUser.profileRepository.activeProfile.value?.profileId)

            // Destination preferences were preserved
            assertEquals(4, destUser.settingsRepository.gridColumnsFlow.first())
            assertEquals(6, destUser.settingsRepository.gridRowsFlow.first())
            assertEquals(1.35f, destUser.settingsRepository.gridTileScaleFlow.first(), 0.001f)
            assertEquals(0.9f, destUser.settingsRepository.gridTileContainerScaleFlow.first(), 0.001f)
            assertEquals(1.2f, destUser.settingsRepository.barTileImageScaleFlow.first(), 0.001f)
            assertEquals(1.1f, destUser.settingsRepository.barTileTitleScaleFlow.first(), 0.001f)
            assertEquals(1.25f, destUser.settingsRepository.actionButtonScaleFlow.first(), 0.001f)
            assertEquals(true, destUser.settingsRepository.showSentenceBarFlow.first())
            assertEquals(false, destUser.settingsRepository.showBackButtonFlow.first())
            assertEquals(true, destUser.settingsRepository.showBackspaceButtonFlow.first())
            assertEquals(false, destUser.settingsRepository.showSpeakButtonFlow.first())
            assertEquals(false, destUser.settingsRepository.homeInActionBarFlow.first())
            assertEquals(false, destUser.settingsRepository.speakOnTilePressFlow.first())
            assertEquals(false, destUser.settingsRepository.tilesUseTtsFlow.first())
            assertEquals(Gender.FEMALE, destUser.settingsRepository.userGenderFlow.first())
            assertEquals("he", destUser.settingsRepository.languageCodeFlow.first())

            // Media resolved to destination storage using ARCHIVED bytes, never poison bytes
            val expectedImage = File(destUser.filesDir, "images/old_pic.png")
            val expectedAudio = File(destUser.filesDir, "audio_tiles/he/old_voice.wav")
            assertArrayEquals(archivedImageBytes, expectedImage.readBytes())
            assertArrayEquals(archivedAudioBytes, expectedAudio.readBytes())

            val restoredTile = destUser.aacRepository.baseDefinitions.value.single { it.id == "tile_old_abs" }
            assertEquals(expectedImage.absolutePath, restoredTile.imageUri)
            assertEquals(expectedAudio.absolutePath, restoredTile.audioUri)
        }

    @Test
    fun restoreReportsMissingMediaReferencesAndNeverReadsOldAbsolutePaths() = runBlocking {
        val destUser = createUserStack("dest_missing_media")
        seedDefaultDestinationUser(destUser)
        destUser.settingsRepository.updateTilesUseTts(false)
        destUser.settingsRepository.updateLanguageCode("he")

        // Existing poison file on disk outside the archive must NOT be read or linked
        val poisonRoot = File(testRootDir, "poison_missing/files").apply { mkdirs() }
        val poisonMissingImage = File(poisonRoot, "images/not_in_archive.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x7F, 0x7E))
        }

        val presentImageBytes = createTestPngBytes(Color.RED)
        val missingZip = File(archiveDir, "missing_media.zip")
        val profile = UserProfile(
            profileId = "partial_profile",
            profileName = "Partial Media Profile",
            activeLanguageCode = "he",
            layout = mapOf(
                "tile_ok" to TileLayoutState(tileId = "tile_ok", parentId = null, cellIndex = 0),
                "tile_missing" to TileLayoutState(tileId = "tile_missing", parentId = null, cellIndex = 1),
            ),
        )
        val tiles = listOf(
            TileDefinition(
                id = "tile_ok",
                label = "קיים",
                ttsText = "קיים",
                imageUri = "images/present.png",
                type = TileType.BASIC,
                languageCode = "he",
                defaultCellIndex = 0,
            ),
            TileDefinition(
                id = "tile_missing",
                label = "חסר",
                ttsText = "חסר",
                emoji = "❓",
                imageUri = poisonMissingImage.absolutePath,
                audioUri = "audio_tiles/he/not_in_archive.wav",
                type = TileType.BASIC,
                languageCode = "he",
                defaultCellIndex = 1,
            ),
        )

        ZipOutputStream(missingZip.outputStream().buffered()).use { zos ->
            putZipTextEntry(zos, "profiles/profile_partial_profile.json", json.encodeToString(profile))
            putZipTextEntry(zos, "tiles/he/default_dictionary.json", "[]")
            putZipTextEntry(zos, "tiles/he/export_user_defined.json", json.encodeToString(tiles))
            putZipBytesEntry(zos, "images/present.png", presentImageBytes)
        }

        destUser.backupManager.importDatabase(Uri.fromFile(missingZip), targetContext.contentResolver)
        val result = destUser.backupManager.result.filterNotNull().first()

        assertTrue(result.isSuccess)
        assertEquals(2, result.missingMedia.size)
        assertEquals(
            targetContext.getString(R.string.import_success_with_missing_media, 2),
            result.message,
        )
        assertTrue(
            result.missingMedia.any {
                it.tileId == "tile_missing" &&
                    it.mediaType == BackupMediaType.IMAGE &&
                    it.reference == poisonMissingImage.absolutePath
            },
        )
        assertTrue(
            result.missingMedia.any {
                it.tileId == "tile_missing" &&
                    it.mediaType == BackupMediaType.AUDIO &&
                    it.reference == "audio_tiles/he/not_in_archive.wav"
            },
        )

        val restoredMissingTile = destUser.aacRepository.baseDefinitions.value.single {
            it.id == "tile_missing"
        }
        assertNull(restoredMissingTile.imageUri)
        assertNull(restoredMissingTile.audioUri)
        assertFalse(File(destUser.filesDir, "images/not_in_archive.png").exists())

        val restoredOkTile = destUser.aacRepository.baseDefinitions.value.single {
            it.id == "tile_ok"
        }
        assertEquals(
            File(destUser.filesDir, "images/present.png").absolutePath,
            restoredOkTile.imageUri,
        )
    }

    @Test
    fun unsupportedOrCorruptManifestIsRejectedBeforeTouchingLiveBoardOrSettings() = runBlocking {
        val destUser = createUserStack("dest_bad_manifest")
        seedDefaultDestinationUser(destUser)

        val futureManifestZip = File(archiveDir, "future_manifest.zip")
        val corruptManifestZip = File(archiveDir, "corrupt_manifest.zip")

        val sampleProfile = UserProfile("p2", "Other", "he")
        ZipOutputStream(futureManifestZip.outputStream().buffered()).use { zos ->
            putZipTextEntry(
                zos,
                BackupService.BACKUP_MANIFEST_FILE,
                json.encodeToString(BackupManifest(formatVersion = 99)),
            )
            putZipTextEntry(zos, "profiles/profile_p2.json", json.encodeToString(sampleProfile))
            putZipTextEntry(zos, "tiles/he/default_dictionary.json", "[]")
        }
        ZipOutputStream(corruptManifestZip.outputStream().buffered()).use { zos ->
            putZipTextEntry(zos, BackupService.BACKUP_MANIFEST_FILE, "{broken-json")
            putZipTextEntry(zos, "profiles/profile_p2.json", json.encodeToString(sampleProfile))
            putZipTextEntry(zos, "tiles/he/default_dictionary.json", "[]")
        }

        listOf(futureManifestZip, corruptManifestZip).forEach { badZip ->
            destUser.backupManager.importDatabase(Uri.fromFile(badZip), targetContext.contentResolver)
            val result = destUser.backupManager.result.filterNotNull().first()
            assertFalse(result.isSuccess)
            assertEquals("dest_default", destUser.settingsRepository.activeProfileIdFlow.first())
            assertEquals(3, destUser.settingsRepository.gridColumnsFlow.first())
            assertEquals(5, destUser.settingsRepository.gridRowsFlow.first())
            assertEquals("en", destUser.settingsRepository.languageCodeFlow.first())
        }
    }

    @Test
    fun failureAfterSettingsAppliedRollsBackAndStartupRecoveryRecoversAllSettings() = runBlocking {
        val destUser = createUserStack("dest_rollback")
        seedDefaultDestinationUser(destUser)

        val backupFile = createVersionedBackupWithCustomSettings()

        // 1. In-process failure right AFTER settings have been applied
        destUser.backupManager.importDatabase(
            uri = Uri.fromFile(backupFile),
            contentResolver = targetContext.contentResolver,
            onApplyStep = { step ->
                if (step == RestoreApplyStep.AFTER_SETTINGS_APPLIED) {
                    throw IllegalStateException("Injected failure after settings applied")
                }
            },
        )
        val inProcessResult = destUser.backupManager.result.filterNotNull().first()
        assertFalse(inProcessResult.isSuccess)
        assertOriginalDestinationStateIntact(destUser)

        // 2. Simulated app restart after settings were already applied mid-restore
        val interrupted = destUser.backupService.interruptApplyForTest(
            contentResolver = targetContext.contentResolver,
            uri = Uri.fromFile(backupFile),
            interruptAt = RestoreApplyStep.AFTER_SETTINGS_APPLIED,
        )
        assertTrue(interrupted)

        // Confirm mid-restore DataStore & files WERE already overwritten before restart
        assertEquals("restored_5x7", destUser.settingsRepository.activeProfileIdFlow.first())
        assertEquals(5, destUser.settingsRepository.gridColumnsFlow.first())
        assertEquals(7, destUser.settingsRepository.gridRowsFlow.first())
        assertEquals("he", destUser.settingsRepository.languageCodeFlow.first())
        assertEquals(true, destUser.settingsRepository.showSentenceBarFlow.first())

        // Recreate fresh stack simulating process restart, keeping same filesDir/DataStore/Room DB
        val savedFilesDir = destUser.filesDir
        val savedDataStoreFile = destUser.dataStoreFile
        val savedDbName = destUser.dbName
        destUser.closeWithoutDeletingDisk()

        val restartedUser = createUserStack(
            userTag = "restarted",
            reuseFilesDir = savedFilesDir,
            reuseDataStoreFile = savedDataStoreFile,
            reuseDbName = savedDbName,
        )

        val coordinator = AppStartupCoordinator(
            context = restartedUser.context,
            backupService = restartedUser.backupService,
            repository = restartedUser.aacRepository,
            profileRepository = restartedUser.profileRepository,
        )
        assertTrue(coordinator.initialize())

        // Original board, active profile, and ALL affected settings are recovered
        assertOriginalDestinationStateIntact(restartedUser)

        // User can retry the restore cleanly after startup recovery
        restartedUser.backupManager.importDatabase(
            Uri.fromFile(backupFile),
            targetContext.contentResolver,
        )
        val retryResult = restartedUser.backupManager.result.filterNotNull().first()
        assertTrue(retryResult.isSuccess)
        assertEquals("restored_5x7", restartedUser.settingsRepository.activeProfileIdFlow.first())
        assertEquals(5, restartedUser.settingsRepository.gridColumnsFlow.first())
        assertEquals(7, restartedUser.settingsRepository.gridRowsFlow.first())
        assertEquals("he", restartedUser.settingsRepository.languageCodeFlow.first())
    }

    private suspend fun seedDefaultDestinationUser(user: IsolatedUserStack) {
        File(user.filesDir, "first_boot_complete.flag").writeText("booted")

        val destProfile = UserProfile(
            profileId = "dest_default",
            profileName = "Destination Default",
            activeLanguageCode = "en",
            layout = mapOf(
                "dest_tile" to TileLayoutState(tileId = "dest_tile", parentId = null, cellIndex = 0),
            ),
        )
        writeJson(File(user.filesDir, "profiles/profile_dest_default.json"), destProfile)

        val destDict = listOf(
            TileDefinition(
                id = "dest_tile",
                label = "Original Tile",
                ttsText = "Original Tile",
                emoji = "🏠",
                type = TileType.BASIC,
                languageCode = "en",
                defaultCellIndex = 0,
            ),
        )
        writeJson(File(user.filesDir, "tiles/en/default_dictionary.json"), destDict)
        writeJson(File(user.filesDir, "tiles/en/export_user_defined.json"), emptyList<TileDefinition>())

        val dao = user.database.aacTileDao()
        dao.insertTile(
            AACTile(
                id = "room_orig_tile",
                label = "Room Orig",
                ttsText = "Room Orig",
                isCategory = false,
                languageCode = "en",
            ),
        )
        dao.insertPlacements(
            listOf(
                TilePlacement(
                    tileId = "room_orig_tile",
                    parentId = ROOT_PARENT_ID,
                    languageCode = "en",
                    cellIndex = 1,
                    sortOrder = 1,
                ),
            ),
        )
        dao.insertClickEvents(
            listOf(
                TileClickEvent(
                    id = 99L,
                    tileId = "room_orig_tile",
                    profileId = "dest_default",
                    timestamp = 1_700_000_099L,
                ),
            ),
        )

        user.settingsRepository.apply {
            updateActiveProfileId("dest_default")
            updateGridColumns(3)
            updateGridRows(5)
            updateGridTileScale(0.9f)
            updateGridTileContainerScale(0.95f)
            updateBarTileImageScale(1.0f)
            updateBarTileTitleScale(1.0f)
            updateActionButtonScale(1.0f)
            updateShowSentenceBar(false)
            updateShowBackButton(true)
            updateShowBackspaceButton(true)
            updateShowSpeakButton(true)
            updateHomeInActionBar(true)
            updateSpeakOnTilePress(true)
            updateTilesUseTts(true)
            updateUserGender(Gender.MALE)
            updateLanguageCode("en")
        }

        user.profileRepository.reload()
        user.aacRepository.reload()
    }

    private suspend fun assertOriginalDestinationStateIntact(user: IsolatedUserStack) {
        assertEquals("dest_default", user.settingsRepository.activeProfileIdFlow.first())
        assertEquals("dest_default", user.profileRepository.activeProfile.value?.profileId)
        assertEquals(3, user.settingsRepository.gridColumnsFlow.first())
        assertEquals(5, user.settingsRepository.gridRowsFlow.first())
        assertEquals(0.9f, user.settingsRepository.gridTileScaleFlow.first(), 0.001f)
        assertEquals(0.95f, user.settingsRepository.gridTileContainerScaleFlow.first(), 0.001f)
        assertEquals(1.0f, user.settingsRepository.barTileImageScaleFlow.first(), 0.001f)
        assertEquals(1.0f, user.settingsRepository.barTileTitleScaleFlow.first(), 0.001f)
        assertEquals(1.0f, user.settingsRepository.actionButtonScaleFlow.first(), 0.001f)
        assertEquals(false, user.settingsRepository.showSentenceBarFlow.first())
        assertEquals(true, user.settingsRepository.showBackButtonFlow.first())
        assertEquals(true, user.settingsRepository.showBackspaceButtonFlow.first())
        assertEquals(true, user.settingsRepository.showSpeakButtonFlow.first())
        assertEquals(true, user.settingsRepository.homeInActionBarFlow.first())
        assertEquals(true, user.settingsRepository.speakOnTilePressFlow.first())
        assertEquals(true, user.settingsRepository.tilesUseTtsFlow.first())
        assertEquals(Gender.MALE, user.settingsRepository.userGenderFlow.first())
        assertEquals("en", user.settingsRepository.languageCodeFlow.first())

        assertTrue(File(user.filesDir, "profiles/profile_dest_default.json").exists())
        assertFalse(File(user.filesDir, "profiles/profile_restored_5x7.json").exists())
        assertTrue(user.aacRepository.baseDefinitions.value.any { it.id == "dest_tile" })
        assertEquals(1, user.database.aacTileDao().getAllClickEventsSync().size)
        assertFalse(File(user.filesDir, "restore_work").exists())
    }

    private fun createVersionedBackupWithCustomSettings(): File {
        val archiveFile = File(archiveDir, "versioned_backup.zip")
        val restoredProfile = UserProfile(
            profileId = "restored_5x7",
            profileName = "משוחזר",
            activeLanguageCode = "he",
            layout = mapOf(
                "tile_new" to TileLayoutState(tileId = "tile_new", parentId = null, cellIndex = 18),
            ),
        )
        val restoredDict = listOf(
            TileDefinition(
                id = "tile_new",
                label = "חדש",
                ttsText = "חדש",
                emoji = "🌟",
                type = TileType.BASIC,
                languageCode = "he",
                defaultCellIndex = 18,
            ),
        )
        val manifest = BackupManifest(
            formatVersion = BackupService.CURRENT_BACKUP_FORMAT_VERSION,
            exportedAtEpochMs = 1_700_000_500L,
            settings = com.kon.myaacapp.data.repository.ExportedBoardSettings(
                activeProfileId = "restored_5x7",
                gridColumns = 5,
                gridRows = 7,
                gridTileScale = 1.4f,
                gridTileContainerScale = 0.8f,
                barTileImageScale = 1.2f,
                barTileTitleScale = 1.3f,
                actionButtonScale = 1.1f,
                showSentenceBar = true,
                showBackButton = false,
                showBackspaceButton = false,
                showSpeakButton = false,
                homeInActionBar = false,
                speakOnTilePress = false,
                tilesUseTts = false,
                userGender = Gender.FEMALE.name,
                languageCode = "he",
            ),
        )

        ZipOutputStream(archiveFile.outputStream().buffered()).use { zos ->
            putZipTextEntry(zos, BackupService.BACKUP_MANIFEST_FILE, json.encodeToString(manifest))
            putZipTextEntry(zos, "profiles/profile_restored_5x7.json", json.encodeToString(restoredProfile))
            putZipTextEntry(zos, "tiles/he/default_dictionary.json", json.encodeToString(restoredDict))
        }
        return archiveFile
    }

    private fun createTestPngBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }

    private fun createTestWavBytes(seedByte: Byte): ByteArray {
        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x28, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            seedByte, 0x11, 0x22, 0x33,
        )
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

    private data class IsolatedUserStack(
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
            runCatching {
                runBlocking {
                    scope.coroutineContext[Job]?.cancelAndJoin()
                }
            }
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
