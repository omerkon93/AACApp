package com.kon.myaacapp

import android.content.Context
import android.content.res.AssetManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.local.dao.AACTileDao
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.CreateProfileResult
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.ui.profile.ProfileManagerAction
import com.kon.myaacapp.ui.profile.ProfileManagerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalCoroutinesApi::class)
class StarterProfileAndMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        context = mock(Context::class.java)
        assetManager = mock(AssetManager::class.java)
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.cacheDir).thenReturn(cacheDir)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.assets).thenReturn(assetManager)
        whenever(context.getString(R.string.default_profile_name)).thenReturn("Default Profile")
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

    private fun resolveAssetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")
    }

    private fun mockAssetZip(zipBytes: ByteArray) {
        doAnswer { ByteArrayInputStream(zipBytes) }
            .whenever(assetManager)
            .open("initial_data.zip")
    }

    @Test
    fun `shipped initial_data archive validates parity reachability domains and English formatting`() {
        val zipFile = resolveAssetFile("initial_data.zip")
        lateinit var factoryProfile: UserProfile
        lateinit var heDefs: List<TileDefinition>
        lateinit var enDefs: List<TileDefinition>
        val entryNames = mutableListOf<String>()

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    entryNames += entry.name
                }
            }

            assertFalse(
                "initial_data.zip should not include .bak backup files",
                entryNames.any { it.endsWith(".bak", ignoreCase = true) },
            )

            factoryProfile = json.decodeFromString(
                zip.getInputStream(zip.getEntry("profiles/profile_default.json"))
                    .bufferedReader().readText().removePrefix("\uFEFF"),
            )
            heDefs = json.decodeFromString(
                zip.getInputStream(zip.getEntry("tiles/he/default_dictionary.json"))
                    .bufferedReader().readText().removePrefix("\uFEFF"),
            )
            enDefs = json.decodeFromString(
                zip.getInputStream(zip.getEntry("tiles/en/default_dictionary.json"))
                    .bufferedReader().readText().removePrefix("\uFEFF"),
            )
        }

        val heMap = heDefs.associateBy { it.id }
        val enMap = enDefs.associateBy { it.id }

        assertEquals("HE and EN unique IDs must match", heMap.keys, enMap.keys)
        assertEquals(529, heMap.size)
        assertEquals(590, factoryProfile.layout.size)

        // Every placement resolves in both dictionaries
        factoryProfile.layout.values.forEach { placement ->
            assertTrue("Missing HE definition for ${placement.tileId}", heMap.containsKey(placement.tileId))
            assertTrue("Missing EN definition for ${placement.tileId}", enMap.containsKey(placement.tileId))
        }

        // No cell collisions within any folder
        factoryProfile.layout.values
            .groupBy { it.parentId?.takeIf { p -> p != "home" } }
            .forEach { (parentId, placements) ->
                val uniqueCells = placements.map { it.cellIndex }.toSet()
                assertEquals(
                    "Duplicate cellIndex inside parent=$parentId",
                    placements.size,
                    uniqueCells.size,
                )
            }

        // BFS from root reaches every folder in the layout without broken links
        val childrenByParent = factoryProfile.layout.values.groupBy { placement ->
            placement.parentId?.takeIf { it != "home" }
        }
        val allFolders = factoryProfile.layout.values
            .filter { heMap.getValue(it.tileId).resolvedType == TileType.FOLDER }
            .map { it.linkedCategoryId ?: heMap.getValue(it.tileId).defaultLinkedCategoryId ?: it.tileId }
            .toSet()

        val visitedFolders = mutableSetOf<String>()
        val queue = ArrayDeque<String?>()
        queue.add(null)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val items = childrenByParent[current].orEmpty()
            items.forEach { placement ->
                val def = enMap.getValue(placement.tileId)
                if (def.resolvedType == TileType.FOLDER) {
                    val targetFolderId = placement.linkedCategoryId
                        ?: def.defaultLinkedCategoryId
                        ?: placement.tileId
                    assertFalse(
                        "Folder $targetFolderId has no placements",
                        childrenByParent[targetFolderId].isNullOrEmpty(),
                    )
                    if (visitedFolders.add(targetFolderId)) {
                        queue.add(targetFolderId)
                    }
                }
            }
        }
        assertEquals("Unreachable folders exist", allFolders, visitedFolders)

        // Intentional repeated placements preserved
        val countsById = factoryProfile.layout.values.groupingBy { it.tileId }.eachCount()
        assertTrue((countsById["cup"] ?: 0) >= 2)
        assertTrue((countsById["cut"] ?: 0) >= 3)
        assertTrue((countsById["done"] ?: 0) >= 2)
        assertTrue((countsById["more"] ?: 0) >= 2)

        // Core AAC domains present & placed
        val requiredIds = listOf(
            "yes", "no", "dont_want", "stop",
            "help", "can_you_help",
            "hurt", "headache", "stomachache", "earache", "feel_sick", "uncomfortable",
            "want", "please", "more", "done",
            "i_feel", "emotions_category", "happy", "sad", "angry",
            "time_routines_category", "morning_routine_category", "evening_routine_category",
            "wash_face", "change_diaper", "leave_for_kindergarten", "put_on_pajamas",
        )
        val placedIds = factoryProfile.layout.values.map { it.tileId }.toSet()
        requiredIds.forEach { id ->
            assertTrue("Missing $id in HE", heMap.containsKey(id))
            assertTrue("Missing $id in EN", enMap.containsKey(id))
            assertTrue("Missing $id in layout", placedIds.contains(id))
        }

        // English dictionary quality invariants
        enDefs.forEach { def ->
            assertEquals("Trailing/leading whitespace in label for ${def.id}", def.label.trim(), def.label)
            assertEquals("Trailing/leading whitespace in ttsText for ${def.id}", def.ttsText.trim(), def.ttsText)
            assertEquals("Unexpected labelFeminine on EN tile ${def.id}", null, def.labelFeminine)
            assertEquals("Unexpected ttsTextFeminine on EN tile ${def.id}", null, def.ttsTextFeminine)
        }

        // Pilot-reviewed English AAC wording with stable IDs
        assertEquals("Breathe", enMap.getValue("breathe").label)
        assertEquals("Take a breath", enMap.getValue("breathe").ttsText)
        assertEquals("Wash face", enMap.getValue("wash_face").label)
        assertEquals("Wash my face", enMap.getValue("wash_face").ttsText)
        assertEquals("Change diaper", enMap.getValue("change_diaper").label)
        assertEquals("I need a diaper change", enMap.getValue("change_diaper").ttsText)
        assertEquals("Time for school", enMap.getValue("leave_for_kindergarten").label)
        assertEquals("Time for school", enMap.getValue("leave_for_kindergarten").ttsText)
    }

    @Test
    fun `createProfile creates factory starter independently of edited default and duplicate retains source language`() =
        runTest(testDispatcher) {
            val zipBytes = resolveAssetFile("initial_data.zip").readBytes()
            mockAssetZip(zipBytes)

            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val profileRepository = ProfileRepository(context, settings, scope)

            // Seed an edited "default" profile on disk (different layout & stats from factory)
            val customizedDefault = UserProfile(
                profileId = "default",
                profileName = "פרופיל ברירת מחדל",
                activeLanguageCode = "he",
                layout = mapOf(
                    "root_water" to TileLayoutState(
                        tileId = "water",
                        parentId = null,
                        cellIndex = 0,
                        isHidden = true,
                        clickCount = 42,
                    ),
                ),
            )
            File(filesDir, "profiles").mkdirs()
            File(filesDir, "profiles/profile_default.json").writeText(json.encodeToString(customizedDefault))
            settings.updateActiveProfileId("default")

            profileRepository.reload()
            testScheduler.advanceUntilIdle()
            assertEquals(1, profileRepository.activeProfile.value?.layout?.size)

            // 1. Starter profile in English must use shipped 590-placement factory layout, not customized "default"
            profileRepository.createProfile(
                name = "English Starter",
                creationMode = ProfileCreationMode.DEFAULT_TEMPLATE,
                boardLanguageCode = "en",
            )
            testScheduler.advanceUntilIdle()

            val englishStarter = profileRepository.activeProfile.value
            assertNotNull(englishStarter)
            assertEquals("en", englishStarter!!.activeLanguageCode)
            assertEquals(590, englishStarter.layout.size)
            assertTrue(englishStarter.layout.values.all { !it.isHidden && it.clickCount == 0 })
            assertTrue(File(filesDir, "tiles/en/default_dictionary.json").isFile)

            // 2. Duplicate current retains active profile's board language ("en") & layout even if another code is passed
            val editedEnglish = englishStarter.copy(
                layout = mapOf(
                    "root_yes" to TileLayoutState(tileId = "yes", parentId = null, cellIndex = 0, clickCount = 7),
                ),
            )
            profileRepository.updateActiveProfile(editedEnglish)
            testScheduler.advanceUntilIdle()
            profileRepository.createProfile(
                name = "English Copy",
                creationMode = ProfileCreationMode.DUPLICATE_CURRENT,
                boardLanguageCode = "he",
            )
            testScheduler.advanceUntilIdle()

            val duplicated = profileRepository.activeProfile.value
            assertNotNull(duplicated)
            assertEquals("en", duplicated!!.activeLanguageCode)
            assertEquals(editedEnglish.layout, duplicated.layout)

            // 3. Blank profile respects chosen language independently of active profile
            profileRepository.createProfile(
                name = "Blank Hebrew",
                creationMode = ProfileCreationMode.BLANK,
                boardLanguageCode = "he",
            )
            testScheduler.advanceUntilIdle()

            val blankHebrew = profileRepository.activeProfile.value
            assertNotNull(blankHebrew)
            assertEquals("he", blankHebrew!!.activeLanguageCode)
            assertTrue(blankHebrew.layout.isEmpty())

            scope.cancel()
        }

    @Test
    fun `upgrade sync updates starter dictionaries without clobbering profiles overrides images or recordings`() =
        runTest(testDispatcher) {
            val oldZipBytes = resolveAssetFile("initial_data_old.zip").readBytes()
            val newZipBytes = resolveAssetFile("initial_data.zip").readBytes()
            mockAssetZip(oldZipBytes)

            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val profileRepository = ProfileRepository(context, settings, scope)
            val dao = mock(AACTileDao::class.java)
            whenever(dao.getAllTilesSync()).thenReturn(emptyList())
            whenever(dao.getAllPlacements()).thenReturn(emptyList())
            val database = mock(AACDatabase::class.java)
            whenever(database.aacTileDao()).thenReturn(dao)
            val aacRepository = AACRepository(dao, context, profileRepository, settings)
            val backupService = BackupService(context, aacRepository, database, settings)

            // Initial old install
            assertTrue(backupService.importFromAssets("initial_data.zip"))

            val oldEnDict = json.decodeFromString<List<TileDefinition>>(
                File(filesDir, "tiles/en/default_dictionary.json").readText(),
            )
            assertFalse(oldEnDict.any { it.id == "morning_routine_category" })

            // Simulate user customizations, custom recording, custom image, and custom audio on bundled file
            val customProfile = UserProfile(
                profileId = "default",
                profileName = "My Board",
                activeLanguageCode = "he",
                layout = mapOf("root_yes" to TileLayoutState(tileId = "yes", parentId = null, cellIndex = 9)),
            )
            File(filesDir, "profiles").mkdirs()
            File(filesDir, "profiles/profile_default.json").writeText(json.encodeToString(customProfile))

            val customRecording = File(filesDir, "audio_tiles/he/user_voice.wav").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val preservedBundledAudio = File(filesDir, "audio_tiles/he/audio_yes.wav")
            val customAudioOverrideBytes = byteArrayOf(9, 8, 7, 6, 5)
            preservedBundledAudio.writeBytes(customAudioOverrideBytes)

            val customImage = File(filesDir, "images/child_photo.webp").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(10, 20, 30))
            }
            val userOverride = listOf(
                TileDefinition(
                    id = "yes",
                    label = "כן שלי",
                    ttsText = "בטח שכן",
                    audioUri = customRecording.absolutePath,
                    isCustomAudio = true,
                    imageUri = customImage.absolutePath,
                    type = TileType.BASIC,
                    languageCode = "he",
                ),
            )
            File(filesDir, "tiles/he/export_user_defined.json").writeText(json.encodeToString(userOverride))

            // Simulate pre-PR5 install without starter_data_version.txt upgrading to new APK assets
            File(filesDir, "starter_data_version.txt").delete()
            mockAssetZip(newZipBytes)

            assertTrue(backupService.syncStarterDataFromAssetsIfNeeded("initial_data.zip"))

            val upgradedEnDict = json.decodeFromString<List<TileDefinition>>(
                File(filesDir, "tiles/en/default_dictionary.json").readText(),
            )
            assertTrue(upgradedEnDict.any { it.id == "morning_routine_category" })
            assertTrue(upgradedEnDict.any { it.id == "wash_face" })
            assertEquals("Parrot", upgradedEnDict.first { it.id == "parrot" }.label)

            // User data and recordings remain untouched
            assertEquals(
                customProfile,
                json.decodeFromString<UserProfile>(File(filesDir, "profiles/profile_default.json").readText()),
            )
            assertEquals(
                userOverride,
                json.decodeFromString<List<TileDefinition>>(
                    File(filesDir, "tiles/he/export_user_defined.json").readText(),
                ),
            )
            assertArrayEquals(customAudioOverrideBytes, preservedBundledAudio.readBytes())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), customRecording.readBytes())
            assertArrayEquals(byteArrayOf(10, 20, 30), customImage.readBytes())

            scope.cancel()
        }

    @Test
    fun `multi-profile legacy migration repairs active and blank profiles without overwriting valid sibling languages`() =
        runTest(testDispatcher) {
            val zipBytes = resolveAssetFile("initial_data.zip").readBytes()
            mockAssetZip(zipBytes)

            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val profileRepository = ProfileRepository(context, settings, scope)
            val dao = mock(AACTileDao::class.java)
            whenever(dao.getAllTilesSync()).thenReturn(emptyList())
            whenever(dao.getAllPlacements()).thenReturn(emptyList())
            val database = mock(AACDatabase::class.java)
            whenever(database.aacTileDao()).thenReturn(dao)
            val aacRepository = AACRepository(dao, context, profileRepository, settings)
            val backupService = BackupService(context, aacRepository, database, settings)
            val coordinator = AppStartupCoordinator(context, backupService, aacRepository, profileRepository, settings)

            File(filesDir, "first_boot_complete.flag").writeText("booted")
            File(filesDir, "profiles").mkdirs()

            val activeStaleProfile = UserProfile(
                profileId = "active_en",
                profileName = "Active EN",
                activeLanguageCode = "he",
                layout = emptyMap(),
            )
            val siblingHebrewProfile = UserProfile(
                profileId = "sibling_he",
                profileName = "Sibling HE",
                activeLanguageCode = "he",
                layout = emptyMap(),
            )
            val legacyIwProfile = UserProfile(
                profileId = "legacy_iw",
                profileName = "Legacy IW",
                activeLanguageCode = "iw",
                layout = emptyMap(),
            )
            val blankLangProfile = UserProfile(
                profileId = "blank_lang",
                profileName = "Blank Lang",
                activeLanguageCode = "",
                layout = emptyMap(),
            )

            File(filesDir, "profiles/profile_active_en.json").writeText(json.encodeToString(activeStaleProfile))
            File(filesDir, "profiles/profile_sibling_he.json").writeText(json.encodeToString(siblingHebrewProfile))
            File(filesDir, "profiles/profile_legacy_iw.json").writeText(json.encodeToString(legacyIwProfile))
            File(filesDir, "profiles/profile_blank_lang.json").writeText(json.encodeToString(blankLangProfile))

            settings.updateActiveProfileId("active_en")
            settings.updateLanguageCode("en")

            assertTrue(coordinator.initialize())
            testScheduler.advanceUntilIdle()

            val migratedProfiles = profileRepository.profiles.value.associateBy { it.profileId }
            assertEquals("en", migratedProfiles.getValue("active_en").activeLanguageCode)
            assertEquals("he", migratedProfiles.getValue("sibling_he").activeLanguageCode)
            assertEquals("he", migratedProfiles.getValue("legacy_iw").activeLanguageCode)
            assertEquals("en", migratedProfiles.getValue("blank_lang").activeLanguageCode)
            assertEquals("en", settings.interfaceLanguageCodeFlow.first())

            scope.cancel()
        }

    @Test
    fun `profileManagerViewModel forwards chosen board language and reloads definitions`() =
        runTest(testDispatcher) {
            val profileRepository = mock(ProfileRepository::class.java)
            val aacRepository = mock(AACRepository::class.java)
            val defaultHebrew = UserProfile("default", "Default", "he", emptyMap())
            val createdEnglish = UserProfile("sam-id", "Sam", "en", emptyMap())
            whenever(profileRepository.profiles).thenReturn(MutableStateFlow(listOf(defaultHebrew)))
            whenever(profileRepository.activeProfile).thenReturn(MutableStateFlow(defaultHebrew))
            whenever(
                profileRepository.createProfile(
                    name = "Sam",
                    creationMode = ProfileCreationMode.DEFAULT_TEMPLATE,
                    boardLanguageCode = "en",
                ),
            ).thenReturn(CreateProfileResult.Success(createdEnglish))

            val vm = ProfileManagerViewModel(profileRepository, aacRepository)
            vm.onAction(ProfileManagerAction.OpenCreateDialog)
            vm.onAction(ProfileManagerAction.ProfileNameChanged("Sam"))
            vm.onAction(ProfileManagerAction.CreationModeChanged(ProfileCreationMode.DEFAULT_TEMPLATE))
            vm.onAction(ProfileManagerAction.BoardLanguageChanged("en"))
            vm.onAction(ProfileManagerAction.CreateProfileClicked)
            testScheduler.advanceUntilIdle()

            verify(profileRepository).createProfile(
                name = "Sam",
                creationMode = ProfileCreationMode.DEFAULT_TEMPLATE,
                boardLanguageCode = "en",
            )
            verify(aacRepository).reload()
        }
}
