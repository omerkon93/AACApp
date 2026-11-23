package com.kon.myaacapp

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.service.backup.RestoreApplyStep
import com.kon.myaacapp.ui.admin.system.SystemSettingsAction
import com.kon.myaacapp.ui.admin.system.SystemSettingsEffect
import com.kon.myaacapp.ui.admin.system.SystemSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class RestoreSafetyInstrumentedTest {

    private lateinit var targetContext: Context
    private lateinit var application: Application
    private lateinit var testRootDir: File
    private lateinit var sandboxFilesDir: File
    private lateinit var externalArchiveDir: File
    private lateinit var dbName: String
    private lateinit var isolatedContext: IsolatedAppContext

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var database: AACDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var aacRepository: AACRepository
    private lateinit var backupService: BackupService
    private lateinit var backupManager: BackupManager

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        application = targetContext.applicationContext as Application
        val runId = UUID.randomUUID().toString()
        testRootDir = File(targetContext.cacheDir, "restore_safety_test_$runId")
        sandboxFilesDir = File(testRootDir, "files").apply { mkdirs() }
        externalArchiveDir = File(testRootDir, "archives").apply { mkdirs() }
        dbName = "restore_safety_test_$runId.db"
        isolatedContext = IsolatedAppContext(targetContext, sandboxFilesDir)

        createStack()
    }

    @After
    fun tearDown() {
        runCatching { scope.cancel() }
        runCatching { database.close() }
        targetContext.deleteDatabase(dbName)
        testRootDir.deleteRecursively()
    }

    private fun createStack() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(testRootDir, "datastore/test_settings.preferences_pb") },
        )
        database = Room.databaseBuilder(
            targetContext,
            AACDatabase::class.java,
            dbName,
        ).build()
        settingsRepository = SettingsRepository(isolatedContext, dataStore)
        profileRepository = ProfileRepository(isolatedContext, settingsRepository, scope)
        aacRepository = AACRepository(
            aacTileDao = database.aacTileDao(),
            context = isolatedContext,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )
        backupService = BackupService(
            context = isolatedContext,
            repository = aacRepository,
            database = database,
            settingsRepository = settingsRepository,
        )
        backupManager = BackupManager(
            application = application,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            scope = scope,
        )
    }

    private suspend fun seedLiveBoard() {
        File(sandboxFilesDir, "first_boot_complete.flag").writeText("booted")

        val liveProfile = UserProfile(
            profileId = "live_profile",
            profileName = "Original Live Profile",
            activeLanguageCode = "he",
            layout = mapOf(
                "live_tile" to TileLayoutState(
                    tileId = "live_tile",
                    parentId = null,
                    cellIndex = 0,
                ),
            ),
        )
        writeJsonFile("profiles/profile_live_profile.json", liveProfile)

        val liveDict = listOf(
            TileDefinition(
                id = "live_tile",
                label = "Live Tile",
                ttsText = "Live Tile",
                imageUri = "images/live_img.webp",
                audioUri = "audio_tiles/live_audio.wav",
                type = TileType.BASIC,
                languageCode = "he",
                defaultCellIndex = 0,
            ),
        )
        writeJsonFile("tiles/he/default_dictionary.json", liveDict)
        writeJsonFile("tiles/he/export_user_defined.json", emptyList<TileDefinition>())

        writeBinaryFile("images/live_img.webp", LIVE_IMAGE_BYTES)
        writeBinaryFile("audio_tiles/live_audio.wav", LIVE_AUDIO_BYTES)

        val dao = database.aacTileDao()
        dao.insertTile(
            AACTile(
                id = "room_live_tile",
                label = "Room Live",
                ttsText = "Room Live",
                isCategory = false,
                languageCode = "he",
            ),
        )
        dao.insertPlacements(
            listOf(
                TilePlacement(
                    tileId = "room_live_tile",
                    parentId = ROOT_PARENT_ID,
                    languageCode = "he",
                    cellIndex = 4,
                    sortOrder = 4,
                ),
            ),
        )
        dao.insertClickEvents(
            listOf(
                TileClickEvent(
                    id = 42L,
                    tileId = "room_live_tile",
                    profileId = "live_profile",
                    timestamp = 1_700_000_042L,
                ),
            ),
        )

        settingsRepository.updateActiveProfileId("live_profile")
        settingsRepository.updateTilesUseTts(false)
        profileRepository.reload()
        aacRepository.reload()

        val active = profileRepository.activeProfile
            .filterNotNull()
            .first { it.profileId == "live_profile" }
        assertEquals("live_profile", active.profileId)
    }

    private fun createValidBackupZip(fileName: String = "valid_backup.zip"): File {
        val restoredProfile = UserProfile(
            profileId = "restored_profile",
            profileName = "פרופיל משוחזר",
            activeLanguageCode = "he",
            layout = mapOf(
                "folder_food" to TileLayoutState(
                    tileId = "folder_food",
                    parentId = null,
                    cellIndex = 1,
                ),
                "folder_fruits" to TileLayoutState(
                    tileId = "folder_fruits",
                    parentId = "folder_food",
                    cellIndex = 2,
                ),
                "tile_mango" to TileLayoutState(
                    tileId = "tile_mango",
                    parentId = "folder_fruits",
                    cellIndex = 0,
                ),
            ),
        )

        val defaultTiles = listOf(
            TileDefinition(
                id = "folder_food",
                label = "אוכל",
                ttsText = "אוכל",
                isCategory = true,
                type = TileType.FOLDER,
                languageCode = "he",
                defaultCellIndex = 1,
            ),
        )
        val userTiles = listOf(
            TileDefinition(
                id = "folder_fruits",
                label = "פירות",
                ttsText = "פירות",
                isCategory = true,
                type = TileType.FOLDER,
                languageCode = "he",
                defaultParentId = "folder_food",
                defaultCellIndex = 2,
            ),
            TileDefinition(
                id = "tile_mango",
                label = "מנגו",
                ttsText = "אני רוצה מנגו",
                imageUri = "images/mango.webp",
                audioUri = "audio_tiles/mango.wav",
                type = TileType.BASIC,
                languageCode = "he",
                defaultParentId = "folder_fruits",
                defaultCellIndex = 0,
            ),
        )

        val archiveFile = File(externalArchiveDir, fileName)
        ZipOutputStream(archiveFile.outputStream().buffered()).use { zos ->
            putZipTextEntry(
                zos,
                "profiles/profile_restored_profile.json",
                json.encodeToString(restoredProfile),
            )
            putZipTextEntry(
                zos,
                "tiles/he/default_dictionary.json",
                json.encodeToString(defaultTiles),
            )
            putZipTextEntry(
                zos,
                "tiles/he/default_dictionary.json.bak",
                "backup-metadata-ignored",
            )
            putZipTextEntry(
                zos,
                "tiles/he/export_user_defined.json",
                json.encodeToString(userTiles),
            )
            putZipBytesEntry(zos, "images/mango.webp", RESTORED_IMAGE_BYTES)
            putZipBytesEntry(zos, "audio_tiles/mango.wav", RESTORED_AUDIO_BYTES)
        }
        return archiveFile
    }

    @Test
    fun validBackupRestoresProfilesPlacementsNestedFoldersImageAndAudio() = runBlocking {
        seedLiveBoard()
        val backupFile = createValidBackupZip()

        backupManager.importDatabase(Uri.fromFile(backupFile), targetContext.contentResolver)
        val result = backupManager.result.filterNotNull().first()

        assertTrue(result.isSuccess)
        assertEquals(targetContext.getString(R.string.import_success), result.message)

        val restoredProfile = profileRepository.activeProfile.value
        assertNotNull(restoredProfile)
        assertEquals("restored_profile", restoredProfile!!.profileId)
        assertEquals("folder_food", restoredProfile.layout["folder_fruits"]?.parentId)
        assertEquals("folder_fruits", restoredProfile.layout["tile_mango"]?.parentId)
        assertEquals(0, restoredProfile.layout["tile_mango"]?.cellIndex)

        val expectedImagePath = File(sandboxFilesDir, "images/mango.webp").absolutePath
        val expectedAudioPath = File(sandboxFilesDir, "audio_tiles/mango.wav").absolutePath

        assertTrue(
            aacRepository.baseDefinitions.value.any {
                it.id == "tile_mango" &&
                    it.imageUri == expectedImagePath &&
                    it.audioUri == expectedAudioPath
            },
        )

        assertArrayEquals(RESTORED_IMAGE_BYTES, File(sandboxFilesDir, "images/mango.webp").readBytes())
        assertArrayEquals(RESTORED_AUDIO_BYTES, File(sandboxFilesDir, "audio_tiles/mango.wav").readBytes())
        assertFalse(File(sandboxFilesDir, "images/live_img.webp").exists())
        assertFalse(File(sandboxFilesDir, "restore_work").exists())
    }

    @Test
    fun exportThenRestoreRoundTripPreservesNestedFoldersAndMedia() = runBlocking {
        seedLiveBoard()
        val initialZip = createValidBackupZip("seed_for_export.zip")
        backupManager.importDatabase(Uri.fromFile(initialZip), targetContext.contentResolver)
        assertTrue(backupManager.result.filterNotNull().first().isSuccess)

        val exportedZip = File(externalArchiveDir, "roundtrip_export.zip")
        backupManager.exportDatabase(Uri.fromFile(exportedZip), targetContext.contentResolver)
        val exportResult = backupManager.result.filterNotNull().first()
        assertTrue(exportResult.isSuccess)
        assertTrue(exportedZip.length() > 0L)

        // Mutate live state back to a different board before restoring the exported archive
        File(sandboxFilesDir, "profiles").deleteRecursively()
        File(sandboxFilesDir, "tiles").deleteRecursively()
        File(sandboxFilesDir, "images").deleteRecursively()
        File(sandboxFilesDir, "audio_tiles").deleteRecursively()
        seedLiveBoard()

        backupManager.importDatabase(Uri.fromFile(exportedZip), targetContext.contentResolver)
        val importResult = backupManager.result.filterNotNull().first()
        assertTrue(importResult.isSuccess)

        val restoredProfile = profileRepository.activeProfile.value
        assertEquals("restored_profile", restoredProfile?.profileId)
        assertEquals("folder_fruits", restoredProfile?.layout?.get("tile_mango")?.parentId)
        assertArrayEquals(RESTORED_IMAGE_BYTES, File(sandboxFilesDir, "images/mango.webp").readBytes())
        assertArrayEquals(RESTORED_AUDIO_BYTES, File(sandboxFilesDir, "audio_tiles/mango.wav").readBytes())
    }

    @Test
    fun invalidOrTruncatedArchivesLeaveLiveBoardIntactAndAllowRetry() = runBlocking {
        seedLiveBoard()
        val validZip = createValidBackupZip()
        val validBytes = validZip.readBytes()

        val garbageZip = File(externalArchiveDir, "garbage.zip").apply {
            writeBytes("not a zip archive at all".toByteArray())
        }
        val truncatedZip = File(externalArchiveDir, "truncated.zip").apply {
            writeBytes(validBytes.copyOf((validBytes.size * 0.55).toInt()))
        }
        val missingProfilesZip = File(externalArchiveDir, "missing_profiles.zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                putZipTextEntry(zos, "tiles/he/default_dictionary.json", "[]")
            }
        }
        val malformedTilesZip = File(externalArchiveDir, "malformed_tiles.zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                putZipTextEntry(
                    zos,
                    "profiles/profile_bad.json",
                    json.encodeToString(UserProfile("bad", "Bad", "he")),
                )
                putZipTextEntry(zos, "tiles/he/default_dictionary.json", "{not-valid-json")
            }
        }
        val traversalZip = File(externalArchiveDir, "traversal.zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                putZipTextEntry(zos, "../../evil.txt", "pwned")
                putZipTextEntry(
                    zos,
                    "profiles/profile_bad.json",
                    json.encodeToString(UserProfile("bad", "Bad", "he")),
                )
                putZipTextEntry(zos, "tiles/he/default_dictionary.json", "[]")
            }
        }

        val badArchives = listOf(
            garbageZip,
            truncatedZip,
            missingProfilesZip,
            malformedTilesZip,
            traversalZip,
        )

        badArchives.forEach { badFile ->
            backupManager.importDatabase(Uri.fromFile(badFile), targetContext.contentResolver)
            val result = backupManager.result.filterNotNull().first()
            assertFalse("Expected failure for ${badFile.name}", result.isSuccess)
            assertEquals(targetContext.getString(R.string.import_failed), result.message)
            assertLiveBoardAndRoomIntact()
            assertFalse(File(testRootDir, "evil.txt").exists())
            assertFalse(File(sandboxFilesDir, "restore_work").exists())
        }

        // Retry with valid archive succeeds
        backupManager.importDatabase(Uri.fromFile(validZip), targetContext.contentResolver)
        val retryResult = backupManager.result.filterNotNull().first()
        assertTrue(retryResult.isSuccess)
        assertEquals("restored_profile", profileRepository.activeProfile.value?.profileId)
    }

    @Test
    fun injectedApplyFailureRollsBackLiveDataAndRoomAndAllowsRetry() = runBlocking {
        seedLiveBoard()
        val validZip = createValidBackupZip()

        val failureSteps = listOf(
            RestoreApplyStep.AFTER_FILE_SWAP,
            RestoreApplyStep.AFTER_ROOM_CLEAR,
            RestoreApplyStep.AFTER_RELOAD,
        )

        failureSteps.forEach { stepToFail ->
            backupManager.importDatabase(
                uri = Uri.fromFile(validZip),
                contentResolver = targetContext.contentResolver,
                onApplyStep = { step ->
                    if (step == stepToFail) {
                        throw IllegalStateException("Injected failure at $stepToFail")
                    }
                },
            )
            val result = backupManager.result.filterNotNull().first()
            assertFalse("Expected failure at $stepToFail", result.isSuccess)
            assertEquals(targetContext.getString(R.string.import_failed), result.message)
            assertLiveBoardAndRoomIntact()
            assertFalse(File(sandboxFilesDir, "images/mango.webp").exists())
            assertFalse(File(sandboxFilesDir, "profiles/profile_restored_profile.json").exists())
            assertFalse(File(sandboxFilesDir, "restore_work").exists())
        }

        // User can retry restore cleanly after failures
        backupManager.importDatabase(Uri.fromFile(validZip), targetContext.contentResolver)
        val retryResult = backupManager.result.filterNotNull().first()
        assertTrue(retryResult.isSuccess)
        assertEquals("restored_profile", profileRepository.activeProfile.value?.profileId)
    }

    @Test
    fun appRestartAfterInterruptedApplyRecoversOriginalBoardAndAllowsRetry() = runBlocking {
        seedLiveBoard()
        val validZip = createValidBackupZip()

        val interrupted = backupService.interruptApplyForTest(
            contentResolver = targetContext.contentResolver,
            uri = Uri.fromFile(validZip),
            interruptAt = RestoreApplyStep.AFTER_ROOM_CLEAR,
        )
        assertTrue(interrupted)

        // Confirm mid-restore dirty state before simulated restart
        assertTrue(File(sandboxFilesDir, "profiles/profile_restored_profile.json").exists())
        assertFalse(File(sandboxFilesDir, "profiles/profile_live_profile.json").exists())
        assertTrue(database.aacTileDao().getAllTilesSync().isEmpty())
        assertEquals("COMMITTING", File(sandboxFilesDir, "restore_work/journal.txt").readText())

        // Simulate app process death & restart with fresh Database/Repositories/StartupCoordinator
        scope.cancel()
        database.close()
        createStack()

        val coordinator = AppStartupCoordinator(
            context = isolatedContext,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
        )
        val initialized = coordinator.initialize()
        assertTrue(initialized)

        // Original profile, files, base tile, and analytics click events are intact
        assertEquals("live_profile", settingsRepository.activeProfileIdFlow.first())
        assertEquals("live_profile", profileRepository.activeProfile.value?.profileId)
        assertTrue(File(sandboxFilesDir, "profiles/profile_live_profile.json").exists())
        assertArrayEquals(LIVE_IMAGE_BYTES, File(sandboxFilesDir, "images/live_img.webp").readBytes())
        assertArrayEquals(LIVE_AUDIO_BYTES, File(sandboxFilesDir, "audio_tiles/live_audio.wav").readBytes())
        assertTrue(aacRepository.baseDefinitions.value.any { it.id == "live_tile" })
        val recoveredClickEvents = database.aacTileDao().getAllClickEventsSync()
        assertEquals(1, recoveredClickEvents.size)
        assertEquals(42L, recoveredClickEvents.single().id)
        assertEquals(1_700_000_042L, recoveredClickEvents.single().timestamp)
        assertFalse(File(sandboxFilesDir, "profiles/profile_restored_profile.json").exists())
        assertFalse(File(sandboxFilesDir, "images/mango.webp").exists())
        assertFalse(File(sandboxFilesDir, "restore_work").exists())

        // User can retry the restore after restart recovery
        backupManager.importDatabase(Uri.fromFile(validZip), targetContext.contentResolver)
        val retryResult = backupManager.result.filterNotNull().first()
        assertTrue(retryResult.isSuccess)
        assertEquals("restored_profile", profileRepository.activeProfile.value?.profileId)
    }

    @Test
    fun directRecoveryRestoresExactRoomSnapshotTablesBeforeLegacyMigration() = runBlocking {
        seedLiveBoard()
        val validZip = createValidBackupZip()

        assertTrue(
            backupService.interruptApplyForTest(
                contentResolver = targetContext.contentResolver,
                uri = Uri.fromFile(validZip),
                interruptAt = RestoreApplyStep.AFTER_ROOM_CLEAR,
            ),
        )
        assertTrue(database.aacTileDao().getAllTilesSync().isEmpty())
        assertTrue(database.aacTileDao().getAllPlacements().isEmpty())
        assertTrue(database.aacTileDao().getAllClickEventsSync().isEmpty())

        assertTrue(backupService.recoverIncompleteRestoreIfNeeded())
        profileRepository.reload()
        aacRepository.reload()

        assertLiveBoardAndRoomIntact()
        assertFalse(File(sandboxFilesDir, "restore_work").exists())
    }

    @Test
    fun systemSettingsViewModelResetsBusyStateAndEmitsErrorBeforeSuccessOnRetry() = runBlocking {
        seedLiveBoard()
        val validZip = createValidBackupZip()
        val badZip = File(externalArchiveDir, "bad_vm_test.zip").apply {
            writeBytes(validZip.readBytes().copyOf(128))
        }

        val viewModel = SystemSettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            aacRepository = aacRepository,
            profileRepository = profileRepository,
            backupService = backupService,
        )

        val emittedEffects = mutableListOf<SystemSettingsEffect>()
        val stateCollectorJob = launch(Dispatchers.Main.immediate) {
            viewModel.state.collect {}
        }
        val collectorJob = launch(Dispatchers.Main.immediate) {
            viewModel.effects.collect { emittedEffects += it }
        }

        // First attempt with truncated archive -> emits ShowError and resets isImporting for retry
        viewModel.onAction(SystemSettingsAction.ImportBackupFileSelected(Uri.fromFile(badZip)))
        val firstEffect = waitForEffect(emittedEffects, expectedCount = 1)
        assertTrue(firstEffect is SystemSettingsEffect.ShowError)
        val firstIdleState = kotlinx.coroutines.withTimeout(kotlin.time.Duration.parse("3s")) {
            viewModel.state.first { !it.isImporting && it.canRunBackupOperation }
        }
        assertFalse(firstIdleState.isImporting)
        assertTrue(firstIdleState.canRunBackupOperation)
        assertLiveBoardAndRoomIntact()

        // Retry with valid archive -> emits ShowMessage only after restored board is ready
        viewModel.onAction(SystemSettingsAction.ImportBackupFileSelected(Uri.fromFile(validZip)))
        val secondEffect = waitForEffect(emittedEffects, expectedCount = 2)
        assertTrue(secondEffect is SystemSettingsEffect.ShowMessage)
        val secondIdleState = kotlinx.coroutines.withTimeout(kotlin.time.Duration.parse("3s")) {
            viewModel.state.first { !it.isImporting && it.canRunBackupOperation }
        }
        assertFalse(secondIdleState.isImporting)
        assertTrue(secondIdleState.canRunBackupOperation)
        assertEquals("restored_profile", profileRepository.activeProfile.value?.profileId)

        collectorJob.cancel()
        stateCollectorJob.cancel()
    }

    private suspend fun waitForEffect(
        effects: List<SystemSettingsEffect>,
        expectedCount: Int,
    ): SystemSettingsEffect {
        val deadlineMs = System.currentTimeMillis() + 10_000L
        while (effects.size < expectedCount && System.currentTimeMillis() < deadlineMs) {
            kotlinx.coroutines.delay(kotlin.time.Duration.parse("25ms"))
        }
        assertTrue("Timed out waiting for effect #$expectedCount", effects.size >= expectedCount)
        return effects[expectedCount - 1]
    }

    private suspend fun assertLiveBoardAndRoomIntact() {
        assertEquals("live_profile", settingsRepository.activeProfileIdFlow.first())
        assertEquals("live_profile", profileRepository.activeProfile.value?.profileId)
        assertTrue(File(sandboxFilesDir, "profiles/profile_live_profile.json").exists())
        assertArrayEquals(LIVE_IMAGE_BYTES, File(sandboxFilesDir, "images/live_img.webp").readBytes())
        assertArrayEquals(LIVE_AUDIO_BYTES, File(sandboxFilesDir, "audio_tiles/live_audio.wav").readBytes())
        assertTrue(aacRepository.baseDefinitions.value.any { it.id == "live_tile" })

        val dao = database.aacTileDao()
        val tiles = dao.getAllTilesSync()
        val placements = dao.getAllPlacements()
        val clickEvents = dao.getAllClickEventsSync()

        assertEquals(1, tiles.size)
        assertEquals("room_live_tile", tiles.single().id)
        assertEquals(1, placements.size)
        assertEquals("room_live_tile", placements.single().tileId)
        assertEquals(4, placements.single().cellIndex)
        assertEquals(1, clickEvents.size)
        assertEquals(42L, clickEvents.single().id)
        assertEquals(1_700_000_042L, clickEvents.single().timestamp)
    }

    private inline fun <reified T> writeJsonFile(relativePath: String, value: T) {
        val file = File(sandboxFilesDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(value))
    }

    private fun writeBinaryFile(relativePath: String, bytes: ByteArray) {
        val file = File(sandboxFilesDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private fun putZipTextEntry(zos: ZipOutputStream, name: String, content: String) {
        putZipBytesEntry(zos, name, content.toByteArray(Charsets.UTF_8))
    }

    private fun putZipBytesEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private class IsolatedAppContext(
        base: Context,
        private val customFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = customFilesDir
        override fun getApplicationContext(): Context = this
    }

    private companion object {
        val LIVE_IMAGE_BYTES = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x01, 0x02, 0x03)
        val LIVE_AUDIO_BYTES = byteArrayOf(0x57, 0x41, 0x56, 0x45, 0x04, 0x05, 0x06)
        val RESTORED_IMAGE_BYTES = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x10, 0x20, 0x30)
        val RESTORED_AUDIO_BYTES = byteArrayOf(0x57, 0x41, 0x56, 0x45, 0x40, 0x50, 0x60)
    }
}
